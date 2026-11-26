package com.pruefstein.report.bootstrap;

import java.time.Instant;

import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The backfill for Macs that are on record twice: once under the hostname an
 * agent fell back to, and once under their hardware UUID.
 */
@QuarkusTest
@TestTransaction
class HostnameStandInRetirementTest
{
	@Inject
	HostnameStandInRetirement retirement;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRepository reportRepository;

	@Test
	void retiresTheHostnameRecordOfAMacThatReportsUnderItsUuid()
	{
		// given
		Device standIn = device("Standin-Mac.local", "Standin-Mac.local", "standin@example.com");
		Device real = device("STANDIN-UUID", "Standin-Mac.local", "standin@example.com");
		Report missing = missingReport("Standin-Mac.local", "standin@example.com");

		// when
		retirement.retireStandIns();

		// then
		assertNull(standIn.getPeriodicFlowInstanceId());
		assertNotNull(real.getPeriodicFlowInstanceId());
		reportRepository.getEntityManager().refresh(missing);
		assertNotNull(missing.getArchivedAt());
	}

	@Test
	void leavesAHostnameDeviceWithNoUuidTwin()
	{
		// given an old agent's Mac, only ever on record under its hostname
		Device alone = device("Alone-Mac.local", "Alone-Mac.local", "alone@example.com");

		// when
		retirement.retireStandIns();

		// then
		assertNotNull(alone.getPeriodicFlowInstanceId());
	}

	private Device device(String deviceId, String hostname, String login)
	{
		Device device = new Device();
		device.setDeviceId(deviceId);
		device.setUserId(hostname);
		device.setKeycloakUser(login);
		device.setLastReportAt(Instant.now());
		device.setPeriodicFlowInstanceId(deviceId + "-no-such-instance");
		deviceRepository.persist(device);
		return device;
	}

	private Report missingReport(String deviceId, String login)
	{
		Report report = new Report();
		report.setDeviceId(deviceId);
		report.setUserId(deviceId);
		report.setKeycloakUser(login);
		report.setCheckedAt(Instant.now());
		report.setStatus(ReportStatus.MISSING);
		reportRepository.persist(report);
		return report;
	}
}
