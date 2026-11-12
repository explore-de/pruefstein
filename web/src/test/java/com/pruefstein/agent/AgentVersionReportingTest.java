package com.pruefstein.agent;

import java.time.Instant;

import com.pruefstein.agent.service.AgentReleaseCatalog;
import com.pruefstein.report.domain.Report;
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
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the server keeps and tells about the agent's own version.
 */
@QuarkusTest
@TestSecurity(user = "agent-version-reporter", roles = "user")
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "agent-version-reporter") })
class AgentVersionReportingTest
{
	private static final String DEVICE = "agent-version-device";

	@Inject
	AgentReleaseCatalog catalog;

	@Inject
	ReportRepository reportRepository;

	@AfterEach
	void tearDown()
	{
		catalog.remember(null);
		QuarkusTransaction.requiringNew().run(() -> reportRepository.delete("deviceId = ?1", DEVICE));
	}

	@Test
	void tellsTheAgentTheNewestRelease()
	{
		catalog.remember("v1.0.7");

		given().when().get("/api/agent-release")
			.then().statusCode(200).body("latest", equalTo("1.0.7"));
	}

	@Test
	void saysNothingBeforeItKnows()
	{
		given().when().get("/api/agent-release")
			.then().statusCode(200).body("latest", nullValue());
	}

	@Test
	void marksAReportFromAnOutdatedAgent()
	{
		catalog.remember("1.0.7");

		Report report = push(",\"agentVersion\":\"1.0.5\"");

		assertEquals("1.0.5", report.getAgentVersion());
		assertEquals("1.0.7", report.getAgentLatestVersion());
		assertTrue(report.isAgentOutdated());
	}

	@Test
	void aCurrentAgentIsNotOutdated()
	{
		catalog.remember("1.0.7");

		assertFalse(push(",\"agentVersion\":\"1.0.7\"").isAgentOutdated());
	}

	@Test
	void acceptsAnAgentTooOldToSay()
	{
		catalog.remember("1.0.7");

		Report report = push("");

		assertNull(report.getAgentVersion());
		assertFalse(report.isAgentOutdated());
	}

	private Report push(String agentFragment)
	{
		String body = """
			{"deviceId":"%s","userId":"agent-version-user","checkedAt":"%s","results":[]%s}
			""".formatted(DEVICE, Instant.now(), agentFragment);

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
