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
 * These pin the two things the {@code preferences}-table versions of these
 * checks got wrong: passing a machine whose setting was never actually read,
 * and reading a timeout of 0 — the screen saver never starting — as being
 * comfortably under the limit. Expressions are taken from the library rather
 * than written out, so the check and its test cannot drift apart.
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

	private static String idle(int configured, String shortest, String longest)
	{
		return "[{\"configured\":\"" + configured + "\",\"shortest\":\"" + shortest
			+ "\",\"longest\":\"" + longest + "\"}]";
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

	// ── Screen lock timeout
	// ───────────────────────────────────────────────

	@Test
	void aTimeoutInsideThePolicyPasses() throws Exception
	{
		assertTrue(evaluate("screen-lock-timeout", idle(1, "300", "300")));
		assertTrue(evaluate("screen-lock-timeout", idle(2, "60", "300")));
	}

	@Test
	void aTimeoutOverThePolicyFails() throws Exception
	{
		assertFalse(evaluate("screen-lock-timeout", idle(1, "600", "600")));
	}

	@Test
	void oneAccountOverThePolicyFailsTheMachine() throws Exception
	{
		// The check is about the endpoint, not about whichever account happens
		// to be tidiest
		assertFalse(evaluate("screen-lock-timeout", idle(2, "60", "600")));
	}

	@Test
	void aScreenSaverThatNeverStartsFails() throws Exception
	{
		// An idleTime of 0 means never, which the old `value <= 300` passed
		assertFalse(evaluate("screen-lock-timeout", idle(1, "0", "0")));
		assertFalse(evaluate("screen-lock-timeout", idle(2, "0", "300")));
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

	@Test
	void noTimeoutConfiguredAnywhereFails() throws Exception
	{
		// count(*) over no rows still yields a row, with nulls in the
		// aggregates — the expression has to short-circuit before touching them
		assertFalse(evaluator.evaluate(
			"[{\"configured\":\"0\",\"shortest\":null,\"longest\":null}]",
			expression("screen-lock-timeout")));
	}
}
