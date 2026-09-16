package com.pruefstein.report.api;

import java.time.Instant;

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

/**
 * What the report header says about the operating system, and in which colour.
 * <p>
 * Each report carries the newest release that existed when it was filed, so
 * these fixtures do not depend on the live catalog — or on what Apple ships
 * next week.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "admin") })
class ReportOsVersionHeaderTest
{
	@Inject
	ReportRepository reportRepository;

	private Long reportId;

	@AfterEach
	void tearDown()
	{
		if (reportId != null)
		{
			QuarkusTransaction.requiringNew().run(() -> reportRepository.deleteById(reportId));
			reportId = null;
		}
	}

	private void seed(String osVersion, String build, String latest)
	{
		Long[] ids = new Long[1];
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = new Report();
			report.setDeviceId("os-header-device");
			report.setUserId("os-header-user");
			report.setKeycloakUser("admin");
			report.setCheckedAt(Instant.now());
			report.setStatus(ReportStatus.COMPLIANT);
			report.setOsName("macOS");
			report.setOsVersion(osVersion);
			report.setOsBuild(build);
			report.setOsLatestVersion(latest);
			reportRepository.persist(report);
			ids[0] = report.id;
		});
		reportId = ids[0];
	}

	@Test
	void showsTheVersionAsCurrentWhenItIsTheNewest()
	{
		// given
		seed("27.0", "26A428", "27.0");

		// when / then
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString("27.0"))
			.body(containsString("CURRENT"))
			.body(not(containsString("FIX BEHIND")))
			.body(not(containsString("USES A")));
	}

	@Test
	void marksAMissingFixInAmber()
	{
		// given — same train, same feature update, one fix short
		seed("26.7", "25G229", "26.7.1");

		// when / then
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString("FIX BEHIND"))
			.body(containsString("bg-amber-400"))
			.body(containsString("latest was 26.7.1"))
			.body(not(containsString("USES A")));
	}

	@Test
	void marksAMissingFeatureUpdateInRed()
	{
		// given — same train, an older feature update
		seed("26.5.1", "25F80", "26.7");

		// when / then
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString("UPDATE BEHIND"))
			.body(containsString("bg-red-400"))
			.body(not(containsString("USES A")));
	}

	@Test
	void marksAWholeTrainBehindInRedAndNamesItsAge()
	{
		// given — macOS 15 shipped in 2024; the report is read today
		seed("15.7.9", "24G830", "26.7");

		// when / then
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString("MAJOR BEHIND"))
			.body(containsString("bg-red-600"))
			// macOS 15 shipped in 2024, so it is two trains and two years back
			.body(containsString("USES A 2-YEAR-OLD VERSION"));
	}

	@Test
	void saysSoPlainlyWhenNoVersionWasReported()
	{
		// given — an agent older than this field, or an osquery that failed
		seed(null, null, "27.0");

		// when / then
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString("not reported"))
			.body(not(containsString("MAJOR BEHIND")));
	}

	@Test
	void showsTheVersionUnjudgedWhenApplesFeedWasNeverReached()
	{
		// given — a report filed while the catalog was empty
		seed("15.7.9", "24G830", null);

		// when / then — the version is worth showing; the colour is not
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString("15.7.9"))
			.body(not(containsString("MAJOR BEHIND")))
			.body(not(containsString("FIX BEHIND")));
	}
}
