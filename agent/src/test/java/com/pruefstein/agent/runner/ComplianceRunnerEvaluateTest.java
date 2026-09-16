package com.pruefstein.agent.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The expression shapes the shipped catalog actually uses, applied to the JSON
 * osquery actually returns.
 * <p>
 * These run on the JVM, where JEXL's reflection always works. They pin what an
 * expression <em>means</em>; they cannot tell you the native binary evaluates
 * it — that failed for months while tests like these passed. See
 * {@link JexlNativeSupport}.
 */
class ComplianceRunnerEvaluateTest
{
	private final ComplianceRunner runner = new ComplianceRunner();

	ComplianceRunnerEvaluateTest()
	{
		runner.objectMapper = new ObjectMapper();
	}

	private boolean evaluate(String json, String expression)
	{
		try
		{
			return runner.evaluate(json, expression);
		}
		catch (Exception e)
		{
			throw new AssertionError("expression should have evaluated: " + expression, e);
		}
	}

	@Test
	void countsTheRowsARowMustExistFor()
	{
		// given — "FileVault enabled" asks only whether a row came back
		String expression = "results.size() > 0";

		// when / then
		assertTrue(evaluate("[{\"filevault_status\":\"on\"}]", expression));
		assertFalse(evaluate("[]", expression));
	}

	@Test
	void readsAColumnOffTheFirstRow()
	{
		// given — the shape almost every check in the catalog uses
		String expression = "results.size() > 0 && results[0].global_state == '1'";

		// when / then
		assertTrue(evaluate("[{\"global_state\":\"1\"}]", expression));
		assertFalse(evaluate("[{\"global_state\":\"0\"}]", expression));
	}

	@Test
	void guardsTheRowAccessSoAnEmptyResultIsFalseAndNotAnError()
	{
		// given — the guard is why an off machine reads as a failure rather
		// than as a broken check
		String expression = "results.size() > 0 && results[0].global_state == '1'";

		// when / then
		assertFalse(evaluate("[]", expression));
	}

	@Test
	void treatsAnAbsentSettingAsTheSafeDefault()
	{
		// given — "Guest account disabled": no preference set means disabled
		String expression = "results.size() == 0 || results[0].value == '0'";

		// when / then
		assertTrue(evaluate("[]", expression));
		assertTrue(evaluate("[{\"value\":\"0\"}]", expression));
		assertFalse(evaluate("[{\"value\":\"1\"}]", expression));
	}

	@Test
	void comparesNumbersOsqueryHandsBackAsText()
	{
		// given — "Screen lock timeout ≤ 300 seconds"
		String expression = "results.size() > 0 && results[0].value <= 300";

		// when / then
		assertTrue(evaluate("[{\"value\":\"120\"}]", expression));
		assertFalse(evaluate("[{\"value\":\"600\"}]", expression));
	}

	@Test
	void readsSeveralColumnsOffOneRow()
	{
		// given — "Screen lock requires a password", which asks two things
		String expression = "results.size() > 0 && results[0].enabled == '1' && results[0].grace_period <= 300";

		// when / then
		assertTrue(evaluate("[{\"enabled\":\"1\",\"grace_period\":\"0\"}]", expression));
		assertFalse(evaluate("[{\"enabled\":\"1\",\"grace_period\":\"900\"}]", expression));
		assertFalse(evaluate("[{\"enabled\":\"0\",\"grace_period\":\"0\"}]", expression));
	}

	@Test
	void refusesAnExpressionThatDoesNotDecideAnything()
	{
		// given — an expression returning a value rather than a verdict

		// when / then — better a loud error than a check that silently passes
		assertThrows(IllegalArgumentException.class,
			() -> runner.evaluate("[{\"value\":\"1\"}]", "results[0].value"));
	}
}
