package com.pruefstein.report.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.notification.ReportRequestMailService;
import com.pruefstein.report.flow.PeriodicDeadlineJob;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What follows an unanswered reminder: the owner hears that the report ran out,
 * and hears it again every resend interval (1 day by default) until a report
 * arrives.
 */
@QuarkusTest
class OverdueNoticeJobTest
{
	private static final String MAIL = "overdue-notice@example.com";

	@Inject
	OverdueNoticeJob job;

	@Inject
	PeriodicDeadlineJob deadlineJob;

	@Inject
	PeriodicCycleService cycleService;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRepository reportRepository;

	@Inject
	UserRepository userRepository;

	@InjectMock
	ReportRequestMailService.Sender sender;

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			reportRepository.delete("deviceId like ?1", "overdue-notice-%");
			deviceRepository.delete("deviceId like ?1", "overdue-notice-%");
			userRepository.delete("mail", MAIL);
		});
	}

	@Test
	void aMissedDeadlineMarksTheDeviceOverdueAndMailsItsOwner()
	{
		// given a device whose interval ran out an hour ago
		String deviceId = seed("overdue-notice-missed", Instant.now().minus(30, ChronoUnit.DAYS)
			.minus(1, ChronoUnit.HOURS), null, null);

		// when the deadline job closes the cycle and the notice job comes round
		deadlineJob.checkOverdueDevices();
		job.noticeOverdueDevices();

		// then
		Device device = reload(deviceId);
		assertNotNull(device.getOverdueSince());
		assertNotNull(device.getOverdueNoticeSentAt());
		Mockito.verify(sender).send(Mockito.eq(MAIL), Mockito.contains("overdue"), Mockito.any());
	}

	@Test
	void doesNotResendInsideTheInterval()
	{
		// given a device told twelve hours ago
		seed("overdue-notice-recent", Instant.now(), Instant.now().minus(3, ChronoUnit.DAYS),
			Instant.now().minus(12, ChronoUnit.HOURS));

		// when
		job.noticeOverdueDevices();

		// then
		Mockito.verifyNoInteractions(sender);
	}

	@Test
	void resendsOnceTheIntervalHasPassed()
	{
		// given a device told a day and a bit ago
		String deviceId = seed("overdue-notice-again", Instant.now(), Instant.now().minus(3, ChronoUnit.DAYS),
			Instant.now().minus(25, ChronoUnit.HOURS));

		// when
		job.noticeOverdueDevices();

		// then
		Mockito.verify(sender).send(Mockito.eq(MAIL), Mockito.contains("overdue"), Mockito.any());
		assertTrue(reload(deviceId).getOverdueNoticeSentAt().isAfter(Instant.now().minusSeconds(60)));
	}

	@Test
	void leavesADeviceThatIsNotOverdueAlone()
	{
		// given
		seed("overdue-notice-fine", Instant.now().minus(29, ChronoUnit.DAYS), null, null);

		// when
		job.noticeOverdueDevices();

		// then
		Mockito.verifyNoInteractions(sender);
	}

	@Test
	void aReportClearsTheOverdueState()
	{
		// given an overdue device
		String deviceId = seed("overdue-notice-cleared", Instant.now(), Instant.now().minus(3, ChronoUnit.DAYS),
			Instant.now().minus(2, ChronoUnit.DAYS));

		// when its next cycle closes with a report
		QuarkusTransaction.requiringNew().run(() -> cycleService.completeCycle(
			deviceRepository.findByDeviceId(deviceId).orElseThrow(), true));
		job.noticeOverdueDevices();

		// then
		assertNull(reload(deviceId).getOverdueSince());
		Mockito.verifyNoInteractions(sender);
	}

	@Test
	void aSecondMissedCycleKeepsTheFirstDeadline()
	{
		// given a device overdue since five weeks ago whose next cycle also ran
		// out
		Instant firstMissed = Instant.now().minus(35, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		String deviceId = seed("overdue-notice-twice", Instant.now().minus(31, ChronoUnit.DAYS),
			firstMissed, Instant.now().minus(1, ChronoUnit.HOURS));

		// when
		deadlineJob.checkOverdueDevices();

		// then
		assertEquals(firstMissed, reload(deviceId).getOverdueSince().truncatedTo(ChronoUnit.SECONDS));
	}

	private String seed(String deviceId, Instant lastReportAt, Instant overdueSince, Instant noticeSentAt)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			AppUser user = userRepository.find("mail", MAIL).firstResultOptional()
				.orElseGet(() -> {
					AppUser fresh = new AppUser();
					fresh.setFirstname("Overdue");
					fresh.setLastname("Notice");
					fresh.setMail(MAIL);
					userRepository.persist(fresh);
					return fresh;
				});
			Device device = new Device();
			device.setDeviceId(deviceId);
			device.setUserId("overdue-notice-user");
			device.setKeycloakUser("overdue-notice-user");
			device.setAppUser(user);
			device.setLastReportAt(lastReportAt);
			device.setOverdueSince(overdueSince);
			device.setOverdueNoticeSentAt(noticeSentAt);
			device.setPeriodicFlowInstanceId("flow-" + deviceId);
			deviceRepository.persist(device);
		});
		return deviceId;
	}

	private Device reload(String deviceId)
	{
		List<Device> found = QuarkusTransaction.requiringNew()
			.call(() -> deviceRepository.list("deviceId", deviceId));
		return found.getFirst();
	}
}
