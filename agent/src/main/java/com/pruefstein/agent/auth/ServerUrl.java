package com.pruefstein.agent.auth;

/**
 * The server URL as the agent uses it, whatever the user typed.
 * <p>
 * {@code --server pruefstein.example.com} is what an administrator writes, and
 * every consumer of the value needs a scheme: {@code URI.create} without one
 * yields a relative URI that {@link java.net.http.HttpClient} rejects, and
 * {@code localhost:8080} is worse than that — it parses as a URI with the
 * scheme {@code localhost}, so the failure never mentions the missing
 * {@code https://}. Normalising once, before the URL is stored, keeps a
 * schemeless value out of the credentials file and out of the REST client's
 * base URL.
 */
public final class ServerUrl
{
	private ServerUrl()
	{
	}

	/**
	 * @param value a server URL with or without a scheme, or {@code null}
	 * @return the value with {@code https://} prepended if it named no scheme;
	 *         {@code null} and blank come back untouched, since it is not this
	 *         method's business to decide what a missing server means
	 */
	public static String normalize(String value)
	{
		if (value == null || value.isBlank())
		{
			return value;
		}
		String trimmed = value.trim();
		// Only "://" counts as a scheme. A bare "host:port" has a colon too,
		// and prepending https:// is exactly what it needs.
		return trimmed.contains("://") ? trimmed : "https://" + trimmed;
	}
}
