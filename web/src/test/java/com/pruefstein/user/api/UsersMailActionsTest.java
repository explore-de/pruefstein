package com.pruefstein.user.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.notification.ReportRequestMailService;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two ways an admin asks somebody to report, and the one that happens
 * without being asked for. Which mail goes out follows from whether the person
 * has a device, so the admin never has to know.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
class UsersMailActionsTest
{
	private static final String MAIL = "mail-actions@example.com";

	@Inject
	UserRepository userRepository;

	@Inject
	DeviceRepository deviceRepository;

	@InjectMock
	ReportRequestMailService.Sender sender;

	private final List<Long> seededDevices = new ArrayList<>();

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			seededDevices.forEach(deviceRepository::deleteById);
			// The bulk delete below goes straight to SQL, so the device rows
			// have to be gone from the database and not just from the session
			// before the user they point at can go.
			deviceRepository.flush();
			userRepository.delete("mail", MAIL);
		});
		seededDevices.clear();
	}

	@Test
	void addingAUserInvitesThem()
	{
		// given / when — a new hire is typed into the Users screen
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("firstname", "New")
			.formParam("lastname", "Hire")
			.formParam("mail", MAIL)
			.when().post("/Users/create")
			.then()
			.statusCode(lessThan(400));

		// then they are told how to set the agent up, unprompted
		assertSubjectSent("set up your device compliance check");
	}

	@Test
	void requestingAReportFromSomeoneWithNoDeviceSendsTheInviteInstead()
	{
		// given someone who has never reported
		AppUser user = seedUser();
		Mockito.clearInvocations(sender);

		// when
		post("/Users/requestReport", user.id);

		// then a deadline would mean nothing to them — they need the setup
		assertSubjectSent("set up your device compliance check");
	}

	@Test
	void requestingAReportMailsEveryDeviceThePersonHas()
	{
		// given somebody with two Macs, each proving itself separately
		AppUser user = seedUser();
		seedDevice(user, "mail-actions-laptop");
		seedDevice(user, "mail-actions-desktop");
		Mockito.clearInvocations(sender);

		// when
		post("/Users/requestReport", user.id);

		// then
		Mockito.verify(sender, Mockito.times(2))
			.send(Mockito.eq(MAIL), Mockito.anyString(), Mockito.any());
	}

	@Test
	void resendingTheInviteWorksAfterTheFirstOneIsLost()
	{
		// given
		AppUser user = seedUser();
		Mockito.clearInvocations(sender);

		// when
		post("/Users/invite", user.id);

		// then
		assertSubjectSent("set up your device compliance check");
	}

	@Test
	void deletingSomeoneLeavesTheirDeviceBehindRatherThanFailing()
	{
		// given somebody who has reported from a company Mac
		AppUser user = seedUser();
		seedDevice(user, "mail-actions-leaver");

		// when the account goes — a leaver, a duplicate, a typo
		post("/Users/delete", user.id);

		// then the account is gone and the device survives, unowned, holding
		// the reporting history and ready to re-link on its next report
		QuarkusTransaction.requiringNew().run(() -> {
			assertNull(userRepository.findById(user.id));
			Device device = deviceRepository.findByDeviceId("mail-actions-leaver").orElseThrow();
			assertNull(device.getAppUser());
		});
	}

	@Test
	void aMissingUserIsNotFoundRatherThanMailedToNobody()
	{
		// given (no user with this id)

		// when / then
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("id", 999999999L)
			.when().post("/Users/requestReport")
			.then()
			.statusCode(404);
		Mockito.verifyNoInteractions(sender);
	}

	private void post(String path, Long id)
	{
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("id", id)
			.when().post(path)
			.then()
			.statusCode(lessThan(400));
	}

	private void assertSubjectSent(String fragment)
	{
		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		Mockito.verify(sender).send(Mockito.eq(MAIL), subject.capture(), Mockito.any());
		assertTrue(subject.getValue().contains(fragment),
			"expected a subject containing \"" + fragment + "\" but got: " + subject.getValue());
	}

	private AppUser seedUser()
	{
		AppUser[] holder = new AppUser[1];
		QuarkusTransaction.requiringNew().run(() -> {
			AppUser user = new AppUser();
			user.setFirstname("Mail");
			user.setLastname("Actions");
			user.setMail(MAIL);
			userRepository.persist(user);
			holder[0] = user;
		});
		return holder[0];
	}

	private void seedDevice(AppUser user, String deviceId)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			Device device = new Device();
			device.setDeviceId(deviceId);
			device.setUserId(MAIL);
			device.setKeycloakUser("mail-actions");
			device.setAppUser(userRepository.findById(user.id));
			device.setLastReportAt(Instant.now());
			deviceRepository.persist(device);
			seededDevices.add(device.id);
		});
	}
}
