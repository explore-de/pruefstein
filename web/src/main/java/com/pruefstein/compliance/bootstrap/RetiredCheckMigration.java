package com.pruefstein.compliance.bootstrap;

import java.util.List;

import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.library.ComplianceLibrary;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.shared.bootstrap.SeedLedger;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retires baseline checks that were withdrawn from the
 * {@link ComplianceLibrary}, in the databases that already seeded them.
 *
 * <p>
 * Dropping an entry from the library only stops new deployments creating it.
 * Every database that booted on an earlier release still has the check, and
 * {@link CatalogSeeder} will not touch it again — so without this, the same
 * release would measure devices differently depending on when the install
 * happened. {@link CatalogQueryMigration} cannot do the job either: it applies
 * SQL from a library entry, and a withdrawn check has none.
 *
 * <p>
 * Retiring rather than deleting, for the reason the UI retires: reports already
 * filed name the check, and the rows that justify them have to stay readable.
 * Ledgered per check, so an administrator who adds it back from the Library
 * screen keeps it — except a withdrawn check is no longer on that screen, which
 * makes this the one retirement they cannot undo. It is also why a check whose
 * SQL they had changed is left alone: they were measuring something of their
 * own with it, and that is theirs to withdraw.
 */
@ApplicationScoped
public class RetiredCheckMigration
{
	/**
	 * After the query corrections, so a check is never both fixed and retired.
	 */
	public static final int PRIORITY = CatalogQueryMigration.PRIORITY + 10;

	private static final Logger LOG = LoggerFactory.getLogger(RetiredCheckMigration.class);

	/**
	 * @param ledgerKey
	 *            permanent, and deliberately not the check's own seed key:
	 *            claiming it must never look like having seeded the check
	 * @param checkName
	 *            the name the check was shipped under, which is the only handle
	 *            the database offers — the seed key lives in the ledger, not on
	 *            the row. A check an administrator renamed is left alone
	 * @param shippedQueries
	 *            every SQL this check shipped with, so one whose query has been
	 *            edited can be told apart and kept
	 */
	record Retirement(String ledgerKey, String checkName, List<String> shippedQueries)
	{
	}

	/**
	 * The screen lock timeout could only ever be read from
	 * {@code com.apple.screensaver}, and macOS no longer keeps an idle time
	 * there: the domain is absent on a current machine, and the sandboxed
	 * screen saver container osquery would have to read instead is unreadable
	 * without Full Disk Access. The check therefore failed every device whose
	 * screen locks perfectly well. What remains measurable is whether the lock
	 * asks for a password and how long it waits, which is
	 * {@code screen-lock-password} reading the {@code screenlock} table.
	 */
	/**
	 * The library keys these checks were shipped under. Nothing reads them at
	 * runtime — a withdrawn entry is simply absent — but the renames in
	 * {@link LibraryKeyMigration} still point at them, and a key that names no
	 * entry has to be explained rather than just missing.
	 */
	static final List<String> WITHDRAWN_KEYS = List.of("screen-lock-timeout");

	static final List<Retirement> RETIREMENTS = List.of(
		new Retirement("screen-lock-timeout#retired", "Screen lock timeout ≤ 300 seconds", List.of(
			"SELECT value FROM preferences WHERE domain = 'com.apple.screensaver' AND key = 'idleTime';",
			"SELECT count(*) AS configured, min(cast(value AS integer)) AS shortest,"
				+ " max(cast(value AS integer)) AS longest FROM plist WHERE (path ="
				+ " '/Library/Managed Preferences/com.apple.screensaver.plist' OR path LIKE"
				+ " '/Users/%/Library/Preferences/com.apple.screensaver.plist' OR path LIKE"
				+ " '/Users/%/Library/Preferences/ByHost/com.apple.screensaver.%') AND key ="
				+ " 'idleTime';",
			"WITH managed AS (SELECT value FROM plist WHERE path ="
				+ " '/Library/Managed Preferences/com.apple.screensaver.plist' AND key = 'idleTime'),"
				+ " local AS (SELECT value FROM plist WHERE path LIKE"
				+ " '/Users/%/Library/Preferences/com.apple.screensaver.plist' AND key = 'idleTime'"
				+ " UNION ALL SELECT value FROM plist WHERE path LIKE"
				+ " '/Users/%/Library/Preferences/ByHost/com.apple.screensaver.%' AND key = 'idleTime'),"
				+ " effective AS (SELECT value FROM managed UNION ALL SELECT value FROM local"
				+ " WHERE NOT EXISTS (SELECT 1 FROM managed))"
				+ " SELECT count(*) AS configured, min(cast(value AS integer)) AS shortest,"
				+ " max(cast(value AS integer)) AS longest FROM effective;")));

	@Inject
	SeedLedger ledger;

	@Inject
	ComplianceItemRepository itemRepository;

	/**
	 * A deployment that has frozen its catalog has opted out of us changing its
	 * checks, withdrawals included.
	 */
	@ConfigProperty(name = "pruefstein.compliance.seed-catalog", defaultValue = "true")
	boolean seedingEnabled;

	@Transactional
	void retireOnStartup(@Observes @Priority(PRIORITY) StartupEvent event)
	{
		if (!seedingEnabled)
		{
			return;
		}
		int retired = retire();
		if (retired > 0)
		{
			LOG.info("Retired {} withdrawn compliance check(s)", retired);
		}
	}

	/**
	 * @return how many checks this call retired; zero on a database seeded
	 *         after the withdrawal, which never had them
	 */
	public int retire()
	{
		int retired = 0;
		for (Retirement retirement : RETIREMENTS)
		{
			if (!ledger.claim(retirement.ledgerKey()))
			{
				continue;
			}
			for (ComplianceItem item : itemRepository.list("name", retirement.checkName()))
			{
				if (item.isRetired() || !wasShippedAsIs(item, retirement))
				{
					continue;
				}
				item.retire();
				retired++;
			}
		}
		return retired;
	}

	private static boolean wasShippedAsIs(ComplianceItem item, Retirement retirement)
	{
		return item instanceof ExpressionCheck check
			&& retirement.shippedQueries().contains(check.getQuery());
	}
}
