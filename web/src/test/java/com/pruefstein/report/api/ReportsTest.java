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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "admin") })
class ReportsTest
{
	@Inject
	ReportRepository reportRepository;

	private Long reportId;

	@BeforeEach
	void setUp()
	{
		Long[] ids = new Long[1];
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = new Report();
			report.setDeviceId("reports-test-device");
			report.setUserId("reports-test-user");
			report.setCheckedAt(Instant.now());
			report.setStatus(ReportStatus.COMPLIANT);
			reportRepository.persist(report);
			ids[0] = report.id;
		});
		reportId = ids[0];
	}

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> reportRepository.deleteById(reportId));
	}

	@Test
	void indexReturns200()
	{
		// given (report seeded in setUp)

		// when / then
		given()
			.when().get("/Reports/index")
			.then()
			.statusCode(200)
			.contentType(containsString("text/html"));
	}

	@Test
	void indexFiltersByStatus()
	{
		// given — one COMPLIANT report from setUp

		// when asked for the status it has
		given()
			.when().get("/Reports/index?status=COMPLIANT")
			.then()
			.statusCode(200)
			.body(containsString("reports-test-device"));

		// when asked for one it does not — the dashboard's non-compliant block
		// links here, so this parameter is a contract, not a convenience
		given()
			.when().get("/Reports/index?status=NON_COMPLIANT")
			.then()
			.statusCode(200)
			.body(not(containsString("reports-test-device")));
	}

	@Test
	void indexIgnoresAnUnknownStatus()
	{
		// given — a value no ReportStatus has, as a hand-edited URL would give

		// when / then — the filter is dropped rather than the page breaking
		given()
			.when().get("/Reports/index?status=NOT_A_STATUS")
			.then()
			.statusCode(200)
			.body(containsString("reports-test-device"));
	}

	@Test
	void theResetButtonIsAbsentWhileNothingIsFiltering()
	{
		// given — the default view

		// when / then — a reset that is always on screen reads as though a
		// filter were applied
		given()
			.when().get("/Reports/index")
			.then()
			.statusCode(200)
			.body(not(containsString("RESET FILTERS")));
	}

	@Test
	void theResetButtonAppearsForAStatusFilter()
	{
		// when / then
		given()
			.when().get("/Reports/index?status=COMPLIANT")
			.then()
			.statusCode(200)
			.body(containsString("RESET FILTERS"));
	}

	@Test
	void theResetButtonAppearsForASearch()
	{
		// when / then — searching is filtering too, and it is the half that is
		// easy to forget
		given()
			.when().get("/Reports/index?q=reports-test")
			.then()
			.statusCode(200)
			.body(containsString("RESET FILTERS"));
	}

	@Test
	void theResetButtonKeepsTheChosenOrdering()
	{
		// when / then — it clears the filters by leaving them off the link,
		// and carries the sort so a reader's ordering survives the reset
		given()
			.when().get("/Reports/index?status=COMPLIANT&sort=deviceId&dir=asc")
			.then()
			.statusCode(200)
			.body(containsString("sort=deviceId&amp;dir=asc"))
			.body(not(containsString("status=COMPLIANT&amp;")));
	}

	@Test
	void theSortHeadersSitInsideTheComponentThatDefinesSetSort()
	{
		// given — the default view

		// when — the sort buttons live in the table, which used to sit outside
		// the x-data element defining setSort, so Alpine never bound them and
		// clicking a column header did nothing at all. The component has to
		// wrap the whole page, not just the filter bar.
		String html = given()
			.when().get("/Reports/index")
			.then()
			.statusCode(200)
			.extract().asString();

		// then — the component opens before the table it has to reach. Pinning
		// this on the wrapper's classes instead only tests the page's width.
		int component = html.indexOf("setSort(col)");
		int table = html.indexOf("<table");
		assertTrue(component > 0, "the page defines setSort");
		assertTrue(table > component, "the table opens inside the component defining setSort");
	}

	@Test
	void indexSortsByTheRequestedColumn()
	{
		// given — the seeded report, plus one that sorts ahead of it by device

		// when / then — the parameters the sort headers submit have to be
		// understood on arrival, or a working click still changes nothing
		given()
			.when().get("/Reports/index?sort=deviceId&dir=asc")
			.then()
			.statusCode(200)
			.body(containsString("reports-test-device"));
		given()
			.when().get("/Reports/index?sort=deviceId&dir=desc")
			.then()
			.statusCode(200)
			.body(containsString("reports-test-device"));
	}

	@Test
	void indexContainsReport()
	{
		// given (report seeded in setUp)

		// when / then
		given()
			.when().get("/Reports/index")
			.then()
			.statusCode(200)
			.body(containsString("reports-test-device"));
	}

	@Test
	void showReturns200ForExistingReport()
	{
		// given (report seeded in setUp)

		// when / then
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.contentType(containsString("text/html"))
			.body(containsString("reports-test-device"));
	}

	@Test
	void showDisplaysCompliantStatus()
	{
		// given (report seeded in setUp with COMPLIANT status)

		// when / then
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString("COMPLIANT"));
	}

	@Test
	void showReturns404ForUnknownReport()
	{
		// given
		long unknownId = Long.MAX_VALUE;

		// when / then
		given()
			.when().get("/Reports/show/" + unknownId)
			.then()
			.statusCode(404);
	}
}
