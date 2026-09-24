package com.pruefstein.agent.runner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class OsqueryRequirementTest
{
	@Test
	void findsTheBinaryOnThePath(@TempDir Path empty, @TempDir Path binaries) throws Exception
	{
		Path binary = Files.createFile(binaries.resolve("osqueryi"));
		assertTrue(binary.toFile().setExecutable(true), "test needs an executable bit to set");

		String path = empty + File.pathSeparator + binaries;

		assertEquals(Optional.of(binary), OsqueryRequirement.locate("osqueryi", path));
	}

	/**
	 * A directory is executable and a downloaded-but-unset file is not, and
	 * {@link ProcessBuilder} would run neither.
	 */
	@Test
	void ignoresWhatItCannotRun(@TempDir Path directories, @TempDir Path unreadable) throws Exception
	{
		Files.createDirectory(directories.resolve("osqueryi"));
		Path notExecutable = Files.createFile(unreadable.resolve("osqueryi"));
		assertTrue(notExecutable.toFile().setExecutable(false), "test needs the executable bit cleared");

		String path = directories + File.pathSeparator + unreadable;

		assertEquals(Optional.empty(), OsqueryRequirement.locate("osqueryi", path));
	}

	@Test
	void survivesAnAbsentPath()
	{
		assertEquals(Optional.empty(), OsqueryRequirement.locate("osqueryi", null));
		assertEquals(Optional.empty(), OsqueryRequirement.locate("osqueryi", ""));
	}

}
