package com.pruefstein.mcp;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reports over MCP follow the report screens: a user reads their own, an admin
 * reads everyone's.
 */
@QuarkusTest
class ReportToolsTest
{
	@TestHTTPResource
	URI baseUri;

	@Inject
	ReportRepository reportRepository;

	private Long aliceReportId;
	private Long bobReportId;
	private McpStreamableTestClient client;

	@BeforeEach
	void setUp()
	{
		aliceReportId = seed("alice");
		bobReportId = seed("bob");
		McpAssured.baseUri = baseUri;
		client = McpAssured.newConnectedStreamableClient();
	}

	@AfterEach
	void tearDown()
	{
		client.disconnect();
		QuarkusTransaction.requiringNew().run(() -> {
			reportRepository.deleteById(aliceReportId);
			reportRepository.deleteById(bobReportId);
		});
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "alice") })
	void nonAdminListsOnlyOwnReports()
	{
		// given (alice's report and bob's report seeded in setUp)

		// when / then
		client.when()
			.toolsCall("listReports", Map.of("search", "mcp-test-"),
				response -> assertEquals(List.of("mcp-test-alice"), deviceIds(response.structuredContent())))
			.thenAssertResults();
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "alice") })
	void nonAdminCannotReadSomeoneElsesReport()
	{
		// given (bob's report seeded in setUp)

		// when / then
		client.when()
			.toolsCall("getReport", Map.of("id", bobReportId), response -> assertTrue(response.isError()))
			.thenAssertResults();
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "alice") })
	void nonAdminCanReadOwnReport()
	{
		// given (alice's report seeded in setUp)

		// when / then
		client.when()
			.toolsCall("getReport", Map.of("id", aliceReportId), response -> {
				assertFalse(response.isError());
				JsonObject report = JsonObject.mapFrom(response.structuredContent()).getJsonObject("report");
				assertEquals("mcp-test-alice", report.getString("deviceId"));
				assertEquals("COMPLIANT", report.getString("status"));
			})
			.thenAssertResults();
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "admin") })
	void adminListsEveryonesReports()
	{
		// given (alice's report and bob's report seeded in setUp)

		// when / then
		client.when()
			.toolsCall("listReports", Map.of("search", "mcp-test-"),
				response -> assertEquals(List.of("mcp-test-alice", "mcp-test-bob"),
					deviceIds(response.structuredContent()).stream().sorted().toList()))
			.thenAssertResults();
	}

	private Long seed(String user)
	{
		return QuarkusTransaction.requiringNew().call(() -> {
			Report report = new Report();
			report.setDeviceId("mcp-test-" + user);
			report.setUserId(user);
			report.setKeycloakUser(user);
			report.setCheckedAt(Instant.now());
			report.setStatus(ReportStatus.COMPLIANT);
			reportRepository.persist(report);
			return report.id;
		});
	}

	private static List<String> deviceIds(Object structuredContent)
	{
		return JsonObject.mapFrom(structuredContent).getJsonArray("reports").stream()
			.map(report -> ((JsonObject)report).getString("deviceId"))
			.toList();
	}
}
