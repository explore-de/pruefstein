package com.pruefstein.homebrew.service;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the Homebrew catalogue loaded. A failed refresh is logged and
 * forgotten: a homepage link that is a day stale is no loss, and until the
 * first refresh succeeds the report links to formulae.brew.sh instead.
 */
@ApplicationScoped
public class HomebrewCatalogRefreshJob
{
	private static final Logger LOG = LoggerFactory.getLogger(HomebrewCatalogRefreshJob.class);

	@Inject
	HomebrewCatalog catalog;

	@ConfigProperty(name = "pruefstein.homebrew.feed-enabled", defaultValue = "true")
	boolean enabled;

	/**
	 * The catalogue lives in memory, so every start begins empty. Loading it
	 * off the startup thread keeps a slow download from holding up the boot.
	 */
	void refreshOnStartup(@Observes StartupEvent event)
	{
		Thread.ofVirtual().name("homebrew-catalog").start(this::refresh);
	}

	@Scheduled(every = "{pruefstein.homebrew.refresh-interval}", delayed = "{pruefstein.homebrew.refresh-interval}", concurrentExecution = ConcurrentExecution.SKIP)
	void refreshPeriodically()
	{
		refresh();
	}

	private void refresh()
	{
		if (!enabled)
		{
			return;
		}
		try
		{
			int known = catalog.refresh();
			LOG.info("Loaded {} Homebrew formulae and casks", known);
		}
		catch (Exception e)
		{
			LOG.warn("Could not refresh the Homebrew catalogue — keeping what is already loaded: {}", e.toString());
		}
	}
}
