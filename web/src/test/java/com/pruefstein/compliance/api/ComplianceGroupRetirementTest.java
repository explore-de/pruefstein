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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deleting a group failed the same way deleting a check did: its checks name it
 * through a foreign key, and they in turn are named by every result. It is
 * retired instead, and takes its checks with it.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "admin") })
class ComplianceGroupRetirementTest
{
	private static final String GROUP_NAME = "A.12 Group retirement test";

	private static final String CHECK_NAME = "Group retirement test — SIP enabled";

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
			group.setName(GROUP_NAME);
			groupRepository.persist(group);
			ids[0] = group.id;

			ExpressionCheck check = new ExpressionCheck();
			check.setName(CHECK_NAME);
			check.setQuery("SELECT enabled FROM sip_config;");
			check.setExpectedExpression("results[0].enabled == \"1\"");
			check.setGroup(group);
			itemRepository.persist(check);
			ids[1] = check.id;

			Report report = new Report();
			report.setDeviceId("group-retirement-device");
			report.setUserId("group-retirement-user");
			report.setKeycloakUser("admin");
			report.setCheckedAt(Instant.now());
			report.setStatus(ReportStatus.OPEN);
			reportRepository.persist(report);
			ids[2] = report.id;

			ComplianceResult result = new ComplianceResult();
			result.setItem(check);
			result.setReport(report);
			result.setPassed(false);
			result.setOutput("[{\"enabled\":\"0\"}]");
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
	void deletingGroupWithReportedChecksSucceeds()
	{
		// given — a group whose check a report was already judged against

		// when the admin deletes the group
		Response redirect = given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("id", groupId)
			.redirects().follow(false)
			.when().post("/ComplianceGroups/delete");

		// then it is a redirect, not the 500 the foreign key used to cause
		assertEquals(303, redirect.getStatusCode());

		// and the group and its check are retired, with the evidence intact
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceGroup group = groupRepository.findById(groupId);
			assertNotNull(group, "the group must survive so past reports can still name it");
			assertTrue(group.isRetired());

			ComplianceItem check = itemRepository.findById(itemId);
			assertNotNull(check, "the check must survive so its results still resolve");
			assertTrue(check.isRetired(), "a group's checks are retired with it");

			assertEquals(1, resultRepository.count("item.id = ?1", itemId));
		});

		// and the admin is told what it did to the reports
		given()
			.cookie(FLASH_COOKIE, redirect.getCookie(FLASH_COOKIE))
			.when().get("/ComplianceGroups/index")
			.then()
			.statusCode(200)
			.body(containsString("and 1 check in it"))
			.body(containsString("now count them as passed"));
	}

	@Test
	void retiredGroupLeavesTheCatalogue()
	{
		// given
		retireGroup();

		// then it is off the index
		given()
			.when().get("/ComplianceGroups/index")
			.then()
			.statusCode(200)
			.body(not(containsString(GROUP_NAME)));

		// and its own page is gone with it
		given()
			.when().get("/ComplianceGroups/show/" + groupId)
			.then()
			.statusCode(404);

		QuarkusTransaction.requiringNew().run(
			() -> assertFalse(groupRepository.listActive().stream().anyMatch(g -> g.id.equals(groupId))));
	}

	@Test
	void oldReportReadsTheGroupsChecksAsPassedAndSaysWhy()
	{
		// given
		retireGroup();

		// then the report explains the green, and offers no dead link back to
		// a catalogue entry that is no longer there
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(containsString(CHECK_NAME))
			.body(containsString("RETIRED CHECK"))
			.body(containsString("WHY 1 CHECK TURNED GREEN"))
			.body(not(containsString("/ComplianceGroups/show/" + groupId)));
	}

	@Test
	void retiredGroupTakesNoNewChecksAndCannotBeRenamed()
	{
		// given
		retireGroup();

		// when / then — both refuse rather than quietly writing into it
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("groupId", groupId)
			.formParam("name", "Sneaked in")
			.formParam("query", "SELECT 1;")
			.formParam("expectedExpression", "true")
			.when().post("/ComplianceGroups/createItem")
			.then()
			.statusCode(404);

		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("id", groupId)
			.formParam("name", "Renamed")
			.when().post("/ComplianceGroups/update")
			.then()
			.statusCode(404);
	}

	@Test
	void deletingAnAlreadyRetiredGroupIsNotFound()
	{
		// given
		retireGroup();

		// when / then
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("id", groupId)
			.when().post("/ComplianceGroups/delete")
			.then()
			.statusCode(404);
	}

	private void retireGroup()
	{
		QuarkusTransaction.requiringNew().run(() -> groupRepository.findById(groupId).retire());
	}
}
