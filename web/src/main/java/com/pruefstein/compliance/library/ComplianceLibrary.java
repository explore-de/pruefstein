package com.pruefstein.compliance.library;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The built-in library of compliance checks, shipped as JSON inside the build.
 * <p>
 * Each check is one file under {@code compliance-library/}, and
 * {@code index.json} lists their keys in the order the library shows them. The
 * index exists because production is a native image, where a classpath
 * directory cannot be listed — the binary only knows the files it is told
 * about. {@code ComplianceLibraryTest} fails the build if a file and the index
 * ever disagree.
 * <p>
 * The library is read-only and independent of the database: it is what a
 * deployment can create checks from, not what it currently enforces. Every
 * entry names the ISO/IEC 27001:2022 Annex A theme it is filed under and the
 * control it evidences.
 */
@ApplicationScoped
public class ComplianceLibrary
{
	public static final String DIRECTORY = "compliance-library/";

	@Inject
	ObjectMapper objectMapper;

	private List<LibraryEntry> entries;

	@PostConstruct
	void load()
	{
		List<LibraryEntry> loaded = new ArrayList<>();
		for (String key : read("index.json", String[].class))
		{
			loaded.add(validate(read(key + ".json", LibraryEntry.class).withKey(key)));
		}
		entries = List.copyOf(loaded);
	}

	/** Every entry, in index order. */
	public List<LibraryEntry> entries()
	{
		return entries;
	}

	public Optional<LibraryEntry> find(String key)
	{
		return entries.stream().filter(entry -> entry.key().equals(key)).findFirst();
	}

	private <T> T read(String file, Class<T> type)
	{
		String path = DIRECTORY + file;
		try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path))
		{
			if (in == null)
			{
				throw new IllegalStateException("Compliance library file " + path + " is missing");
			}
			return objectMapper.readValue(in, type);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException("Compliance library file " + path + " is not valid", e);
		}
	}

	/**
	 * A broken entry is a packaging mistake, so it fails startup rather than
	 * surfacing later as a check that cannot be evaluated.
	 */
	private static LibraryEntry validate(LibraryEntry entry)
	{
		List<String> problems = new ArrayList<>();
		if (entry.type() == null)
		{
			problems.add("type is missing");
		}
		if (isBlank(entry.name()))
		{
			problems.add("name is missing");
		}
		if (isBlank(entry.control()))
		{
			problems.add("control is missing");
		}
		if (entry.type() == LibraryEntry.Type.EXPRESSION)
		{
			if (isBlank(entry.group()))
			{
				problems.add("group is missing");
			}
			if (isBlank(entry.query()))
			{
				problems.add("query is missing");
			}
			if (isBlank(entry.expression()))
			{
				problems.add("expression is missing");
			}
		}
		if (!problems.isEmpty())
		{
			throw new IllegalStateException(
				"Compliance library entry " + entry.key() + " is invalid: " + String.join(", ", problems));
		}
		return entry;
	}

	private static boolean isBlank(String value)
	{
		return value == null || value.isBlank();
	}
}
