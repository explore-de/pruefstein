package com.pruefstein.mcp;

import java.net.URI;
import java.util.Map;

import com.pruefstein.notification.ReportRequestMailService;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Users over MCP are admin-only, like the users screen, and adding one invites
 * them the same way.
 */
@QuarkusTest
class UserToolsTest
{
	private static final String MAIL = "mcp-user@example.com";

	@TestHTTPResource
	URI baseUri;

	@Inject
	UserRepository userRepository;

	@InjectMock
	ReportRequestMailService.Sender sender;

	private McpStreamableTestClient client;

	@BeforeEach
	void setUp()
	{
		McpAssured.baseUri = baseUri;
		client = McpAssured.newConnectedStreamableClient();
	}

	@AfterEach
	void tearDown()
	{
		client.disconnect();
		QuarkusTransaction.requiringNew().run(() -> userRepository.delete("mail", MAIL));
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	void nonAdminCannotListUsers()
	{
		// given (regular user without admin role)

		// when / then
		client.when()
			.toolsCall("listUsers")
			.withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.SECURITY_ERROR, error.code()))
			.send()
			.thenAssertResults();
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	void nonAdminCannotAddAUser()
	{
		// given (regular user without admin role)

		// when / then
		client.when()
			.toolsCall("addUser")
			.withArguments(Map.of("firstname", "New", "lastname", "Hire", "mail", MAIL))
			.withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.SECURITY_ERROR, error.code()))
			.send()
			.thenAssertResults();
		assertEquals(0, QuarkusTransaction.requiringNew().call(() -> userRepository.count("mail", MAIL)));
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void adminAddsAUserAndTheyAreInvited()
	{
		// given (admin user)

		// when
		client.when()
			.toolsCall("addUser", Map.of("firstname", "New", "lastname", "Hire", "mail", MAIL),
				response -> assertFalse(response.isError()))
			.thenAssertResults();

		// then
		assertEquals(1, QuarkusTransaction.requiringNew().call(() -> userRepository.count("mail", MAIL)));
		Mockito.verify(sender).send(Mockito.eq(MAIL), Mockito.anyString(), Mockito.any());
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void addingAUserWithAnInvalidAddressIsRefused()
	{
		// given (admin user)

		// when / then
		client.when()
			.toolsCall("addUser", Map.of("firstname", "New", "lastname", "Hire", "mail", "not-an-address"),
				response -> assertTrue(response.isError()))
			.thenAssertResults();
		Mockito.verifyNoInteractions(sender);
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void adminRequestsAReportFromSomeoneWithoutADevice()
	{
		// given someone who has never reported
		Long userId = QuarkusTransaction.requiringNew().call(() -> {
			AppUser user = new AppUser();
			user.setFirstname("No");
			user.setLastname("Device");
			user.setMail(MAIL);
			userRepository.persist(user);
			return user.id;
		});

		// when
		client.when()
			.toolsCall("requestReport", Map.of("userId", userId), response -> {
				assertFalse(response.isError());
				assertTrue(JsonObject.mapFrom(response.structuredContent()).getString("message").contains("setup invite"));
			})
			.thenAssertResults();

		// then they get the setup mail, since there is nothing to re-check
		Mockito.verify(sender).send(Mockito.eq(MAIL), Mockito.anyString(), Mockito.any());
	}
}
