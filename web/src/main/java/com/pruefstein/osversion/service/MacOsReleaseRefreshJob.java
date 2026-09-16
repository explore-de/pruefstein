package com.pruefstein.osversion.service;

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
 * Keeps the macOS release catalogue current.
 * <p>
 * Apple posts a handful of releases a month, so this is a slow poll rather than
 * anything urgent. A failed refresh is logged and forgotten: the catalogue
 * already holds the last good copy, and reporting "we do not know what the
 * latest macOS is" on every report page would be worse than being a few hours
 * stale.
 */
@ApplicationScoped
public class MacOsReleaseRefreshJob
{
	private static final Logger LOG = LoggerFactory.getLogger(MacOsReleaseRefreshJob.class);

	@Inject
	MacOsReleaseCatalog catalog;

	@ConfigProperty(name = "pruefstein.macos.feed-enabled", defaultValue = "true")
	boolean enabled;

	/**
	 * A fresh deployment has an empty catalogue and no way to judge a report
	 * until the first refresh, so it does not wait for the first interval.
	 */
	void refreshOnStartup(@Observes StartupEvent event)
	{
		refresh();
	}

	@Scheduled(every = "{pruefstein.macos.refresh-interval}", concurrentExecution = ConcurrentExecution.SKIP)
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
			int added = catalog.refresh();
			if (added > 0)
			{
				LOG.info("Learned {} new macOS release(s) from Apple's feed", added);
			}
		}
		catch (Exception e)
		{
			LOG.warn("Could not refresh the macOS release catalogue — keeping what is already stored", e);
		}
	}
}
