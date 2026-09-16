package com.pruefstein.agent.command;

import java.util.List;
import java.util.concurrent.Callable;

import com.pruefstein.agent.runner.CheckSelfTest;
import com.pruefstein.agent.runner.ConsoleStyle;
import jakarta.inject.Inject;
import picocli.CommandLine;

/**
 * Checks that this build can evaluate a compliance check at all.
 * <p>
 * Needs no server, no credentials and no osquery, which is the point: it is the
 * one thing CI can ask of a packaged binary before publishing it.
 */
@CommandLine.Command(
	name = "selftest",
	description = "Check that this build can evaluate compliance checks. Needs no server.",
	mixinStandardHelpOptions = true)
public class SelfTestCommand implements Callable<Integer>
{
	@Inject
	CheckSelfTest selfTest;

	@Override
	public Integer call()
	{
		List<CheckSelfTest.Outcome> outcomes = selfTest.run();
		long failed = outcomes.stream().filter(outcome -> !outcome.passed()).count();

		for (CheckSelfTest.Outcome outcome : outcomes)
		{
			String label = outcome.passed() ? "[PASS]" : "[FAIL]";
			System.out.printf("  %s %s%n", label, outcome.scenario().name());
			if (!outcome.passed())
			{
				System.out.printf("         %s%n", outcome.describe());
				System.out.printf("         %s%n", outcome.scenario().expression());
			}
		}

		System.out.println(ConsoleStyle.rule());
		if (failed == 0)
		{
			System.out.printf("This build evaluates checks correctly (%d/%d).%n",
				outcomes.size(), outcomes.size());
			return 0;
		}
		System.out.printf("This build cannot evaluate checks: %d of %d failed.%n", failed, outcomes.size());
		System.out.println("Every compliance check it runs would be wrong. Do not ship it.");
		return 1;
	}
}
