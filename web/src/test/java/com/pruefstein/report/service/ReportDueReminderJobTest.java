package com.pruefstein.report.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.notification.ReportRequestMailService;
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
 * The nudge that makes the reporting interval something people can meet. The
 * window it fires in is narrow on both sides: too early and it arrives before
 * anyone could act on it, too late and the MISSING report has already landed.
 *
 * <p>
 * Defaults under test are a 7-day interval with the reminder 2 days out, so a
 * device is due for one once its last report is between 5 and 7 days old.
 */
@QuarkusTest
class ReportDueReminderJobTest
{
	private static final String MAIL = "due-reminder@example.com";

	@Inject
	ReportDueReminderJob job;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	UserRepository userRepository;

	@InjectMock
	ReportRequestMailService.Sender sender;

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			deviceRepository.delete("deviceId like ?1", "due-reminder-%");
			userRepository.delete("mail", MAIL);
		});
	}

	@Test
	void remindsADeviceInsideTheWindowAndStampsIt()
	{
		// given a device last seen six days ago — one day before it is due
		String deviceId = seed("due-reminder-inside", 6);

		// when
		job.remindBeforeReportIsDue();

		// then
		Mockito.verify(sender).send(Mockito.eq(MAIL), Mockito.anyString(), Mockito.any());
		assertNotNull(reload(deviceId).getReminderSentAt());
	}

	@Test
	void doesNotRemindADeviceThatStillHasPlentyOfTime()
	{
		// given a device that reported two days ago
		seed("due-reminder-early", 2);

		// when
		job.remindBeforeReportIsDue();

		// then
		Mockito.verifyNoInteractions(sender);
	}

	@Test
	void doesNotRemindADeviceThatIsAlreadyOverdue()
	{
		// given a device past the interval — PeriodicDeadlineJob owns this one,
		// and a warning about a deadline that has passed helps nobody
		seed("due-reminder-late", 9);

		// when
		job.remindBeforeReportIsDue();

		// then
		Mockito.verifyNoInteractions(sender);
	}

	@Test
	void remindsOnlyOncePerCycle()
	{
		// given a device already reminded this cycle
		String deviceId = seed("due-reminder-once", 6);
		job.remindBeforeReportIsDue();
		Mockito.clearInvocations(sender);

		// when the job comes round again an hour later
		job.remindBeforeReportIsDue();

		// then
		Mockito.verifyNoInteractions(sender);
		assertNotNull(reload(deviceId).getReminderSentAt());
	}

	@Test
	void stampsTheDeviceEvenWhenTheMailFails()
	{
		// given a send that blows up
		String deviceId = seed("due-reminder-broken", 6);
		Mockito.doThrow(new RuntimeException("smtp is down"))
			.when(sender).send(Mockito.anyString(), Mockito.anyString(), Mockito.any());

		// when
		job.remindBeforeReportIsDue();

		// then the stamp still goes on, so a recovering mail server does not
		// turn an hourly job into an hourly mail storm
		assertNotNull(reload(deviceId).getReminderSentAt());
	}

	@Test
	void oneUnmailableDeviceDoesNotCostTheRestTheirReminder()
	{
		// given two devices due at once, the first of which cannot be mailed
		seed("due-reminder-first", 6);
		String second = seed("due-reminder-second", 6);
		Mockito.doThrow(new RuntimeException("smtp is down"))
			.doNothing()
			.when(sender).send(Mockito.anyString(), Mockito.anyString(), Mockito.any());

		// when
		job.remindBeforeReportIsDue();

		// then the batch ran to the end
		Mockito.verify(sender, Mockito.times(2)).send(Mockito.eq(MAIL), Mockito.anyString(), Mockito.any());
		assertNotNull(reload(second).getReminderSentAt());
	}

	private String seed(String deviceId, int daysAgo)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			AppUser user = userRepository.find("mail", MAIL).firstResultOptional()
				.orElseGet(() -> {
					AppUser fresh = new AppUser();
					fresh.setFirstname("Due");
					fresh.setLastname("Reminder");
					fresh.setMail(MAIL);
					userRepository.persist(fresh);
					return fresh;
				});
			Device device = new Device();
			device.setDeviceId(deviceId);
			device.setUserId("due-reminder-user");
			device.setKeycloakUser("due-reminder-user");
			device.setAppUser(user);
			device.setLastReportAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
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
