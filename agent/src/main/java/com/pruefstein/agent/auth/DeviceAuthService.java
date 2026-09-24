package com.pruefstein.agent.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DeviceAuthService
{
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
	{
	};

	private static final String CLIENT_ID_PARAMETER = "client_id=";

	private static final String ACCESS_TOKEN = "access_token";

	@Inject
	ObjectMapper objectMapper;

	@Inject
	OidcDiscovery discovery;

	private final HttpClient http = HttpClient.newHttpClient();

	public Credentials login(String serverUrl, AgentServerConfig config)
		throws IOException, InterruptedException
	{
		OidcEndpoints endpoints = discovery.discover(config.issuer());

		HttpResponse<String> response = post(endpoints.deviceAuthorizationEndpoint(),
			CLIENT_ID_PARAMETER + encode(config.clientId()) + scopeParameter(config));
		Map<String, Object> device = objectMapper.readValue(response.body(), MAP_TYPE);

		if (!device.containsKey("device_code"))
		{
			throw new IllegalStateException("Device authorization failed: " + describeError(device));
		}

		String userCode = (String) device.get("user_code");
		String verificationUri = (String) device.get("verification_uri_complete");
		if (verificationUri == null)
		{
			verificationUri = (String) device.get("verification_uri");
		}
		String deviceCode = (String) device.get("device_code");
		int interval = ((Number) device.getOrDefault("interval", 5)).intValue();

		System.out.println();
		System.out.println("  Please open this URL to log in:");
		System.out.println("  " + verificationUri);
		System.out.println("  Code: " + userCode);
		System.out.println();
		System.out.print("  Waiting for authentication");

		Map<String, Object> token = pollForToken(endpoints.tokenEndpoint(), config, deviceCode, interval);
		return toCredentials(serverUrl, config, token);
	}

	private Map<String, Object> pollForToken(String tokenEndpoint, AgentServerConfig config,
		String deviceCode, int intervalSeconds) throws IOException, InterruptedException
	{
		String body = CLIENT_ID_PARAMETER + encode(config.clientId())
			+ "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
			+ "&device_code=" + encode(deviceCode);

		while (true)
		{
			Thread.sleep(intervalSeconds * 1000L);
			System.out.print(".");

			HttpResponse<String> response = post(tokenEndpoint, body);
			Map<String, Object> result = objectMapper.readValue(response.body(), MAP_TYPE);

			if (result.containsKey(ACCESS_TOKEN))
			{
				System.out.println(" done.");
				return result;
			}

			String error = (String) result.get("error");
			if (!"authorization_pending".equals(error) && !"slow_down".equals(error))
			{
				throw new IllegalStateException("Authentication failed: " + describeError(result));
			}
			if ("slow_down".equals(error))
			{
				intervalSeconds += 5;
			}
		}
	}

	public Credentials refresh(Credentials current) throws IOException, InterruptedException
	{
		AgentServerConfig config = current.serverConfig();
		OidcEndpoints endpoints = discovery.discover(config.issuer());

		String body = CLIENT_ID_PARAMETER + encode(config.clientId())
			+ "&grant_type=refresh_token"
			+ "&refresh_token=" + encode(current.refreshToken())
			+ scopeParameter(config);

		HttpResponse<String> response = post(endpoints.tokenEndpoint(), body);
		Map<String, Object> result = objectMapper.readValue(response.body(), MAP_TYPE);

		if (!result.containsKey(ACCESS_TOKEN))
		{
			throw new IllegalStateException("Token refresh failed: " + describeError(result));
		}
		return toCredentials(current.serverUrl(), config, result);
	}

	private HttpResponse<String> post(String endpoint, String body) throws IOException, InterruptedException
	{
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(endpoint))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();

		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * Keycloak defaults the scope when none is sent; Entra rejects the request
	 * outright (AADSTS900144). The value decides both the token's audience and
	 * whether a refresh token comes back at all, so the server dictates it.
	 */
	private static String scopeParameter(AgentServerConfig config)
	{
		String scopes = config.scopes();
		return scopes == null || scopes.isBlank() ? "" : "&scope=" + encode(scopes);
	}

	private static Credentials toCredentials(String serverUrl, AgentServerConfig config,
		Map<String, Object> tokenResponse)
	{
		String accessToken = (String) tokenResponse.get(ACCESS_TOKEN);
		String refreshToken = (String) tokenResponse.get("refresh_token");
		int expiresIn = ((Number) tokenResponse.getOrDefault("expires_in", 300)).intValue();
		Instant expiresAt = Instant.now().plusSeconds(expiresIn);

		return new Credentials(serverUrl, config.issuer(), config.clientId(), config.scopes(),
			accessToken, refreshToken, expiresAt);
	}

	/**
	 * Entra puts the actionable part in {@code error_description} — the bare
	 * {@code error} code is usually {@code invalid_request}.
	 */
	private static String describeError(Map<String, Object> response)
	{
		Object description = response.get("error_description");
		return String.valueOf(description != null ? description : response.get("error"));
	}

	private static String encode(String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
