package com.pruefstein.report.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "admin") })
class ReportsDeadlineColumnTest
{
	@Inject
	ReportRepository reportRepository;

	private final List<Long> seeded = new ArrayList<>();

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> seeded.forEach(reportRepository::deleteById));
		seeded.clear();
	}

	private void seed(String user, ReportStatus status, Instant deadline)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = new Report();
			report.setDeviceId("deadline-device");
			report.setUserId(user + "@example.com");
			report.setKeycloakUser(user);
			report.setCheckedAt(Instant.now());
			report.setStatus(status);
			report.setDeadline(deadline);
			reportRepository.persist(report);
			seeded.add(report.id);
		});
	}

	@Test
	void aDeadlineStillDaysAwayStaysCalm()
	{
		// given — the far end of a fresh seven-day window
		seed("deadline-calm", ReportStatus.OPEN, Instant.now().plus(7, ChronoUnit.DAYS));

		// when / then
		given()
			.when().get("/Reports/index?q=deadline-calm")
			.then()
			.statusCode(200)
			.body(containsString("text-stone-500"))
			.body(not(containsString("bg-red-600")));
	}

	@Test
	void aDeadlineInsideTheLastDayGoesRed()
	{
		// given
		seed("deadline-urgent", ReportStatus.OPEN, Instant.now().plus(6, ChronoUnit.HOURS));

		// when / then — the colour, and the words that say what it means
		given()
			.when().get("/Reports/index?q=deadline-urgent")
			.then()
			.statusCode(200)
			.body(containsString("bg-red-500"))
			.body(containsString("title=\"due today\""));
	}

	@Test
	void aDeadlineThatHasPassedIsUnmissable()
	{
		// given
		seed("deadline-overdue", ReportStatus.OPEN, Instant.now().minus(1, ChronoUnit.DAYS));

		// when / then
		given()
			.when().get("/Reports/index?q=deadline-overdue")
			.then()
			.statusCode(200)
			.body(containsString("bg-red-600"))
			.body(containsString("title=\"overdue\""));
	}

	@Test
	void aClosedReportsDeadlineIsLeftAlone()
	{
		// given — a report that was finalized after blowing its deadline. The
		// window is over; reddening it now is shouting about the past.
		seed("deadline-closed", ReportStatus.NON_COMPLIANT, Instant.now().minus(3, ChronoUnit.DAYS));

		// when / then
		given()
			.when().get("/Reports/index?q=deadline-closed")
			.then()
			.statusCode(200)
			.body(not(containsString("bg-red-600")))
			.body(not(containsString("title=\"overdue\"")));
	}
}
