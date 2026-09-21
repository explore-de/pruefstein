package com.pruefstein.homebrew.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Where each Homebrew formula and cask lives on the web, so the report can link
 * an installed package to its project rather than leave a bare name.
 * <p>
 * Homebrew publishes its whole catalogue as two documents, and those are
 * downloaded in full rather than asked about package by package: a report page
 * must not wait on formulae.brew.sh, and asking for exactly the packages on a
 * device would tell Homebrew what that device has installed. Only the name and
 * homepage of each entry are kept. The catalogue is public and rebuilt on every
 * refresh, so it lives in memory — after a restart, links fall back to the
 * package's page on formulae.brew.sh until the first refresh lands.
 */
@ApplicationScoped
public class HomebrewCatalog
{
	@ConfigProperty(name = "pruefstein.homebrew.api-url", defaultValue = "https://formulae.brew.sh/api")
	String apiUrl;

	@ConfigProperty(name = "pruefstein.homebrew.feed-timeout", defaultValue = "60s")
	Duration timeout;

	@Inject
	ObjectMapper objectMapper;

	private volatile Homepages homepages;

	/**
	 * Where to send someone who wants to know what an installed package is.
	 *
	 * @param source
	 *            {@code brew:formula} or {@code brew:cask}; anything else has
	 *            no link
	 * @param name
	 *            the formula name or cask token
	 * @return the project's homepage when Homebrew lists one, the package's
	 *         page on formulae.brew.sh while the catalogue has not loaded yet,
	 *         and empty for a package Homebrew does not know — a third-party
	 *         tap's, say — since formulae.brew.sh has no page for it either
	 */
	public Optional<String> linkFor(String source, String name)
	{
		String type = typeOf(source);
		if (type == null || name == null || name.isBlank())
		{
			return Optional.empty();
		}
		Homepages loaded = homepages;
		if (loaded == null)
		{
			return Optional.of("https://formulae.brew.sh/" + type + "/" + name);
		}
		Map<String, String> byName = "cask".equals(type) ? loaded.casks() : loaded.formulae();
		return Optional.ofNullable(byName.get(name));
	}

	/**
	 * Downloads both lists and replaces what is held. Either both succeed or
	 * nothing changes, so a failed refresh keeps the last good copy.
	 *
	 * @return how many packages the catalogue now knows
	 */
	public int refresh() throws Exception
	{
		try (HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build())
		{
			Map<String, String> formulae = fetch(client, "formula", FormulaEntry.class,
				e -> e.name(), e -> e.homepage());
			Map<String, String> casks = fetch(client, "cask", CaskEntry.class,
				e -> e.token(), e -> e.homepage());
			homepages = new Homepages(formulae, casks);
			return formulae.size() + casks.size();
		}
	}

	/** For tests: load the lists as if a refresh had just downloaded them. */
	void load(InputStream formulae, InputStream casks) throws IOException
	{
		homepages = new Homepages(
			parse(formulae, FormulaEntry.class, e -> e.name(), e -> e.homepage()),
			parse(casks, CaskEntry.class, e -> e.token(), e -> e.homepage()));
	}

	private <T> Map<String, String> fetch(HttpClient client, String type, Class<T> entryType,
		Function<T, String> key, Function<T, String> homepage) throws Exception
	{
		String url = apiUrl + "/" + type + ".json";
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(timeout)
			.header("Accept", "application/json")
			// About a sixth of the size compressed, and the JDK client does
			// not ask for it on its own.
			.header("Accept-Encoding", "gzip")
			.GET()
			.build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream body = response.body())
		{
			if (response.statusCode() != 200)
			{
				throw new IllegalStateException("Homebrew API returned HTTP " + response.statusCode() + " from " + url);
			}
			boolean gzipped = response.headers().firstValue("Content-Encoding")
				.map("gzip"::equalsIgnoreCase)
				.orElse(false);
			return parse(gzipped ? new GZIPInputStream(body) : body, entryType, key, homepage);
		}
	}

	/**
	 * Reads the list one entry at a time: the formula list is tens of megabytes
	 * uncompressed, and all but two fields of each entry are thrown away.
	 */
	private <T> Map<String, String> parse(InputStream json, Class<T> entryType,
		Function<T, String> key, Function<T, String> homepage) throws IOException
	{
		Map<String, String> byName = new HashMap<>();
		try (MappingIterator<T> entries = objectMapper.readerFor(entryType).readValues(json))
		{
			while (entries.hasNext())
			{
				T entry = entries.next();
				String name = key.apply(entry);
				String url = homepage.apply(entry);
				if (name != null && isWebLink(url))
				{
					byName.put(name, url);
				}
			}
		}
		return Map.copyOf(byName);
	}

	/**
	 * The homepage ends up in an {@code href}, and it is written by whoever
	 * wrote the formula — so nothing but an ordinary web address gets through.
	 */
	private static boolean isWebLink(String url)
	{
		return url != null && (url.startsWith("https://") || url.startsWith("http://"));
	}

	private static String typeOf(String source)
	{
		return switch (source == null ? "" : source)
		{
			case "brew:formula" -> "formula";
			case "brew:cask" -> "cask";
			default -> null;
		};
	}

	private record Homepages(Map<String, String> formulae, Map<String, String> casks)
	{
	}

	// Only Jackson constructs these, so native-image has to be told to keep
	// them — see ApplePmvFeed for how that fails otherwise.
	@RegisterForReflection
	@JsonIgnoreProperties(ignoreUnknown = true)
	record FormulaEntry(String name, String homepage)
	{
	}

	/**
	 * A cask's {@code name} is a list of display names; its key is the token.
	 */
	@RegisterForReflection
	@JsonIgnoreProperties(ignoreUnknown = true)
	record CaskEntry(String token, String homepage)
	{
	}
}
