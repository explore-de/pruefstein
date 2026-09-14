package com.pruefstein.report.service;

import java.time.Instant;
import java.util.Map;

import com.pruefstein.device.domain.Device;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.flow.PeriodicReportingFlow;
import com.pruefstein.report.flow.WorkflowStartTrigger;
import com.pruefstein.report.repository.ReportRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Closes one reporting cycle for a device and opens the next. Shared by the
 * workflow callback and the overdue job, so a device's cycle keeps turning
 * whether or not the event reaches its workflow instance.
 *
 * <p>
 * Callers must already be in a transaction.
 */
@ApplicationScoped
public class PeriodicCycleService
{
	@Inject
	ReportRepository reportRepository;

	@Inject
	PeriodicReportingFlow periodicReportingFlow;

	@Inject
	Event<WorkflowStartTrigger> workflowStartTrigger;

	public void completeCycle(Device device, boolean reported)
	{
		if (!reported)
		{
			Report missing = new Report();
			missing.setDeviceId(device.getDeviceId());
			missing.setUserId(device.getUserId());
			missing.setKeycloakUser(device.getKeycloakUser());
			missing.setCheckedAt(Instant.now());
			missing.setStatus(ReportStatus.MISSING);
			missing.setFinalizedAt(Instant.now());
			reportRepository.persist(missing);
		}

		// Reset the clock so the next hourly tick does not immediately re-fire
		device.setLastReportAt(Instant.now());
		// The new cycle gets its own nudge, whether or not the last one was
		// answered.
		device.setReminderSentAt(null);

		var wi = periodicReportingFlow.instance(Map.of("deviceId", device.getDeviceId()));
		device.setPeriodicFlowInstanceId(wi.id());
		workflowStartTrigger.fire(new WorkflowStartTrigger(wi));
	}
}
