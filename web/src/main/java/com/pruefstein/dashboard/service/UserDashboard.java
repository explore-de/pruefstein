package com.pruefstein.dashboard.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceResult;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.dashboard.api.DeviceCard;
import com.pruefstein.dashboard.api.FailingCheck;
import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.report.service.ReportingSchedule;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Builds one person's view of their own machines.
 *
 * <p>
 * Everything here is keyed off the signed-in user's own identifier — there is
 * no code path that widens to the estate, because the question this page
 * answers ("do I have to do something?") never needs anybody else's devices.
 */
@ApplicationScoped
public class UserDashboard
{
	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRepository reportRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	ReportingSchedule schedule;

	public List<DeviceCard> cardsFor(String keycloakUser)
	{
		return deviceRepository.findByKeycloakUser(keycloakUser).stream()
			.map(device -> card(device, keycloakUser))
			.toList();
	}

	private DeviceCard card(Device device, String keycloakUser)
	{
		Instant dueAt = schedule.dueAt(device.getLastReportAt());
		Optional<Report> latest = reportRepository.findLatestForDevice(device.getDeviceId(), keycloakUser);
		if (latest.isEmpty())
		{
			return new DeviceCard(device.getDeviceId(), null, null, null, null, 0, 0, List.of(),
				dueAt, schedule.daysUntil(dueAt), schedule.overdue(dueAt));
		}

		Report report = latest.get();
		// Sorted by name so the to-do list keeps its order between runs; a list
		// that reshuffles reads as new work even when nothing changed.
		List<ComplianceResult> results = resultRepository.list("report",
			Sort.by("item.name").ascending(), report);

		// A check retired since this run is not on anyone's to-do list any
		// more, so it leaves the failures and joins the passed count.
		List<FailingCheck> failures = results.stream()
			.filter(ComplianceResult::isFailing)
			.map(UserDashboard::failing)
			.toList();
		int passed = results.size() - failures.size();

		return new DeviceCard(device.getDeviceId(), report.id, report.getStatus(),
			report.getCheckedAt(), report.getDeadline(), passed, results.size(), failures,
			dueAt, schedule.daysUntil(dueAt), schedule.overdue(dueAt));
	}

	private static FailingCheck failing(ComplianceResult result)
	{
		ComplianceGroup group = result.getItem().getGroup();
		return new FailingCheck(
			result.getItem().getName(),
			group != null ? group.getName() : null,
			result.getAiShortDescription(),
			result.getAiLongExplanation());
	}
}
