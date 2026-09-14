package com.pruefstein.user.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.pruefstein.notification.ReportRequestMailService;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

/**
 * The Users screen renders its last-report column. The states worth pinning are
 * the ones an admin reads differently: a fresh pass, an unfixed failure with a
 * date on it, and a colleague nothing has ever reported for.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
class UsersIndexTest
{
	@Inject
	UserRepository userRepository;

	@Inject
	ReportRepository reportRepository;

	@InjectMock
	ReportRequestMailService.Sender sender;

	private final List<Long> seededUsers = new ArrayList<>();
	private final List<Long> seededReports = new ArrayList<>();

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			seededReports.forEach(reportRepository::deleteById);
			seededUsers.forEach(userRepository::deleteById);
		});
		seededReports.clear();
		seededUsers.clear();
	}

	@Test
	void showsTheStatusAndAgeOfEachUsersNewestRun()
	{
		// given a user whose latest run passed two days ago
		AppUser user = seedUser("Greta", "Green", "index-green@example.com");
		seedReport(user, Instant.now().minus(9, ChronoUnit.DAYS), ReportStatus.NON_COMPLIANT, null);
		seedReport(user, Instant.now().minus(2, ChronoUnit.DAYS), ReportStatus.COMPLIANT, null);

		// when / then — the newest run is the one on the row
		given().when().get("/Users/index")
			.then()
			.statusCode(200)
			.body(containsString("COMPLIANT"))
			.body(containsString("2 days ago"));
	}

	@Test
	void showsTheRemediationDateForAnUnfixedRun()
	{
		// given an open report with a week left to run
		AppUser user = seedUser("Otto", "Open", "index-open@example.com");
		Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
		seedReport(user, Instant.now().minus(1, ChronoUnit.HOURS), ReportStatus.OPEN, deadline);

		// when / then — an unfixed run is read forwards, not backwards: what
		// matters is the date it has to be fixed by, not when it was taken
		given().when().get("/Users/index")
			.then()
			.statusCode(200)
			.body(containsString("fix by " + deadline.toString().substring(0, 10)));
	}

	@Test
	void saysSoWhenNothingHasEverReportedForAUser()
	{
		// given a user an admin typed in and nobody has reported for
		seedUser("Nora", "Never", "index-never@example.com");

		// when / then
		given().when().get("/Users/index")
			.then()
			.statusCode(200)
			.body(containsString("Never reported"));
	}

	@Test
	void marksAReportOlderThanTheReportingIntervalAsStale()
	{
		// given a pass from three weeks ago, against a 7-day interval
		AppUser user = seedUser("Stan", "Stale", "index-stale@example.com");
		seedReport(user, Instant.now().minus(21, ChronoUnit.DAYS), ReportStatus.COMPLIANT, null);

		// when / then — the verdict is left alone; what expired is the evidence
		given().when().get("/Users/index")
			.then()
			.statusCode(200)
			.body(containsString("STALE"))
			.body(containsString("COMPLIANT"));
	}

	@Test
	void doesNotMarkAFreshReportAsStale()
	{
		// given
		AppUser user = seedUser("Fred", "Fresh", "index-fresh@example.com");
		seedReport(user, Instant.now().minus(1, ChronoUnit.DAYS), ReportStatus.COMPLIANT, null);

		// when / then
		given().when().get("/Users/index")
			.then()
			.statusCode(200)
			.body(not(containsString("STALE")));
	}

	private AppUser seedUser(String firstname, String lastname, String mail)
	{
		AppUser[] holder = new AppUser[1];
		QuarkusTransaction.requiringNew().run(() -> {
			AppUser user = new AppUser();
			user.setFirstname(firstname);
			user.setLastname(lastname);
			user.setMail(mail);
			userRepository.persist(user);
			seededUsers.add(user.id);
			holder[0] = user;
		});
		return holder[0];
	}

	private void seedReport(AppUser user, Instant checkedAt, ReportStatus status, Instant deadline)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = new Report();
			report.setAppUser(userRepository.findById(user.id));
			report.setDeviceId("index-device-" + user.id);
			report.setUserId(user.getMail());
			report.setCheckedAt(checkedAt);
			report.setStatus(status);
			report.setDeadline(deadline);
			reportRepository.persist(report);
			seededReports.add(report.id);
		});
	}
}
