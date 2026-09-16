package com.pruefstein.agent.runner;

import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CheckSelfTestTest
{
	@Inject
	CheckSelfTest selfTest;

	@Test
	void passesOnABuildThatCanEvaluateChecks()
	{
		// given a JVM run, where JEXL's reflection always works

		// when
		List<CheckSelfTest.Outcome> outcomes = selfTest.run();

		// then
		assertEquals(CheckSelfTest.CASES.size(), outcomes.size());
		assertTrue(outcomes.stream().allMatch(CheckSelfTest.Outcome::passed),
			() -> "failed: " + outcomes.stream().filter(o -> !o.passed()).map(CheckSelfTest.Outcome::describe).toList());
	}

	/**
	 * The failure this exists to catch is a lookup returning null rather than
	 * throwing, which is what made the original bug so quiet: an expression
	 * shaped {@code results.size() == 0} decided false and reported a clean
	 * machine as failing. A wrong verdict has to fail the self-test as loudly
	 * as an exception does.
	 */
	@Test
	void treatsAWrongVerdictAsAFailureAndNotOnlyAnError()
	{
		// given an outcome that evaluated cleanly to the wrong answer
		CheckSelfTest.Case scenario = CheckSelfTest.CASES.getFirst();
		CheckSelfTest.Outcome wrong = new CheckSelfTest.Outcome(scenario, !scenario.expected(), null);

		// when / then
		assertFalse(wrong.passed());
		assertTrue(wrong.describe().contains("expected"));
	}

	@Test
	void treatsAnExpressionItCannotEvaluateAsAFailure()
	{
		// given — what the native binary did before the metadata was restored
		CheckSelfTest.Case scenario = CheckSelfTest.CASES.getFirst();
		CheckSelfTest.Outcome broken = new CheckSelfTest.Outcome(scenario, null,
			"JEXL error : > error caused by null operand");

		// when / then
		assertFalse(broken.passed());
		assertTrue(broken.describe().contains("could not evaluate"));
	}

	@Test
	void coversBothVerdictsSoAnAlwaysTrueEvaluatorCannotPass()
	{
		// given the case list

		// when / then — a build that answered true to everything would still
		// be broken, so the cases have to include an expected false
		assertTrue(CheckSelfTest.CASES.stream().anyMatch(CheckSelfTest.Case::expected));
		assertTrue(CheckSelfTest.CASES.stream().anyMatch(c -> !c.expected()));
	}
}
