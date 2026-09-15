package com.pruefstein.report.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * When a device's proof of compliance goes stale.
 *
 * <p>
 * One answer for everybody who has to give it: the reminder mail says the date,
 * the dashboard counts down to it, and the periodic job works to it. Three
 * copies of {@code lastReportAt + interval} would eventually disagree, and the
 * reader would have no way of telling which one to believe.
 */
@ApplicationScoped
public class ReportingSchedule
{
	@ConfigProperty(name = "pruefstein.compliance.reporting-interval-days", defaultValue = "7")
	int intervalDays;

	public int intervalDays()
	{
		return intervalDays;
	}

	/**
	 * A device that has never reported is due now — the clock on proof starts
	 * when the device appears, not when it first gets round to answering.
	 */
	public Instant dueAt(Instant lastReportAt)
	{
		return lastReportAt != null
			? lastReportAt.plus(intervalDays, ChronoUnit.DAYS)
			: Instant.now();
	}

	/** Rounded up, so a due date 47 hours out still reads as "2 days". */
	public long daysUntil(Instant dueAt)
	{
		long hours = Duration.between(Instant.now(), dueAt).toHours();
		return Math.max(0, (long)Math.ceil(hours / 24.0));
	}

	public boolean overdue(Instant dueAt)
	{
		return dueAt != null && dueAt.isBefore(Instant.now());
	}

	public String intervalLabel()
	{
		return intervalDays == 1 ? "1 day" : intervalDays + " days";
	}
}
