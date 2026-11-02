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
 * Fires every hour to tell the owner of an overdue device that its report has
 * run out, and keeps telling them on a fixed interval until one arrives.
 *
 * <p>
 * {@link ReportDueReminderJob} warns ahead of the deadline; this is what
 * follows when the warning went unanswered. Without it the MISSING report filed
 * by {@code PeriodicDeadlineJob} would be the last anyone heard of it.
 *
 * <p>
 * {@code overdueNoticeSentAt} is stamped whether or not the SMTP handover
 * succeeds, for the reason {@link DeadlineReminderJob} gives.
 */
@ApplicationScoped
public class OverdueNoticeJob
{
	private static final Logger LOG = LoggerFactory.getLogger(OverdueNoticeJob.class);

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRequestMailService mailService;

	@ConfigProperty(name = "pruefstein.compliance.overdue-notice-interval-days", defaultValue = "1")
	int noticeIntervalDays;

	@Scheduled(every = "1h")
	@Transactional
	void noticeOverdueDevices()
	{
		Instant now = Instant.now();
		// Anything below a day would mail on every hourly tick.
		Instant lastNoticeBefore = now.minus(Math.max(1, noticeIntervalDays), ChronoUnit.DAYS);

		List<Device> overdue = deviceRepository.findDueForOverdueNotice(lastNoticeBefore);
		if (overdue.isEmpty())
		{
			return;
		}
		LOG.info("Sending the overdue notice for {} device(s)", overdue.size());
		for (Device device : overdue)
		{
			try
			{
				mailService.sendReportDue(device);
			}
			catch (RuntimeException e)
			{
				// As in ReportDueReminderJob: one device that cannot be mailed
				// must not cost the rest of the batch their notice.
				LOG.error("Could not send the overdue mail for device {}",
					device.getDeviceId(), e);
			}
			device.setOverdueNoticeSentAt(now);
		}
	}
}
