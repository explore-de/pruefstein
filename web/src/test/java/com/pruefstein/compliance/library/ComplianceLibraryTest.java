package com.pruefstein.compliance.library;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The library is loaded from an index because the native image cannot list a
 * directory. The one way that goes wrong is a file dropped in without being
 * indexed — it would silently never ship — so that is checked here, where the
 * directory can still be listed.
 */
@QuarkusTest
class ComplianceLibraryTest
{
	@Inject
	ComplianceLibrary library;

	@Test
	void everyFileInTheDirectoryIsIndexedAndLoaded() throws IOException, URISyntaxException
	{
		// given
		Path directory = Path.of(Thread.currentThread().getContextClassLoader()
			.getResource(ComplianceLibrary.DIRECTORY).toURI());
		Set<String> files;
		try (Stream<Path> listing = Files.list(directory))
		{
			files = new HashSet<>(listing
				.map(path -> path.getFileName().toString())
				.filter(name -> name.endsWith(".json") && !name.equals("index.json"))
				.map(name -> name.substring(0, name.length() - ".json".length()))
				.toList());
		}

		// when
		Set<String> loaded = new HashSet<>(library.entries().stream().map(LibraryEntry::key).toList());

		// then
		assertEquals(files, loaded, "compliance-library/index.json must list exactly the files beside it");
		assertEquals(library.entries().size(), loaded.size(), "a key must not be indexed twice");
	}

	@Test
	void theBaselineIsLoadedWithItsContent()
	{
		// when
		LibraryEntry fileVault = library.find("a10.filevault").orElseThrow();
		LibraryEntry blockedApps = library.find("a12.blocked-apps").orElseThrow();

		// then
		assertEquals(LibraryEntry.Type.EXPRESSION, fileVault.type());
		assertEquals("A.10 Cryptography", fileVault.group());
		assertEquals("FileVault enabled", fileVault.name());
		assertTrue(fileVault.query().startsWith("SELECT filevault_status"));
		assertEquals("results.size() > 0", fileVault.expression());

		assertTrue(blockedApps.isGenerated());
		assertNull(blockedApps.group());
		assertNull(blockedApps.query());
	}

	@Test
	void entriesKeepTheIndexOrder()
	{
		// when
		List<String> keys = library.entries().stream().map(LibraryEntry::key).toList();

		// then
		assertEquals("a10.filevault", keys.get(0));
	}
}
