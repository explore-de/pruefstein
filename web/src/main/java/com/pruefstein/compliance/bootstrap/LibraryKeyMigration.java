package com.pruefstein.compliance.bootstrap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.shared.bootstrap.SeedLedger;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drops the 2013 Annex A domain prefixes from the library keys of an existing
 * database.
 *
 * <p>
 * A library key is the entry's file name, and it is permanent: the seed ledger
 * remembers it, and every check created from the entry carries it. The first
 * keys were named after the 2013 domains — {@code a9.}, {@code a10.},
 * {@code a12.}, {@code a13.} — a classification that has since moved underneath
 * them, so the files were renamed to plain names. A database seeded under the
 * old names would then seed every check a second time and offer each entry
 * again on the Library screen. This renames the ledger rows and the library
 * keys instead, before the seeder looks at either.
 *
 * <p>
 * Runs whether or not seeding is enabled: it changes what a check is called,
 * never what it does, and a frozen catalog needs a truthful Library screen as
 * much as any other.
 */
@ApplicationScoped
public class LibraryKeyMigration
{
	/** Before the seeder, which has to find the new keys already claimed. */
	public static final int PRIORITY = CatalogSeeder.PRIORITY - 10;

	private static final Logger LOG = LoggerFactory.getLogger(LibraryKeyMigration.class);

	private static final String LEDGER_KEY = "library-keys#drop-2013-prefixes";

	/**
	 * Every key the library ever had under a 2013 prefix, and the ledger keys
	 * of the SQL rewrites that were named after them, each mapped to its plain
	 * name. Permanent: a database can turn up with the old keys at any time.
	 */
	static final Map<String, String> RENAMES;

	static
	{
		Map<String, String> renames = new LinkedHashMap<>();
		for (String old : List.of(
			"a10.filevault",
			"a12.firewall",
			"a12.auto-updates",
			"a12.critical-updates",
			"a12.macos-updates",
			"a12.gatekeeper",
			"a12.sip",
			"a12.time-machine",
			"a12.firewall-logging",
			"a12.security-data-updates",
			"a12.blocked-apps",
			"a9.screen-lock-timeout",
			"a9.screen-lock-password",
			"a9.auto-login",
			"a9.guest-account",
			"a13.remote-login",
			"a13.screen-sharing",
			"a13.file-sharing",
			"a13.internet-sharing",
			"a13.stealth-mode",
			"a13.remote-management",
			"a13.remote-apple-events",
			"a13.printer-sharing",
			"a13.bluetooth-sharing",
			"a13.content-caching",
			"a13.disc-sharing",
			"a12.auto-updates#plist",
			"a12.critical-updates#plist",
			"a12.macos-updates#plist",
			"a9.screen-lock-timeout#plist",
			"a9.auto-login#plist",
			"a9.guest-account#plist",
			"a12.firewall-logging#os-version"))
		{
			renames.put(old, old.substring(old.indexOf('.') + 1));
		}
		RENAMES = Collections.unmodifiableMap(renames);
	}

	@Inject
	SeedLedger ledger;

	@Inject
	ComplianceItemRepository itemRepository;

	@Transactional
	void migrateOnStartup(@Observes @Priority(PRIORITY) StartupEvent event)
	{
		int renamed = migrate();
		if (renamed > 0)
		{
			LOG.info("Renamed {} ledger row(s) and check(s) from the 2013 library keys", renamed);
		}
	}

	/**
	 * @return how many ledger rows and checks were renamed; zero on a database
	 *         that never saw the old keys
	 */
	public int migrate()
	{
		if (!ledger.claim(LEDGER_KEY))
		{
			return 0;
		}
		int renamed = 0;
		for (Map.Entry<String, String> rename : RENAMES.entrySet())
		{
			renamed += ledger.rename(rename.getKey(), rename.getValue());
			renamed += itemRepository.update("libraryKey = ?1 where libraryKey = ?2", rename.getValue(), rename.getKey());
		}
		return renamed;
	}
}
