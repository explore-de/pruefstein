package com.pruefstein.report.api;

import java.time.Instant;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

/**
 * The macOS version filter the dashboard's version chart links to.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "admin") })
class ReportsOsFilterTest
{
	private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

	@Inject
	ReportRepository reportRepository;

	private final List<Long> seeded = new ArrayList<>();

	@BeforeEach
	void setUp()
	{
		// os-updater was on 26.6.2 and has since moved to 26.7; os-stayer is
		// still on 26.6.2
		seed("os-device-old", "os-updater", NOW.minusSeconds(3600), "26.6.2");
		seed("os-device-new", "os-updater", NOW, "26.7");
		seed("os-device-stay", "os-stayer", NOW.minusSeconds(60), " 26.6.2 ");
	}

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> seeded.forEach(reportRepository::deleteById));
		seeded.clear();
	}

	private void seed(String deviceId, String user, Instant checkedAt, String osVersion)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = new Report();
			report.setDeviceId(deviceId);
			report.setUserId(user + "@example.com");
			report.setKeycloakUser(user);
			report.setCheckedAt(checkedAt);
			report.setStatus(ReportStatus.COMPLIANT);
			report.setOsVersion(osVersion);
			reportRepository.persist(report);
			seeded.add(report.id);
		});
	}

	@Test
	void theVersionIsReadOffTheLatestRun()
	{
		// given — os-updater's older run is on 26.6.2, their latest is not

		// when / then — like the chart, only machines on it now are listed,
		// and the agent's stray whitespace does not hide one
		given()
			.when().get("/Reports/index?q=os-&os=26.6.2")
			.then()
			.statusCode(200)
			.body(containsString("os-device-stay"))
			.body(not(containsString("os-device-new")))
			.body(containsString("MACOS 26.6.2"));
	}

	@Test
	void everyRunCountsWithTheBoxOff()
	{
		// given — the same three runs

		// when / then — with every run in scope, the one os-updater has since
		// moved on from matches too
		given()
			.when().get("/Reports/index?q=os-&os=26.6.2&all=1")
			.then()
			.statusCode(200)
			.body(containsString("os-device-old"))
			.body(containsString("os-device-stay"))
			.body(not(containsString("os-device-new")));
	}

	@Test
	void noVersionMeansNoFilter()
	{
		// given — no os parameter

		// when / then — every user is listed and there is no chip to lift
		given()
			.when().get("/Reports/index?q=os-")
			.then()
			.statusCode(200)
			.body(containsString("os-device-new"))
			.body(containsString("os-device-stay"))
			.body(not(containsString("MACOS ")));
	}
}
