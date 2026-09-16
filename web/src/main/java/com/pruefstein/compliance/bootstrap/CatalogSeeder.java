package com.pruefstein.compliance.bootstrap;

import com.pruefstein.compliance.bootstrap.ComplianceCatalog.CheckDef;
import com.pruefstein.compliance.domain.AppBlacklistCheck;
import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
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
 * Puts the baseline compliance checks into every deployment, production
 * included, so a fresh install has something to measure devices against instead
 * of an empty Groups &amp; Items screen.
 *
 * <p>
 * Each catalog entry is applied at most once per database, tracked by
 * {@link SeedLedger}. That is what makes this safe to run on every boot: an
 * administrator's edits are never reconciled away, a check they deleted is
 * never resurrected, and a check added to the catalog in a later release
 * appears on the next start without duplicating the ones already there.
 */
@ApplicationScoped
public class CatalogSeeder
{
	/**
	 * The demo data in {@code Startup} builds on these checks, so the catalog
	 * has to be in place before any other startup seeding runs.
	 */
	public static final int PRIORITY = 1000;

	private static final Logger LOG = LoggerFactory.getLogger(CatalogSeeder.class);

	@Inject
	SeedLedger ledger;

	@Inject
	ComplianceGroupRepository groupRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@ConfigProperty(name = "pruefstein.compliance.seed-catalog", defaultValue = "true")
	boolean seedingEnabled;

	@Transactional
	void seedOnStartup(@Observes @Priority(PRIORITY) StartupEvent event)
	{
		if (!seedingEnabled)
		{
			return;
		}
		int applied = seed();
		if (applied > 0)
		{
			LOG.info("Seeded {} baseline compliance check(s)", applied);
		}
	}

	/**
	 * @return how many checks this call added
	 */
	public int seed()
	{
		int applied = 0;
		for (CheckDef def : ComplianceCatalog.CHECKS)
		{
			if (!ledger.claim(def.key()))
			{
				continue;
			}
			itemRepository.persist(toItem(def));
			applied++;
		}
		return applied;
	}

	private ComplianceItem toItem(CheckDef def)
	{
		ComplianceItem item;
		if (def.generated())
		{
			item = new AppBlacklistCheck();
		}
		else
		{
			ExpressionCheck check = new ExpressionCheck();
			check.setQuery(def.query());
			check.setExpectedExpression(def.expression());
			item = check;
		}
		item.setName(def.name());
		if (def.groupKey() != null)
		{
			item.setGroup(group(def.groupKey()));
		}
		return item;
	}

	/**
	 * Groups are matched by name rather than ledgered: a check being created
	 * needs somewhere to live, so if its group is gone it is recreated with it.
	 * A retired group counts as gone — a check added by a later release must
	 * not land somewhere no one can reach it.
	 */
	private ComplianceGroup group(String groupKey)
	{
		String name = ComplianceCatalog.groupName(groupKey);
		return groupRepository.findActiveByName(name)
			.orElseGet(() -> {
				ComplianceGroup group = new ComplianceGroup();
				group.setName(name);
				groupRepository.persist(group);
				return group;
			});
	}
}
