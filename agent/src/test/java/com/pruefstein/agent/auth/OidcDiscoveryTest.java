package com.pruefstein.agent.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OidcDiscoveryTest
{
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void readsKeycloakEndpoints() throws Exception
	{
		String metadata = """
			{
			  "issuer": "http://localhost:8180/realms/pruefstein",
			  "token_endpoint": "http://localhost:8180/realms/pruefstein/protocol/openid-connect/token",
			  "device_authorization_endpoint": "http://localhost:8180/realms/pruefstein/protocol/openid-connect/auth/device"
			}
			""";

		OidcEndpoints endpoints = OidcDiscovery.parse("http://localhost", MAPPER.readTree(metadata));

		assertEquals("http://localhost:8180/realms/pruefstein/protocol/openid-connect/auth/device",
			endpoints.deviceAuthorizationEndpoint());
		assertEquals("http://localhost:8180/realms/pruefstein/protocol/openid-connect/token",
			endpoints.tokenEndpoint());
	}

	/**
	 * The whole point of discovery: Entra's paths share no suffix with
	 * Keycloak's, so nothing about them can be assumed from the issuer.
	 */
	@Test
	void readsEntraEndpoints() throws Exception
	{
		String metadata = """
			{
			  "issuer": "https://login.microsoftonline.com/tenant/v2.0",
			  "token_endpoint": "https://login.microsoftonline.com/tenant/oauth2/v2.0/token",
			  "device_authorization_endpoint": "https://login.microsoftonline.com/tenant/oauth2/v2.0/devicecode"
			}
			""";

		OidcEndpoints endpoints = OidcDiscovery.parse("https://login.microsoftonline.com", MAPPER.readTree(metadata));

		assertEquals("https://login.microsoftonline.com/tenant/oauth2/v2.0/devicecode",
			endpoints.deviceAuthorizationEndpoint());
		assertEquals("https://login.microsoftonline.com/tenant/oauth2/v2.0/token", endpoints.tokenEndpoint());
	}

	@Test
	void rejectsAProviderWithoutDeviceFlow() throws Exception
	{
		JsonNode metadata = MAPPER.readTree("""
			{"issuer": "https://idp.example.com", "token_endpoint": "https://idp.example.com/token"}
			""");

		IllegalStateException failure = assertThrows(IllegalStateException.class,
			() -> OidcDiscovery.parse("https://idp.example.com/.well-known/openid-configuration", metadata));

		assertTrue(failure.getMessage().contains("device_authorization_endpoint"));
	}
}
