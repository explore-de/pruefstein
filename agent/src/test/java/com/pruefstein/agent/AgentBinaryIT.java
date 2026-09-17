package com.pruefstein.agent;

import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the packaged agent — the native binary under {@code -Dnative}, the jar
 * otherwise — as somebody would run it from a terminal.
 * <p>
 * Aimed at what a JVM test run cannot see: a native image initialises static
 * fields at build time, drops reflection metadata nobody declared, and still
 * starts perfectly. Every release so far that was broken on a real machine was
 * broken in one of those ways.
 * <p>
 * The binary's home is {@code target/it-home}, set through
 * {@code quarkus.test.arg-line} in the pom.
 */
@QuarkusMainIntegrationTest
class AgentBinaryIT
{
	/** Nothing listens on port 1, so a connection there is refused at once. */
	private static final String UNREACHABLE_SERVER = "http://127.0.0.1:1";

	private static final Path HOME = Path.of(System.getProperty("pruefstein.it.home", "target/it-home"));

	private static final Path CREDENTIALS = HOME.resolve(".config/pruefstein/credentials.json");

	@AfterEach
	void forgetTheLogin() throws Exception
	{
		Files.deleteIfExists(CREDENTIALS);
	}

	@Test
	@Launch("--version")
	void namesItsVersion(LaunchResult result)
	{
		assertTrue(result.getOutput().startsWith("pruefstein-agent "), result.getOutput());
		assertFalse(result.getOutput().contains("unknown"), result.getOutput());
	}

	/**
	 * JEXL reaches an expression's values by reflection. A binary without the
	 * metadata for it started fine and evaluated every check to an error.
	 */
	@Test
	@Launch("selftest")
	void evaluatesChecks(LaunchResult result)
	{
		assertFalse(result.getOutput().contains("[FAIL]"), result.getOutput());
	}

	@Test
	@Launch(value = { "login", "--server", UNREACHABLE_SERVER }, exitCode = 1)
	void saysWhichServerDidNotAnswerWithoutAStackTrace(LaunchResult result)
	{
		String output = everything(result);
		assertTrue(output.contains("Could not connect to the Prüfstein server at " + UNREACHABLE_SERVER), output);
		assertFalse(output.contains("\tat "), output);
		assertFalse(output.contains("null"), output);
	}

	/**
	 * The login a binary finds has to be the one in the home of whoever runs
	 * it. Release 1.0.1 looked in the home of the CI runner that built it.
	 * <p>
	 * The stored token is expired and has no refresh token, so {@code login}
	 * goes back to the stored server — which only names this unreachable one if
	 * the file was read from here.
	 */
	@Test
	void readsTheStoredLoginFromTheHomeItRunsIn(QuarkusMainLauncher launcher) throws Exception
	{
		storeExpiredLogin();

		LaunchResult result = launcher.launch("login");

		assertEquals(1, result.exitCode());
		assertTrue(everything(result).contains(UNREACHABLE_SERVER), everything(result));
	}

	@Test
	void logoutDeletesTheLoginFromTheHomeItRunsIn(QuarkusMainLauncher launcher) throws Exception
	{
		storeExpiredLogin();

		LaunchResult result = launcher.launch("logout");

		assertEquals(0, result.exitCode(), everything(result));
		assertFalse(Files.exists(CREDENTIALS), "logout left " + CREDENTIALS + " behind");
	}

	private static void storeExpiredLogin() throws Exception
	{
		Files.createDirectories(CREDENTIALS.getParent());
		Files.writeString(CREDENTIALS, """
			{"serverUrl":"%s","issuer":"https://idp.example.com","clientId":"agent",
			 "scopes":"openid","accessToken":"tok","refreshToken":null,
			 "expiresAt":"2000-01-01T00:00:00Z"}
			""".formatted(UNREACHABLE_SERVER));
	}

	private static String everything(LaunchResult result)
	{
		return result.getOutput() + "\n" + result.getErrorOutput();
	}
}
