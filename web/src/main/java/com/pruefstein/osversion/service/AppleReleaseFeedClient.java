package com.pruefstein.osversion.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Reads Apple's public asset metadata feed.
 * <p>
 * Deliberately a plain HTTP call rather than a generated REST client: this
 * talks to one unauthenticated endpoint that returns one document, and the
 * catalogue treats any failure the same way — keep what is already stored.
 * <p>
 * It needs its own trust anchor. {@code gdmf.apple.com} is served from Apple's
 * own root rather than a commercial CA, and no JDK ships that root — macOS
 * trusts it through the system keychain, which is why {@code curl} succeeds on
 * a Mac while the JVM next to it cannot complete the handshake at all. Without
 * the root bundled, this fails everywhere, production included. Trusting only
 * that root here, rather than adding it to everything, keeps the rest of the
 * application on the ordinary public CAs.
 */
@ApplicationScoped
public class AppleReleaseFeedClient
{
	@ConfigProperty(name = "pruefstein.macos.feed-url", defaultValue = "https://gdmf.apple.com/v2/pmv")
	String feedUrl;

	@ConfigProperty(name = "pruefstein.macos.feed-timeout", defaultValue = "20s")
	Duration timeout;

	@Inject
	ObjectMapper objectMapper;

	@Inject
	AppleTrust appleTrust;

	/**
	 * @return the feed as Apple published it
	 * @throws java.io.IOException
	 *             if it cannot be fetched or parsed — the caller decides what
	 *             that means, which is always "keep the last good copy"
	 */
	public ApplePmvFeed fetch() throws Exception
	{
		try (HttpClient client = HttpClient.newBuilder()
			.connectTimeout(timeout)
			.sslContext(appleTrust.sslContext())
			.build())
		{
			HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
				.timeout(timeout)
				.header("Accept", "application/json")
				.GET()
				.build();
			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200)
			{
				throw new IllegalStateException(
					"Apple release feed returned HTTP " + response.statusCode() + " from " + feedUrl);
			}
			return objectMapper.readValue(response.body(), ApplePmvFeed.class);
		}
	}

}
