package com.pruefstein.dashboard.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ComplianceResult;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.dashboard.api.DeviceCard;
import com.pruefstein.dashboard.api.FailingCheck;
import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserDashboardTest
{
	@Inject
	UserDashboard dashboard;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRepository reportRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceGroupRepository groupRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Test
	void showsOnlyTheReadersOwnDevices()
	{
		String mine = "dash-mine-" + System.nanoTime();
		String theirs = "dash-theirs-" + System.nanoTime();
		device(mine, "my-mac.local");
		device(theirs, "their-mac.local");

		List<DeviceCard> cards = dashboard.cardsFor(mine);

		assertEquals(1, cards.size());
		assertEquals("my-mac.local", cards.get(0).deviceId());
	}

	@Test
	void openReportBecomesAToDoListWithItsControl()
	{
		String user = "dash-open-" + System.nanoTime();
		String deviceId = "open-mac.local";
		device(user, deviceId);
		report(user, deviceId, ReportStatus.OPEN, Instant.now().plus(5, ChronoUnit.DAYS));

		DeviceCard card = dashboard.cardsFor(user).get(0);

		assertTrue(card.isActionable());
		assertEquals(2, card.total());
		assertEquals(1, card.passed());
		assertEquals(50, card.passedPct());
		assertEquals(1, card.failureCount());
		assertEquals("1 check needs fixing", card.failureLabel());

		FailingCheck failing = card.failures().get(0);
		assertEquals("Dash Fails", failing.name());
		assertEquals("A.10 Cryptography", failing.control());
		assertEquals("it is off", failing.summary());
		assertTrue(failing.hasExplanation());
	}

	@Test
	void compliantReportLeavesNothingToDo()
	{
		String user = "dash-clear-" + System.nanoTime();
		String deviceId = "clear-mac.local";
		device(user, deviceId);
		report(user, deviceId, ReportStatus.COMPLIANT, null);

		DeviceCard card = dashboard.cardsFor(user).get(0);

		assertTrue(card.isClear());
		assertEquals(0, card.failureCount());
		assertFalse(card.neverReported());
	}

	/**
	 * A registered machine that never answered. The page owes this reader
	 * instructions, not a verdict, so the card has to say so rather than read
	 * as compliant-by-default.
	 */
	@Test
	void deviceWithoutAReportSaysSo()
	{
		String user = "dash-silent-" + System.nanoTime();
		device(user, "silent-mac.local");

		DeviceCard card = dashboard.cardsFor(user).get(0);

		assertTrue(card.neverReported());
		assertFalse(card.isClear());
		assertEquals(0, card.total());
	}

	private void device(String keycloakUser, String deviceId)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			Device device = new Device();
			device.setDeviceId(deviceId);
			device.setUserId(keycloakUser);
			device.setKeycloakUser(keycloakUser);
			device.setLastReportAt(Instant.now().minus(1, ChronoUnit.DAYS));
			deviceRepository.persist(device);
		});
	}

	/**
	 * One passing check and one failing one, so the bar has something to say.
	 */
	private void report(String keycloakUser, String deviceId, ReportStatus status, Instant deadline)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceGroup group = new ComplianceGroup();
			group.setName("A.10 Cryptography");
			groupRepository.persist(group);

			Report report = new Report();
			report.setDeviceId(deviceId);
			report.setUserId(keycloakUser);
			report.setKeycloakUser(keycloakUser);
			report.setCheckedAt(Instant.now().minus(1, ChronoUnit.HOURS));
			report.setStatus(status);
			report.setDeadline(deadline);
			reportRepository.persist(report);

			addResult(report, item("Dash Passes", group), true, null, null);
			if (status != ReportStatus.COMPLIANT)
			{
				addResult(report, item("Dash Fails", group), false, "it is off", "turn it on");
			}
		});
	}

	private ComplianceItem item(String name, ComplianceGroup group)
	{
		ExpressionCheck check = new ExpressionCheck();
		check.setName(name);
		check.setGroup(group);
		check.setQuery("SELECT 1");
		check.setExpectedExpression("results.size() > 0");
		itemRepository.persist(check);
		return check;
	}

	private void addResult(Report report, ComplianceItem item, boolean passed, String summary,
		String explanation)
	{
		ComplianceResult result = new ComplianceResult();
		result.setReport(report);
		result.setItem(item);
		result.setPassed(passed);
		result.setOutput("[]");
		result.setAiShortDescription(summary);
		result.setAiLongExplanation(explanation);
		resultRepository.persist(result);
	}
}
