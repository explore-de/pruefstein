package com.pruefstein.mcp;

import java.net.URI;
import java.util.Map;

import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalogue over MCP answers to the same roles as the compliance screens:
 * everyone reads, only an admin adds.
 */
@QuarkusTest
class ComplianceToolsTest
{
	@TestHTTPResource
	URI baseUri;

	@Inject
	ComplianceGroupRepository groupRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	private Long groupId;
	private McpStreamableTestClient client;

	@BeforeEach
	void setUp()
	{
		Long[] id = new Long[1];
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceGroup group = new ComplianceGroup();
			group.setName("MCP Test Group");
			groupRepository.persist(group);
			id[0] = group.id;

			ExpressionCheck item = new ExpressionCheck();
			item.setName("MCP Test Item");
			item.setQuery("SELECT 1;");
			item.setExpectedExpression("results.size() > 0");
			item.setGroup(group);
			itemRepository.persist(item);
		});
		groupId = id[0];
		McpAssured.baseUri = baseUri;
		client = McpAssured.newConnectedStreamableClient();
	}

	@AfterEach
	void tearDown()
	{
		client.disconnect();
		QuarkusTransaction.requiringNew().run(() -> {
			itemRepository.delete("group.id", groupId);
			groupRepository.deleteById(groupId);
		});
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	void anyoneCanReadTheChecksOfAGroup()
	{
		// given (group and check seeded in setUp)

		// when / then
		client.when()
			.toolsCall("listComplianceItems", Map.of("groupId", groupId), response -> {
				assertFalse(response.isError());
				JsonObject check = JsonObject.mapFrom(response.structuredContent())
					.getJsonArray("checks").getJsonObject(0);
				assertEquals("MCP Test Item", check.getString("name"));
				assertEquals("SELECT 1;", check.getString("query"));
				assertEquals("results.size() > 0", check.getString("expectedExpression"));
			})
			.thenAssertResults();
	}

	/**
	 * Claude Code throws away a result without a content array, however good
	 * its structured content is — so the JSON has to come as text too.
	 */
	@Test
	@TestSecurity(user = "alice", roles = {})
	void structuredResultsAlsoCarryTheirJsonAsText()
	{
		// given (group and check seeded in setUp)

		// when / then
		client.when()
			.toolsCall("listComplianceItems", Map.of("groupId", groupId), response -> {
				assertFalse(response.content().isEmpty());
				assertTrue(response.firstContent().asText().text().contains("MCP Test Item"));
			})
			.thenAssertResults();
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	void nonAdminCannotAddACheck()
	{
		// given (regular user without admin role)

		// when / then
		client.when()
			.toolsCall("createComplianceItem")
			.withArguments(Map.of("groupId", groupId, "name", "Forbidden", "query", "SELECT 1;",
				"expectedExpression", "true"))
			.withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.SECURITY_ERROR, error.code()))
			.send()
			.thenAssertResults();
		assertEquals(0, QuarkusTransaction.requiringNew().call(() -> itemRepository.count("name", "Forbidden")));
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void adminCanAddACheck()
	{
		// given (admin user)

		// when
		client.when()
			.toolsCall("createComplianceItem", Map.of("groupId", groupId, "name", "Added over MCP",
				"query", "SELECT 2;", "expectedExpression", "true"), response -> assertFalse(response.isError()))
			.thenAssertResults();

		// then
		assertTrue(QuarkusTransaction.requiringNew().call(() -> itemRepository.count("name", "Added over MCP")) == 1);
	}
}
