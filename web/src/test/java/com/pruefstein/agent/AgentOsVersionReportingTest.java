package com.pruefstein.agent;

import java.time.Instant;
import java.time.LocalDate;

import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.osversion.domain.MacOsRelease;
import com.pruefstein.osversion.repository.MacOsReleaseRepository;
import com.pruefstein.report.domain.Report;
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
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What the server keeps when an agent says which macOS it was running.
 */
@QuarkusTest
@TestSecurity(user = "os-reporter", roles = "user")
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "os-reporter") })
class AgentOsVersionReportingTest
{
	private static final String DEVICE = "os-reporting-device";

	@Inject
	ComplianceGroupRepository groupRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	ReportRepository reportRepository;

	@Inject
	MacOsReleaseRepository releaseRepository;

	private Long groupId;
	private Long itemId;

	@BeforeEach
	void setUp()
	{
		Long[] ids = new Long[2];
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceGroup group = new ComplianceGroup();
			group.setName("OS reporting group");
			groupRepository.persist(group);
			ids[0] = group.id;

			ExpressionCheck check = new ExpressionCheck();
			check.setName("OS reporting check");
			check.setQuery("SELECT 1;");
			check.setExpectedExpression("true");
			check.setGroup(group);
			itemRepository.persist(check);
			ids[1] = check.id;

			MacOsRelease latest = new MacOsRelease();
			latest.setProductVersion("27.0");
			latest.setBuild("26A428");
			latest.setPostingDate(LocalDate.of(2026, 9, 15));
			latest.setPublicRelease(true);
			latest.setSeenAt(Instant.now());
			releaseRepository.persist(latest);
		});
		groupId = ids[0];
		itemId = ids[1];
	}

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			resultRepository.delete("item.id = ?1", itemId);
			reportRepository.delete("deviceId = ?1", DEVICE);
			itemRepository.deleteById(itemId);
			groupRepository.deleteById(groupId);
			releaseRepository.delete("productVersion = ?1", "27.0");
		});
	}

	@Test
	void recordsWhatTheDeviceWasRunning()
	{
		// given / when
		Report report = push("""
			,"osVersion":{"name":"macOS","version":"15.7.9","build":"24G830","platform":"darwin"}""");

		// then
		assertEquals("macOS", report.getOsName());
		assertEquals("15.7.9", report.getOsVersion());
		assertEquals("24G830", report.getOsBuild());
	}

	@Test
	void stampsTheNewestReleaseThatExistedAtTheTime()
	{
		// given a catalog whose newest public release is 27.0

		// when
		Report report = push("""
			,"osVersion":{"name":"macOS","version":"15.7.9","build":"24G830","platform":"darwin"}""");

		// then the report carries its own yardstick, so it keeps its verdict
		// however long it sits there
		assertEquals("27.0", report.getOsLatestVersion());
	}

	@Test
	void acceptsAnAgentTooOldToKnowAboutAnyOfThis()
	{
		// given / when — the field the older agent never sends
		Report report = push("");

		// then the report is filed, simply without an OS on it
		assertNull(report.getOsVersion());
		assertNull(report.getOsLatestVersion());
	}

	private Report push(String osFragment)
	{
		String body = """
			{"deviceId":"%s","userId":"os-reporting-user","checkedAt":"%s",
			 "results":[{"itemId":%d,"passed":true,"output":"ok"}]%s}
			""".formatted(DEVICE, Instant.now(), itemId, osFragment);

		String url = given()
			.contentType(JSON)
			.body(body)
			.when().post("/api/reports")
			.then()
			.statusCode(200)
			.extract().path("reportUrl");

		return QuarkusTransaction.requiringNew().call(() -> reportRepository.findById(
			Long.valueOf(url.substring(url.lastIndexOf('/') + 1))));
	}
}
