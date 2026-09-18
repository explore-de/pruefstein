package com.pruefstein.agent.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pruefstein.agent.auth.ServerUrl;
import com.pruefstein.agent.auth.TokenStore;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Feeds the server URL stored by {@code pruefstein-agent login --server} into the REST
 * client's configuration.
 * <p>
 * {@code @RegisterRestClient} resolves its base URL from MicroProfile Config at
 * injection time, so a value that only exists in the credentials file has to be
 * published as config rather than passed around. The ordinal places it above
 * {@code application.properties} — whose localhost default is only a
 * convenience for a dev machine — and below environment variables, so
 * {@code QUARKUS_REST_CLIENT_PRUEFSTEIN_API_URL} still wins in CI.
 */
public class CredentialsConfigSource implements ConfigSource
{
	private static final String URL_KEY = "quarkus.rest-client.pruefstein-api.url";

	/** Between application.properties (250) and environment variables (300). */
	private static final int ORDINAL = 260;

	@Override
	public Set<String> getPropertyNames()
	{
		return serverUrl() == null ? Set.of() : Set.of(URL_KEY);
	}

	@Override
	public String getValue(String propertyName)
	{
		return URL_KEY.equals(propertyName) ? serverUrl() : null;
	}

	@Override
	public Map<String, String> getProperties()
	{
		String url = serverUrl();
		return url == null ? Map.of() : Map.of(URL_KEY, url);
	}

	@Override
	public int getOrdinal()
	{
		return ORDINAL;
	}

	@Override
	public String getName()
	{
		return "pruefstein-credentials";
	}

	/**
	 * Read on every lookup rather than cached: {@code pruefstein-agent login} writes the
	 * file inside a running JVM, and a value cached from before that would
	 * point at the wrong server for the rest of the process.
	 */
	private static String serverUrl()
	{
		Path file = TokenStore.credentialsFile();
		if (!Files.exists(file))
		{
			return null;
		}
		try
		{
			JsonNode root = new ObjectMapper().readTree(file.toFile());
			JsonNode serverUrl = root.get("serverUrl");
			if (serverUrl == null || serverUrl.isNull() || serverUrl.asText().isBlank())
			{
				return null;
			}
			// Credentials written before the agent normalised the URL can
			// still be schemeless, and the REST client would fail on them.
			return ServerUrl.normalize(serverUrl.asText());
		}
		catch (Exception e)
		{
			// Config is read long before logging is configured; a broken file
			// falls back to the configured default rather than failing startup.
			return null;
		}
	}
}
