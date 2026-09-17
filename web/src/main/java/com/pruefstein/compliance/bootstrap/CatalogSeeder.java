package com.pruefstein.compliance.bootstrap;

import com.pruefstein.compliance.library.ComplianceLibrary;
import com.pruefstein.compliance.library.LibraryEntry;
import com.pruefstein.compliance.library.LibraryInstantiator;
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
 * Puts every check in the built-in {@link ComplianceLibrary} into every
 * deployment, production included, so a fresh install has something to measure
 * devices against instead of an empty Groups &amp; Items screen.
 *
 * <p>
 * Each library entry is applied at most once per database, tracked by
 * {@link SeedLedger}. That is what makes this safe to run on every boot: an
 * administrator's edits are never reconciled away, a check they retired is
 * never resurrected, and an entry added to the library in a later release
 * appears on the next start without duplicating the ones already there. A
 * retired check comes back only when an admin adds it again from the Library
 * screen.
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
	ComplianceLibrary library;

	@Inject
	LibraryInstantiator instantiator;

	@ConfigProperty(name = "pruefstein.compliance.seed-catalog", defaultValue = "true")
	boolean seedingEnabled;

	@Transactional
	void seedOnStartup(@Observes @Priority(PRIORITY) StartupEvent event)
	{
		int adopted = instantiator.adoptUntagged(library.entries());
		if (adopted > 0)
		{
			LOG.info("Linked {} existing compliance check(s) to their library entries", adopted);
		}
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
		for (LibraryEntry entry : library.entries())
		{
			if (!ledger.claim(entry.key()))
			{
				continue;
			}
			instantiator.instantiate(entry);
			applied++;
		}
		return applied;
	}
}
