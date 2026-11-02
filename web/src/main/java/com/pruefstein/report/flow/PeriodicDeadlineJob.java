package com.pruefstein.report.flow;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.report.service.PeriodicCycleService;
import com.pruefstein.report.service.WorkflowInstances;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires every hour to detect devices that have not submitted a report within
 * the configured reporting interval, records a {@code MISSING} report for each
 * and starts their next cycle.
 *
 * <p>
 * The cycle is completed here rather than by handing an event to the device's
 * workflow instance: a device whose instance no longer listens — after a
 * restart it does not — would otherwise never be marked missing and never get
 * another cycle. Overdue is read from {@code lastReportAt}, so the verdict is
 * the database's rather than an event's.
 */
@ApplicationScoped
public class PeriodicDeadlineJob
{
	private static final Logger LOG = LoggerFactory.getLogger(PeriodicDeadlineJob.class);

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	PeriodicCycleService cycleService;

	@Inject
	PeriodicReportingFlow periodicReportingFlow;

	@Inject
	WorkflowInstances workflowInstances;

	@ConfigProperty(name = "pruefstein.compliance.reporting-interval-days", defaultValue = "30")
	int reportingIntervalDays;

	@Scheduled(every = "1h")
	@Transactional
	public void checkOverdueDevices()
	{
		Instant cutoff = Instant.now().minus(reportingIntervalDays, ChronoUnit.DAYS);
		List<Device> overdue = deviceRepository.findOverdue(cutoff);
		if (overdue.isEmpty())
		{
			return;
		}
		LOG.info("Marking {} overdue device(s) as missing", overdue.size());
		for (Device device : overdue)
		{
			// Capture before the cycle overwrites it with the new instance
			String abandoned = device.getPeriodicFlowInstanceId();
			cycleService.completeCycle(device, false);
			workflowInstances.discard(periodicReportingFlow, abandoned);
		}
	}
}
