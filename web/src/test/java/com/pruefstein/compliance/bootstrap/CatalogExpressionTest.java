package com.pruefstein.compliance.bootstrap;

import com.pruefstein.compliance.library.ComplianceLibrary;
import com.pruefstein.compliance.service.ComplianceEvaluator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The seeded expressions, evaluated against osquery output the way the agent
 * would.
 *
 * <p>
 * These pin what the {@code preferences}-table versions of these checks got
 * wrong: passing a machine whose setting was never actually read. Expressions
 * are taken from the library rather than written out, so the check and its test
 * cannot drift apart.
 */
@QuarkusTest
class CatalogExpressionTest
{
	@Inject
	ComplianceEvaluator evaluator;

	@Inject
	ComplianceLibrary library;

	private static String loginWindow(int filePresent, int keysRead, int switchedOn)
	{
		return "[{\"file_present\":\"" + filePresent + "\",\"keys_read\":\"" + keysRead
			+ "\",\"switched_on\":\"" + switchedOn + "\"}]";
	}

	private boolean evaluate(String checkKey, String json) throws Exception
	{
		return evaluator.evaluate(json, expression(checkKey));
	}

	private String expression(String checkKey)
	{
		return library.find(checkKey).orElseThrow().expression();
	}

	// ── Automatic login / guest account
	// ───────────────────────────────────────

	@Test
	void aSettingThatIsOffOnAFileWeReadPasses() throws Exception
	{
		assertTrue(evaluate("auto-login", loginWindow(1, 9, 0)));
		assertTrue(evaluate("guest-account", loginWindow(1, 9, 0)));
	}

	@Test
	void aSettingThatIsOnFails() throws Exception
	{
		assertFalse(evaluate("auto-login", loginWindow(1, 9, 1)));
		assertFalse(evaluate("guest-account", loginWindow(1, 9, 1)));
	}

	@Test
	void aFileThatExistsButCouldNotBeReadFails() throws Exception
	{
		// The whole point of the rewrite: this used to be indistinguishable
		// from a setting that was switched off, and both were called compliant
		assertFalse(evaluate("auto-login", loginWindow(1, 0, 0)));
		assertFalse(evaluate("guest-account", loginWindow(1, 0, 0)));
	}

	@Test
	void aMissingFilePasses() throws Exception
	{
		// Nothing to configure automatic login or a guest account with
		assertTrue(evaluate("auto-login", loginWindow(0, 0, 0)));
		assertTrue(evaluate("guest-account", loginWindow(0, 0, 0)));
	}

	// ── Firewall logging
	// ─────────────────────────────────────────────────

	@Test
	void firewallLoggingPassesOnMacOsFifteenAndLaterWhateverTheTableSays() throws Exception
	{
		// macOS 15 removed the setting and turned logging on for good, and the
		// alf table still reports 0 because the file it reads is gone
		assertTrue(evaluate("firewall-logging", "[{\"major\":\"26\",\"logging_enabled\":\"0\"}]"));
		assertTrue(evaluate("firewall-logging", "[{\"major\":\"15\",\"logging_enabled\":\"0\"}]"));
	}

	@Test
	void firewallLoggingStillReadsTheSettingOnOlderMacOs() throws Exception
	{
		// below that line the setting was real, so it is still what decides
		assertTrue(evaluate("firewall-logging", "[{\"major\":\"14\",\"logging_enabled\":\"1\"}]"));
		assertFalse(evaluate("firewall-logging", "[{\"major\":\"14\",\"logging_enabled\":\"0\"}]"));
		// 10.x compared numerically, not as text
		assertFalse(evaluate("firewall-logging", "[{\"major\":\"10\",\"logging_enabled\":\"0\"}]"));
	}

	// ── Browsers
	// ─────────────────────────────────────────────────────────

	@Test
	void anyOtherBrowserInstalledFails() throws Exception
	{
		assertTrue(evaluate("unmanaged-browsers", "[]"));
		assertFalse(evaluate("unmanaged-browsers",
			"[{\"name\":\"Firefox.app\",\"bundle_identifier\":\"org.mozilla.firefox\",\"path\":\"/Applications/Firefox.app\"}]"));
	}

}
