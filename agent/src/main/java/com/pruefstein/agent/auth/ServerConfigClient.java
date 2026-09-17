package com.pruefstein.agent.auth;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Fetches the IdP bootstrap from the Prüfstein server the agent reports to.
 * <p>
 * Deliberately plain {@link HttpClient} rather than the REST client: this runs
 * before the agent is authenticated and before the REST client's base URL is
 * known, since {@code pruefstein-agent login --server} may be naming a server for the
 * first time.
 */
@ApplicationScoped
public class ServerConfigClient
{
	@Inject
	ObjectMapper objectMapper;

	private final HttpClient http = HttpClient.newHttpClient();

	public AgentServerConfig fetch(String serverUrl) throws IOException, InterruptedException
	{
		String url = trimTrailingSlash(serverUrl) + "/internal/agent-config";

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.GET()
			.build();

		HttpResponse<String> response;
		try
		{
			response = http.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (ConnectException e)
		{
			// Thrown without a message of its own, so the URL is the only
			// thing that can make it say anything.
			ConnectException described = new ConnectException(
				"Could not connect to the Prüfstein server at " + serverUrl + ".");
			described.initCause(e);
			throw described;
		}
		if (response.statusCode() != 200)
		{
			throw new IllegalStateException(
				"Could not read the agent configuration from " + url + " (HTTP " + response.statusCode()
					+ "). Is " + serverUrl + " a Prüfstein server?");
		}

		AgentServerConfig config = objectMapper.readValue(response.body(), AgentServerConfig.class);
		if (config.issuer() == null || config.issuer().isBlank())
		{
			throw new IllegalStateException(
				"The server at " + serverUrl + " reports no identity provider, so there is nothing to log in to.");
		}
		return config;
	}

	private static String trimTrailingSlash(String value)
	{
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
