package com.pruefstein.agent.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pruefstein.agent.domain.AgentVersion;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The newest agent published on GitHub, so a run on an older one can say so and
 * a report can show it.
 * <p>
 * Asked here rather than by every agent: one server polling a public endpoint
 * every few hours stays far inside GitHub's unauthenticated rate limit, where a
 * fleet of Macs behind one office address would not, and the agent learns it
 * through the server it already talks to. Held in memory only — after a restart
 * nothing is called outdated until the first refresh lands.
 */
@ApplicationScoped
public class AgentReleaseCatalog
{
	@ConfigProperty(name = "pruefstein.agent.release-feed-url", defaultValue = "https://api.github.com/repos/explore-de/pruefstein/releases/latest")
	String feedUrl;

	@ConfigProperty(name = "pruefstein.agent.release-feed-timeout", defaultValue = "20s")
	Duration timeout;

	@Inject
	ObjectMapper objectMapper;

	private volatile AgentVersion latest;

	/** The newest release, or empty until a refresh has succeeded. */
	public Optional<String> latestVersion()
	{
		AgentVersion known = latest;
		return Optional.ofNullable(known).map(AgentVersion::toString);
	}

	/**
	 * Asks GitHub for the latest release. A failure leaves what is held.
	 *
	 * @return the version now held
	 */
	public String refresh() throws Exception
	{
		try (HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build())
		{
			HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
				.timeout(timeout)
				.header("Accept", "application/vnd.github+json")
				// GitHub refuses requests without one
				.header("User-Agent", "pruefstein")
				.GET()
				.build();
			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200)
			{
				throw new IllegalStateException(
					"GitHub returned HTTP " + response.statusCode() + " from " + feedUrl);
			}
			Release release = objectMapper.readValue(response.body(), Release.class);
			AgentVersion version = AgentVersion.parse(release.tagName())
				.orElseThrow(() -> new IllegalStateException("Unreadable release tag " + release.tagName()));
			latest = version;
			return version.toString();
		}
	}

	/** For tests: hold a version as if a refresh had just fetched it. */
	public void remember(String version)
	{
		latest = AgentVersion.parse(version).orElse(null);
	}

	// Only Jackson constructs this, so native-image has to be told to keep it
	@RegisterForReflection
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Release(@JsonProperty("tag_name") String tagName)
	{
	}
}
