package com.pruefstein.report.service;

import java.time.Instant;
import java.util.List;

import com.pruefstein.device.domain.Device;
import com.pruefstein.report.flow.PeriodicReportingFlow;
import com.pruefstein.report.repository.ReportRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Stands down the devices of a person who was deleted. Without it their
 * machines keep their reporting cycle, and the deadline job files a MISSING
 * report for them every interval, for good.
 *
 * <p>
 * Callers must already be in a transaction.
 */
@ApplicationScoped
public class DeviceRetirement
{
	@Inject
	ReportRepository reportRepository;

	@Inject
	PeriodicReportingFlow periodicReportingFlow;

	@Inject
	WorkflowInstances workflowInstances;

	/**
	 * Ends the devices' cycles and archives the person's runs, which stay as
	 * evidence but leave the report list and the fleet figures.
	 *
	 * @param appUserId
	 *            the person being deleted, or {@code null} when they are
	 *            already gone and only their devices are left
	 * @return how many runs were archived
	 */
	public int retire(Long appUserId, List<Device> devices)
	{
		for (Device device : devices)
		{
			String abandoned = device.getPeriodicFlowInstanceId();
			device.retire();
			workflowInstances.discard(periodicReportingFlow, abandoned);
		}
		return reportRepository.archive(appUserId, devices.stream().map(Device::getDeviceId).toList(),
			Instant.now());
	}
}
