package com.pruefstein.report.api;

import java.time.Instant;
import java.time.LocalDate;

import com.pruefstein.osversion.domain.MacOsRelease;
import com.pruefstein.osversion.repository.MacOsReleaseRepository;
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

	@Inject
	MacOsReleaseRepository releaseRepository;

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
			.body(not(containsString("MISSING A FIX")))
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
			.body(containsString("MISSING A FIX"))
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
			.body(containsString("MISSING AN UPDATE"))
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
			.body(containsString("OLDER MACOS, MISSING UPDATES"))
			.body(containsString("bg-red-600"))
			// macOS 15 shipped in 2024, so it is two trains and two years back
			.body(containsString("USES A 2-YEAR-OLD VERSION"));
	}

	@Test
	void marksTheNewestFixOfAStillPatchedTrainInAmber()
	{
		// given — 15.7.9 is the newest Sequoia, and Apple still patches it
		seed("15.7.9", "24G830", "26.7");
		release("15.7.9", "24G830");

		try
		{
			// when / then
			given()
				.when().get("/Reports/show/" + reportId)
				.then()
				.statusCode(200)
				.body(containsString("OLDER MACOS, FULLY PATCHED"))
				.body(containsString("bg-amber-400"))
				.body(containsString("newest fix of macOS 15, which Apple still patches"))
				.body(not(containsString("OLDER MACOS, MISSING UPDATES")))
				.body(not(containsString("USES A")));
		}
		finally
		{
			QuarkusTransaction.requiringNew()
				.run(() -> releaseRepository.delete("productVersion = ?1", "15.7.9"));
		}
	}

	@Test
	void namesTheFixAnOlderTrainIsMissing()
	{
		// given — 26.7 is the newest Tahoe, and the machine is still on 26.6.2
		seed("26.6.2", "25G83", "27.0");
		release("26.7", "25G229");

		try
		{
			// when / then
			given()
				.when().get("/Reports/show/" + reportId)
				.then()
				.statusCode(200)
				.body(containsString("OLDER MACOS, MISSING UPDATES"))
				.body(containsString("missing 26.7, the newest macOS 26"));
		}
		finally
		{
			QuarkusTransaction.requiringNew()
				.run(() -> releaseRepository.delete("productVersion = ?1", "26.7"));
		}
	}

	@Test
	void saysWhenApplesStoppedPatchingATrain()
	{
		// given — with 27 newest, macOS 14 is three trains back
		seed("14.8.1", "23J30", "27.0");

		// when / then
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString("OLDER MACOS, NO LONGER PATCHED"))
			.body(containsString("Apple no longer patches macOS 14"))
			.body(containsString("USES A 3-YEAR-OLD VERSION"));
	}

	private void release(String version, String build)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			MacOsRelease release = new MacOsRelease();
			release.setProductVersion(version);
			release.setBuild(build);
			release.setPostingDate(LocalDate.of(2026, 9, 15));
			release.setPublicRelease(true);
			release.setSeenAt(Instant.now());
			releaseRepository.persist(release);
		});
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
			.body(not(containsString("OLDER MACOS, MISSING UPDATES")));
	}

	/**
	 * A report filed while the catalog was still empty carries no yardstick of
	 * its own. Once the catalog fills, the report has to start reading against
	 * today's newest release rather than staying blank forever — which is what
	 * it did on a real report until the feed could be reached at all.
	 */
	@Test
	void fallsBackToTodaysLatestWhenTheReportWasFiledWithoutOne()
	{
		// given a report with no stamped latest, and a catalog that now has one
		seed("26.6.2", "25G83", null);
		QuarkusTransaction.requiringNew().run(() -> {
			MacOsRelease release = new MacOsRelease();
			release.setProductVersion("27.0");
			release.setBuild("26A428");
			release.setPostingDate(LocalDate.of(2026, 9, 15));
			release.setPublicRelease(true);
			release.setSeenAt(Instant.now());
			releaseRepository.persist(release);
		});

		try
		{
			// when / then
			given()
				.when().get("/Reports/show/" + reportId)
				.then()
				.statusCode(200)
				.body(containsString("OLDER MACOS, MISSING UPDATES"))
				.body(containsString("USES A 1-YEAR-OLD VERSION"))
				.body(not(containsString("no release data")));
		}
		finally
		{
			QuarkusTransaction.requiringNew()
				.run(() -> releaseRepository.delete("productVersion = ?1", "27.0"));
		}
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
			.body(not(containsString("OLDER MACOS, MISSING UPDATES")))
			.body(not(containsString("MISSING A FIX")))
			// and says so, rather than showing a bare version that looks fine
			.body(containsString("not compared — no release data"));
	}
}
