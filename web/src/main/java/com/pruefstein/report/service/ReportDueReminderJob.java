package com.pruefstein.report.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.notification.ReportRequestMailService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires every hour to ask for a fresh report before the last one goes stale.
 *
 * <p>
 * Without this the reporting interval is enforced silently: {@code
 * PeriodicDeadlineJob} files a MISSING report the moment a device runs out of
 * time, and the first its owner hears of it is the outcome. Proving compliance
 * on a cadence only works if somebody is told the cadence has come round.
 *
 * <p>
 * {@code reminderSentAt} is stamped whether or not the SMTP handover succeeds,
 * for the reason {@link DeadlineReminderJob} gives: a mail server coming back
 * up must not turn an hourly job into an hourly mail storm.
 */
@ApplicationScoped
public class ReportDueReminderJob
{
	private static final Logger LOG = LoggerFactory.getLogger(ReportDueReminderJob.class);

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRequestMailService mailService;

	@ConfigProperty(name = "pruefstein.compliance.reporting-interval-days", defaultValue = "7")
	int reportingIntervalDays;

	@ConfigProperty(name = "pruefstein.compliance.report-due-reminder-days-before", defaultValue = "2")
	int reminderDaysBefore;

	@Scheduled(every = "1h")
	@Transactional
	void remindBeforeReportIsDue()
	{
		Instant now = Instant.now();
		// A reminder window at least as long as the interval would nudge on the
		// heels of the report that just closed the last cycle, so it is capped
		// one day short of it however the two are configured.
		long leadDays = Math.min(reminderDaysBefore, reportingIntervalDays - 1L);
		Instant remindFrom = now.minus(reportingIntervalDays - leadDays, ChronoUnit.DAYS);
		Instant overdueCutoff = now.minus(reportingIntervalDays, ChronoUnit.DAYS);

		List<Device> due = deviceRepository.findDueForReminder(remindFrom, overdueCutoff);
		if (due.isEmpty())
		{
			return;
		}
		LOG.info("Reminding about {} device(s) approaching their reporting interval", due.size());
		for (Device device : due)
		{
			try
			{
				mailService.sendReportDue(device);
			}
			catch (RuntimeException e)
			{
				// Handing off to the mailer is asynchronous and normally
				// cannot throw, but rendering the template happens here and
				// can. Either way the stamp goes on: one device that cannot be
				// mailed must not cost every later device in the batch its
				// reminder, and must not be retried hourly forever.
				LOG.error("Could not send the report-due mail for device {}",
					device.getDeviceId(), e);
			}
			device.setReminderSentAt(now);
		}
	}
}
