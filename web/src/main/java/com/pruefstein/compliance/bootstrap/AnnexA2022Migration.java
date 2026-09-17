package com.pruefstein.compliance.bootstrap;

import java.util.List;
import java.util.Objects;

import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.library.ComplianceLibrary;
import com.pruefstein.compliance.library.LibraryEntry;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
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
 * Re-files the baseline checks from the 2013 Annex A domains onto the 2022
 * themes the {@link ComplianceLibrary} now files them under, and gives each one
 * its control reference.
 *
 * <p>
 * A database seeded before the 2022 revision has its checks sitting in groups
 * named for the old domains, with no control recorded — and
 * {@link CatalogSeeder} will not touch them again, because their keys are
 * already claimed. Without this, a fresh install and an upgraded one would
 * disagree about what the Groups &amp; Items screen is even organised by.
 *
 * <p>
 * Checks are located by name, which is the only handle the database offers: the
 * seed key lives in the ledger, not on the row. A check an administrator has
 * renamed is therefore left where it is, consistent with every other promise
 * {@link SeedLedger} makes. One they have already filed into a group of their
 * own stays there as well: the control is a fact about the check and is
 * recorded regardless, but where it sits on the screen is their call. Only a
 * check still in one of the old domain groups, or in none, is moved. The old
 * groups are removed once empty; one that still holds an administrator's own
 * checks is kept, since deleting it would take their work with it.
 */
@ApplicationScoped
public class AnnexA2022Migration
{
	/** After the catalog exists, before the demo data that builds on it. */
	public static final int PRIORITY = CatalogSeeder.PRIORITY + 20;

	private static final Logger LOG = LoggerFactory.getLogger(AnnexA2022Migration.class);

	/**
	 * One ledger key for the whole re-filing rather than one per check: the
	 * checks move together or not at all, and a half-migrated screen is worse
	 * than either end state.
	 */
	private static final String LEDGER_KEY = "annex-a-2022#themes";

	/**
	 * The groups the 2013 catalog created. They are matched by name because
	 * that is how they were created, and dropped only once nothing is left in
	 * them.
	 */
	private static final List<String> RETIRED_GROUPS = List.of(
		"A.9 Access Control",
		"A.10 Cryptography",
		"A.12 Operations Security",
		"A.13 Communications Security");

	@Inject
	SeedLedger ledger;

	@Inject
	ComplianceLibrary library;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceGroupRepository groupRepository;

	/**
	 * A deployment that has frozen its catalog has opted out of us changing its
	 * checks, this re-filing included.
	 */
	@ConfigProperty(name = "pruefstein.compliance.seed-catalog", defaultValue = "true")
	boolean seedingEnabled;

	@Transactional
	void migrateOnStartup(@Observes @Priority(PRIORITY) StartupEvent event)
	{
		if (!seedingEnabled)
		{
			return;
		}
		int refiled = migrate();
		if (refiled > 0)
		{
			LOG.info("Re-filed {} baseline check(s) onto the ISO 27001:2022 themes", refiled);
		}
	}

	/**
	 * @return how many checks this call moved; zero on a database seeded after
	 *         the revision, whose checks are already on the themes
	 */
	public int migrate()
	{
		if (!ledger.claim(LEDGER_KEY))
		{
			return 0;
		}
		int refiled = 0;
		for (LibraryEntry entry : library.entries())
		{
			for (ComplianceItem check : itemRepository.list("name", entry.name()))
			{
				if (refile(check, entry))
				{
					refiled++;
				}
			}
		}
		dropRetiredGroups();
		return refiled;
	}

	/**
	 * @return {@code true} if anything about the check actually changed
	 */
	private boolean refile(ComplianceItem check, LibraryEntry entry)
	{
		boolean moved = false;
		if (stillInOldGroup(check))
		{
			ComplianceGroup target = entry.group() == null
				? null
				: groupRepository.findOrCreateByName(entry.group());
			moved = !sameGroup(check.getGroup(), target);
			if (moved)
			{
				check.setGroup(target);
			}
		}
		boolean labelled = !entry.control().equals(check.getControl());
		if (labelled)
		{
			check.setControl(entry.control());
		}
		return moved || labelled;
	}

	/**
	 * Whether the check is still where the 2013 catalog left it: in one of the
	 * old domain groups, or in no group at all. Anywhere else, an administrator
	 * put it there.
	 */
	private static boolean stillInOldGroup(ComplianceItem check)
	{
		return check.getGroup() == null || RETIRED_GROUPS.contains(check.getGroup().getName());
	}

	private static boolean sameGroup(ComplianceGroup current, ComplianceGroup target)
	{
		if (current == null || target == null)
		{
			return current == target;
		}
		return Objects.equals(current.id, target.id);
	}

	private void dropRetiredGroups()
	{
		for (String name : RETIRED_GROUPS)
		{
			for (ComplianceGroup group : groupRepository.list("name", name))
			{
				if (itemRepository.count("group", group) == 0)
				{
					groupRepository.delete(group);
				}
				else
				{
					LOG.info("Keeping group '{}' — it still holds {} check(s) that are not ours",
						name, itemRepository.count("group", group));
				}
			}
		}
	}
}
