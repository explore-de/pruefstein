package com.pruefstein.compliance.api;

import java.time.Instant;

import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ComplianceResult;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deleting a check that a report had already been judged against used to fail
 * the request outright: every result names its check through a foreign key, and
 * the database refused to let the check go. It is retired instead — out of the
 * catalogue, still in the history.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "admin") })
class ComplianceItemRetirementTest
{
	private static final String CHECK_NAME = "Retirement test — screen lock";

	private static final String FLASH_COOKIE = "_renarde_flash";

	@Inject
	ComplianceGroupRepository groupRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	ReportRepository reportRepository;

	private Long groupId;
	private Long itemId;
	private Long reportId;

	@BeforeEach
	void setUp()
	{
		Long[] ids = new Long[3];
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceGroup group = new ComplianceGroup();
			group.setName("A.11 Retirement");
			groupRepository.persist(group);
			ids[0] = group.id;

			ExpressionCheck check = new ExpressionCheck();
			check.setName(CHECK_NAME);
			check.setQuery("SELECT 1 AS locked;");
			check.setExpectedExpression("results[0].locked == \"1\"");
			check.setGroup(group);
			itemRepository.persist(check);
			ids[1] = check.id;

			Report report = new Report();
			report.setDeviceId("retirement-test-device");
			report.setUserId("retirement-test-user");
			report.setKeycloakUser("admin");
			report.setCheckedAt(Instant.now());
			report.setStatus(ReportStatus.OPEN);
			reportRepository.persist(report);
			ids[2] = report.id;

			// The device failed this check when the report was filed — the
			// row that made the delete impossible.
			ComplianceResult result = new ComplianceResult();
			result.setItem(check);
			result.setReport(report);
			result.setPassed(false);
			result.setOutput("[{\"locked\":\"0\"}]");
			resultRepository.persist(result);
		});
		groupId = ids[0];
		itemId = ids[1];
		reportId = ids[2];
	}

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			resultRepository.delete("report.id = ?1", reportId);
			reportRepository.deleteById(reportId);
			itemRepository.deleteById(itemId);
			groupRepository.deleteById(groupId);
		});
	}

	@Test
	void deletingCheckWithResultsSucceedsAndKeepsTheResults()
	{
		// given — a check with one result behind it (seeded in setUp)

		// when the admin deletes it
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("id", itemId)
			.redirects().follow(false)
			.when().post("/ComplianceGroups/deleteItem")
			.then()
			.statusCode(lessThan(400));

		// then the check is retired rather than gone, and the evidence stays
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceItem check = itemRepository.findById(itemId);
			assertNotNull(check, "the check must survive so its results still resolve");
			assertTrue(check.isRetired(), "the check must be retired");
			assertNotNull(check.getRetiredAt());
			assertEquals(1, resultRepository.count("item.id = ?1", itemId),
				"the recorded result must be untouched");
		});
	}

	@Test
	void retiringConfirmsWhatItDidToPastReports()
	{
		// given — a check with one failing result behind it

		// when the admin retires it — the confirmation rides a flash cookie
		// to the group page the redirect sends them to
		Response redirect = given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("id", itemId)
			.redirects().follow(false)
			.when().post("/ComplianceGroups/deleteItem");
		assertEquals(303, redirect.getStatusCode());

		// then the group page says what just happened to the old reports
		given()
			.cookie(FLASH_COOKIE, redirect.getCookie(FLASH_COOKIE))
			.when().get("/ComplianceGroups/show/" + groupId)
			.then()
			.statusCode(200)
			.body(containsString("Past reports keep it, and now count it as passed"));
	}

	@Test
	void retiredCheckLeavesTheCatalogueAndTheAgentsWorkList()
	{
		// given
		retire();

		// then it is gone from the group screen
		given()
			.when().get("/ComplianceGroups/show/" + groupId)
			.then()
			.statusCode(200)
			.body(not(containsString(CHECK_NAME)));

		// and from what is still counted as in force
		QuarkusTransaction.requiringNew().run(() -> {
			assertFalse(itemRepository.listActive().stream().anyMatch(c -> c.id.equals(itemId)));
			assertFalse(itemRepository.listActive(groupRepository.findById(groupId)).stream()
				.anyMatch(c -> c.id.equals(itemId)));
		});
	}

	@Test
	void oldReportReadsTheRetiredCheckAsPassedAndSaysWhy()
	{
		// given — before retirement the report shows the failure as a failure
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString(CHECK_NAME));

		// when the check is retired
		retire();

		// then the row is green, and the page explains the green
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString(CHECK_NAME))
			.body(containsString("RETIRED CHECK"))
			.body(containsString("WHY 1 CHECK TURNED GREEN"))
			.body(containsString("no longer enforced, so it counts as passed"))
			// the original answer is still on the page, not rewritten
			// (Qute escapes the quotes of the recorded JSON)
			.body(containsString("&quot;locked&quot;:&quot;0&quot;"));
	}

	@Test
	void retiredCheckNoLongerCountsAsAFailure()
	{
		// given
		retire();

		// then the result knows it is not held against the device any more,
		// while still recording what the device actually said
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceResult result = resultRepository.find("item.id = ?1", itemId).firstResult();
			assertFalse(result.isPassed(), "the recorded answer stays a failure");
			assertTrue(result.isCheckRetired());
			assertFalse(result.isFailing(), "but it no longer counts as one");
		});
	}

	@Test
	void retiringTwiceKeepsTheFirstDate()
	{
		// given
		retire();
		Instant first = QuarkusTransaction.requiringNew()
			.call(() -> itemRepository.findById(itemId).getRetiredAt());

		// when retired again
		QuarkusTransaction.requiringNew().run(() -> itemRepository.findById(itemId).retire());

		// then the date the reports were judged against is the one that stands
		Instant second = QuarkusTransaction.requiringNew()
			.call(() -> itemRepository.findById(itemId).getRetiredAt());
		assertEquals(first, second);
	}

	private void retire()
	{
		QuarkusTransaction.requiringNew().run(() -> itemRepository.findById(itemId).retire());
	}
}
