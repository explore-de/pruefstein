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
		LibraryEntry fileVault = library.find("filevault").orElseThrow();
		LibraryEntry blockedApps = library.find("blocked-apps").orElseThrow();

		// then
		assertEquals(LibraryEntry.Type.EXPRESSION, fileVault.type());
		assertEquals("A.8 Technological controls", fileVault.group());
		assertEquals("A.8.24", fileVault.control());
		assertEquals("FileVault enabled", fileVault.name());
		assertTrue(fileVault.query().startsWith("SELECT filevault_status"));
		assertEquals("results.size() > 0", fileVault.expression());

		assertTrue(blockedApps.isGenerated());
		assertNull(blockedApps.group());
		assertEquals("A.8.19", blockedApps.control());
		assertNull(blockedApps.query());
	}

	@Test
	void everyEntryNamesAControlAndSitsInAnAnnexATheme()
	{
		// then — a check with no control would show a blank cell to an auditor,
		// and a misspelt theme would file it in a group nobody else uses
		List<String> themes = List.of("A.5 Organizational controls", "A.6 People controls",
			"A.7 Physical controls", "A.8 Technological controls");
		for (LibraryEntry entry : library.entries())
		{
			assertNotNull(entry.control(), entry.key() + " has no control");
			assertTrue(entry.control().matches("A\\.[5-8]\\.\\d+"), entry.key() + ": " + entry.control());
			if (entry.group() != null)
			{
				assertTrue(themes.contains(entry.group()), entry.key() + " is filed under " + entry.group());
				String theme = entry.group().substring(0, entry.group().indexOf(' '));
				assertTrue(entry.control().startsWith(theme + "."),
					entry.key() + ": control " + entry.control() + " is not in theme " + entry.group());
			}
		}
	}

	/**
	 * The entries that read a setting a management profile can enforce. The
	 * SoftwareUpdate entries are not here: they read the local file alone, as
	 * they did when they were written, and a profile is expected to have set
	 * that file's value as well.
	 */
	private static final List<String> PLIST_BACKED = List.of("screen-lock-timeout", "auto-login", "guest-account");

	@Test
	void theEntriesThatReadAFileConsultTheManagedProfile()
	{
		// then — a setting enforced centrally overrides the local file, and a
		// check reading only the local one contradicts the fleet's own policy
		for (String key : PLIST_BACKED)
		{
			String query = library.find(key).orElseThrow().query();
			assertTrue(query.contains("FROM plist"), key + ": " + query);
			assertTrue(query.contains("/Library/Managed Preferences/"), key + " ignores managed preferences: " + query);
		}
	}

	@Test
	void noEntryReadsThePreferencesTable()
	{
		// then — it returns nothing to an agent that is not root, so a check
		// on it reports on a machine it never measured
		for (LibraryEntry entry : library.entries())
		{
			if (entry.query() != null)
			{
				assertFalse(entry.query().contains("FROM preferences"), entry.key() + ": " + entry.query());
			}
		}
	}

	@Test
	void entriesKeepTheIndexOrder()
	{
		// when
		List<String> keys = library.entries().stream().map(LibraryEntry::key).toList();

		// then
		assertEquals("filevault", keys.get(0));
	}
}
