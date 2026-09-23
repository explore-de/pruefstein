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
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "admin") })
class ReportsGroupingTest
{
	private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

	@Inject
	ReportRepository reportRepository;

	private final List<Long> seeded = new ArrayList<>();

	@BeforeEach
	void setUp()
	{
		// grouping-user has three runs, grouping-loner one — so the page has
		// both a folded row and a plain one on it at the same time
		seed("grouping-device-a", "grouping-user", NOW.minusSeconds(7200), ReportStatus.COMPLIANT);
		seed("grouping-device-b", "grouping-user", NOW.minusSeconds(3600), ReportStatus.NON_COMPLIANT);
		seed("grouping-device-c", "grouping-user", NOW, ReportStatus.COMPLIANT);
		seed("grouping-device-d", "grouping-loner", NOW.minusSeconds(60), ReportStatus.COMPLIANT);
	}

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> seeded.forEach(reportRepository::deleteById));
		seeded.clear();
	}

	private void seed(String deviceId, String user, Instant checkedAt, ReportStatus status)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = new Report();
			report.setDeviceId(deviceId);
			report.setUserId(user + "@example.com");
			report.setKeycloakUser(user);
			report.setCheckedAt(checkedAt);
			report.setStatus(status);
			reportRepository.persist(report);
			seeded.add(report.id);
		});
	}

	@Test
	void repeatRunsOfOneUserAreOfferedBehindAToggle()
	{
		// given — three runs from grouping-user

		// when / then — the count names what is folded away, so the reader
		// knows there is something to open before opening it
		given()
			.when().get("/Reports/index?q=grouping-")
			.then()
			.statusCode(200)
			.body(containsString("2 OLDER"));
	}

	@Test
	void aUserWithASingleRunGetsNoToggle()
	{
		// given — grouping-loner ran once

		// when / then — a toggle with nothing behind it is a dead control
		given()
			.when().get("/Reports/index?q=grouping-loner")
			.then()
			.statusCode(200)
			.body(containsString("grouping-device-d"))
			.body(not(containsString("OLDER")));
	}

	@Test
	void theOlderRunsAreStillOnThePageJustHidden()
	{
		// given — three runs from grouping-user

		// when / then — folding them away is a rendering decision, not a
		// filter: opening the toggle must not need another request
		given()
			.when().get("/Reports/index?q=grouping-")
			.then()
			.statusCode(200)
			.body(containsString("grouping-device-c"))
			.body(containsString("grouping-device-b"))
			.body(containsString("grouping-device-a"));
	}

	@Test
	void theRowLeftOnScreenIsTheLatestRun()
	{
		// given — the runs were seeded oldest-first, so the newest is not
		// simply the first row the repository hands over under every sort

		// when — ordered oldest-first, which puts the latest run last
		String html = given()
			.when().get("/Reports/index?q=grouping-&sort=checkedAt&dir=asc")
			.then()
			.statusCode(200)
			.extract().asString();

		// then — the visible row is still grouping-device-c, the latest run,
		// and the two older ones sit inside the collapsed block
		int visible = html.indexOf("grouping-device-c");
		int hidden = html.indexOf("data-older-run");
		assertTrue(visible > 0, "the latest run is on the page");
		assertTrue(hidden > visible,
			"the collapsed older runs follow the row that stays on screen");
	}

	@Test
	void theScopeBoxOpensTicked()
	{
		// given — a plain page, with nothing said about scope

		// when / then — the control has to show the default it is actually
		// running under, or the table is narrower than it looks
		given()
			.when().get("/Reports/index")
			.then()
			.statusCode(200)
			.body(containsString("LATEST ONLY"))
			.body(containsString("aria-checked=\"true\""));
		given()
			.when().get("/Reports/index?all=1")
			.then()
			.statusCode(200)
			.body(containsString("aria-checked=\"false\""));
	}

	@Test
	void filteringNarrowsWhatIsGroupedWhenEveryRunCounts()
	{
		// given — only one of grouping-user's three runs is NON_COMPLIANT

		// when / then — with the box off the group is built from what the
		// filter left, so a single surviving run has nothing to fold away
		given()
			.when().get("/Reports/index?q=grouping-&status=NON_COMPLIANT&all=1")
			.then()
			.statusCode(200)
			.body(containsString("grouping-device-b"))
			.body(not(containsString("OLDER")));
	}

	@Test
	void aStatusFilterReadsTheLatestRunByDefault()
	{
		// given — grouping-user's middle run failed, but the one after it
		// passed: the machine was put right

		// when / then — so they are not on the non-compliant list, and the
		// failing run is not dragged onto it either
		given()
			.when().get("/Reports/index?q=grouping-&status=NON_COMPLIANT")
			.then()
			.statusCode(200)
			.body(not(containsString("grouping-device-b")))
			.body(containsString("No reports match"));
	}

	@Test
	void theEarlierRunsStayInTheFoldOfAGroupThatMatches()
	{
		// given — grouping-user's latest run is COMPLIANT, the two before it
		// are one COMPLIANT and one NON_COMPLIANT

		// when — filtered to the status the latest run has
		given()
			.when().get("/Reports/index?q=grouping-&status=COMPLIANT")
			.then()
			.statusCode(200)
			// then — the group survives whole, history and all: the filter
			// picks which users are listed, not which of their runs exist
			.body(containsString("grouping-device-c"))
			.body(containsString("2 OLDER"))
			.body(containsString("grouping-device-b"));
	}
}
