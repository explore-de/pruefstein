package com.pruefstein.report.bootstrap;

import java.time.Instant;

import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The backfill for people deleted before deleting retired their devices. An
 * unowned device alone does not say its owner was deleted, so what it must get
 * right is telling the two apart.
 */
@QuarkusTest
@TestTransaction
class OrphanedDeviceRetirementTest
{
	@Inject
	OrphanedDeviceRetirement retirement;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRepository reportRepository;

	@Inject
	UserRepository userRepository;

	@Test
	void retiresTheDeviceOfSomebodyWhoIsGone()
	{
		// given a leaver's Mac, still in its cycle, and its MISSING report
		Device device = device("orphan-leaver-mac", "leaver@example.com");
		Report missing = missingReport("orphan-leaver-mac", "leaver@example.com");

		// when
		retirement.retireOrphans();

		// then it is no longer chased, and its history is archived
		assertNull(device.getPeriodicFlowInstanceId());
		reportRepository.getEntityManager().refresh(missing);
		assertNotNull(missing.getArchivedAt());
	}

	@Test
	void leavesTheUnownedDeviceOfSomebodyStillHere()
	{
		// given a Mac registered before devices carried an owner, silent since,
		// whose person is still on record
		AppUser stayer = new AppUser();
		stayer.setFirstname("Still");
		stayer.setLastname("Here");
		stayer.setMail("Stayer@example.com");
		userRepository.persist(stayer);
		Device device = device("orphan-stayer-mac", "stayer@example.com");
		Report missing = missingReport("orphan-stayer-mac", "stayer@example.com");

		// when
		retirement.retireOrphans();

		// then they are still chased — they are exactly who needs it
		assertNotNull(device.getPeriodicFlowInstanceId());
		reportRepository.getEntityManager().refresh(missing);
		assertNull(missing.getArchivedAt());
	}

	private Device device(String deviceId, String login)
	{
		Device device = new Device();
		device.setDeviceId(deviceId);
		device.setUserId(deviceId + ".local");
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
		report.setUserId(deviceId + ".local");
		report.setKeycloakUser(login);
		report.setCheckedAt(Instant.now());
		report.setStatus(ReportStatus.MISSING);
		reportRepository.persist(report);
		return report;
	}
}
