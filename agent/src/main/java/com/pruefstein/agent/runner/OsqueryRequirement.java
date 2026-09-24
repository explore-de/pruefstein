package com.pruefstein.agent.runner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Makes sure {@code osqueryi} is on the {@code PATH} before a run starts, and
 * stops the run when it is not.
 * <p>
 * Every check and the whole inventory shell out to {@code osqueryi}
 * ({@link ComplianceRunner}), so a run without it pushes a report in which
 * every single check errored — which reads like a badly misconfigured machine
 * rather than a missing tool. Installing it is the Homebrew formula's job,
 * which depends on the cask; the agent only says what is missing.
 */
@ApplicationScoped
public class OsqueryRequirement
{
	private static final String BINARY = "osqueryi";
	private static final String INSTALL = "brew install --cask osquery";

	/**
	 * @return {@code true} when {@code osqueryi} can be run. {@code false}
	 *         means abort, and the reason has been printed.
	 */
	public boolean ensureAvailable()
	{
		if (locate(BINARY, System.getenv("PATH")).isPresent())
		{
			return true;
		}
		System.out.println(BINARY + " is not on the PATH. Install it with: " + INSTALL);
		return false;
	}

	/**
	 * The same lookup {@link ProcessBuilder} will do, done up front so the
	 * agent can say what is missing instead of failing on every check with an
	 * {@link IOException}.
	 */
	static Optional<Path> locate(String binary, String pathEnv)
	{
		if (pathEnv == null || pathEnv.isBlank())
		{
			return Optional.empty();
		}
		return Arrays.stream(pathEnv.split(File.pathSeparator))
			.filter(directory -> !directory.isBlank())
			.map(directory -> Path.of(directory).resolve(binary))
			.filter(Files::isRegularFile)
			.filter(Files::isExecutable)
			.findFirst();
	}
}
