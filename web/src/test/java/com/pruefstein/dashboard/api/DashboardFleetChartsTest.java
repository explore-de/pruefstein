package com.pruefstein.dashboard.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.pruefstein.compliance.domain.ComplianceGroup;
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
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * The two fleet charts as they land on the page. The arithmetic behind them is
 * {@link com.pruefstein.dashboard.service.FleetDashboardTest}'s job; this only
 * asks whether the admin dashboard draws them and whether anyone else can see
 * the estate through them.
 */
@QuarkusTest
class DashboardFleetChartsTest
{
	@Inject
	ReportRepository reportRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceGroupRepository groupRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	private Long reportId;

	private Long itemId;

	private Long groupId;

	@BeforeEach
	void seed()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceGroup group = new ComplianceGroup();
			group.setName("Chart Fixture Group");
			groupRepository.persist(group);
			groupId = group.id;

			ExpressionCheck check = new ExpressionCheck();
			check.setName("Chart Fixture Check");
			check.setControl("A.8.24");
			check.setGroup(group);
			check.setQuery("SELECT 1");
			check.setExpectedExpression("results.size() > 0");
			itemRepository.persist(check);
			itemId = check.id;

			Report report = new Report();
			report.setDeviceId("chart-fixture.local");
			report.setUserId("chart-user");
			report.setKeycloakUser("chart-user");
			report.setCheckedAt(Instant.now().minus(1, ChronoUnit.HOURS));
			report.setStatus(ReportStatus.NON_COMPLIANT);
			report.setOsName("macOS");
			report.setOsVersion("15.7.9");
			reportRepository.persist(report);
			reportId = report.id;

			ComplianceResult result = new ComplianceResult();
			result.setReport(report);
			result.setItem(check);
			result.setPassed(false);
			result.setOutput("[]");
			resultRepository.persist(result);
		});
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
	@TestSecurity(user = "admin", roles = { "admin" })
	void adminSeesBothCharts()
	{
		given().when().get("/").then().statusCode(200)
			.body(containsString("macOS versions"))
			.body(containsString("15.7.9"))
			.body(containsString("Most-failed checks"))
			.body(containsString("Chart Fixture Check"))
			.body(containsString("A.8.24"));
	}

	/**
	 * The legend and the wording beside each bar, because colour alone must
	 * never be what tells a reader a machine is out of date.
	 */
	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void everyBarSaysItsStandingInWords()
	{
		given().when().get("/").then().statusCode(200)
			.body(containsString("UP TO DATE"))
			.body(containsString("MISSING A FIX"))
			.body(containsString("NOT REPORTED"));
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	void plainUserSeesNeitherChart()
	{
		given().when().get("/").then().statusCode(200)
			.body(not(containsString("macOS versions")))
			.body(not(containsString("Most-failed checks")))
			.body(not(containsString("Chart Fixture Check")));
	}
}
