package com.pruefstein.report.flow;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An overdue device has to be recorded as missing and given a fresh cycle
 * without any event reaching its workflow instance — after a restart nothing is
 * listening for one.
 */
@QuarkusTest
class PeriodicDeadlineJobTest
{
	private static final String DEVICE = "overdue-job-device";

	@Inject
	PeriodicDeadlineJob periodicDeadlineJob;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRepository reportRepository;

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			reportRepository.delete("deviceId", DEVICE);
			deviceRepository.delete("deviceId", DEVICE);
		});
	}

	@Test
	void anOverdueDeviceIsRecordedMissingAndGivenANewCycle()
	{
		// given — last reported well beyond the interval
		persistDevice(Instant.now().minus(60, ChronoUnit.DAYS), "stale-instance");

		// when
		periodicDeadlineJob.checkOverdueDevices();

		// then
		List<Report> reports = QuarkusTransaction.requiringNew()
			.call(() -> reportRepository.list("deviceId", DEVICE));
		assertEquals(1, reports.size());
		assertEquals(ReportStatus.MISSING, reports.get(0).getStatus());
		assertNotNull(reports.get(0).getDeadline(), "a missing report should carry the due date it missed");
		assertTrue(reports.get(0).getDeadline().isBefore(Instant.now().minus(29, ChronoUnit.DAYS)),
			"the deadline is the missed due date, not when the job noticed");

		Device device = reloadDevice();
		assertNotEquals("stale-instance", device.getPeriodicFlowInstanceId(),
			"a fresh cycle should have been started");
		assertTrue(device.getLastReportAt().isAfter(Instant.now().minusSeconds(60)),
			"the clock should be reset so the next tick does not re-fire");
	}

	@Test
	void aDeviceReportingOnTimeIsLeftAlone()
	{
		// given
		persistDevice(Instant.now(), "live-instance");

		// when
		periodicDeadlineJob.checkOverdueDevices();

		// then
		assertTrue(QuarkusTransaction.requiringNew()
			.call(() -> reportRepository.list("deviceId", DEVICE)).isEmpty());
		assertEquals("live-instance", reloadDevice().getPeriodicFlowInstanceId());
	}

	private void persistDevice(Instant lastReportAt, String flowInstanceId)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			Device device = new Device();
			device.setDeviceId(DEVICE);
			device.setUserId("overdue-user");
			device.setKeycloakUser("overdue-user");
			device.setLastReportAt(lastReportAt);
			device.setPeriodicFlowInstanceId(flowInstanceId);
			deviceRepository.persist(device);
		});
	}

	private Device reloadDevice()
	{
		return QuarkusTransaction.requiringNew()
			.call(() -> deviceRepository.findByDeviceId(DEVICE).orElseThrow());
	}
}
