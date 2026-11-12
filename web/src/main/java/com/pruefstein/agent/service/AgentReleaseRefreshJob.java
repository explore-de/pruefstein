package com.pruefstein.agent.service;

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
 * Keeps the newest agent release known. A failed refresh is logged and
 * forgotten: being a few hours late to call an agent outdated costs nothing.
 */
@ApplicationScoped
public class AgentReleaseRefreshJob
{
	private static final Logger LOG = LoggerFactory.getLogger(AgentReleaseRefreshJob.class);

	@Inject
	AgentReleaseCatalog catalog;

	@ConfigProperty(name = "pruefstein.agent.release-feed-enabled", defaultValue = "true")
	boolean enabled;

	/** Held in memory, so every start begins empty; off the startup thread. */
	void refreshOnStartup(@Observes StartupEvent event)
	{
		Thread.ofVirtual().name("agent-release").start(this::refresh);
	}

	@Scheduled(every = "{pruefstein.agent.release-refresh-interval}", delayed = "{pruefstein.agent.release-refresh-interval}", concurrentExecution = ConcurrentExecution.SKIP)
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
			LOG.debug("Newest agent release is {}", catalog.refresh());
		}
		catch (Exception e)
		{
			LOG.warn("Could not look up the newest agent release — keeping what is already known: {}", e.toString());
		}
	}
}
