package com.pruefstein.onboarding.api;

import com.pruefstein.onboarding.McpManual;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class McpGuideTest
{
	@Inject
	McpManual mcp;

	@Test
	@TestSecurity(user = "alice", roles = {})
	void pageNamesThisServersMcpEndpoint()
	{
		given()
			.when().get("/McpGuide/index")
			.then()
			.statusCode(200)
			.body(containsString(mcp.mcpUrl()))
			.body(containsString("claude mcp add-json --scope user pruefstein"));
	}

	/**
	 * The token expires in minutes, so the command must fetch it per connection
	 * rather than bake one in.
	 */
	@Test
	@TestSecurity(user = "alice", roles = {})
	void claudeCodeCommandFetchesTheTokenFromTheAgent()
	{
		given()
			.when().get("/McpGuide/index")
			.then()
			.statusCode(200)
			.body(containsString("headersHelper"))
			.body(containsString("pruefstein-agent token --header"));
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	void nonAdminIsNotOfferedAdminExamples()
	{
		given()
			.when().get("/McpGuide/index")
			.then()
			.statusCode(200)
			.body(not(containsString("Ask them for a new report")));
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void adminIsOfferedAdminExamples()
	{
		given()
			.when().get("/McpGuide/index")
			.then()
			.statusCode(200)
			.body(containsString("Ask them for a new report"));
	}
}
