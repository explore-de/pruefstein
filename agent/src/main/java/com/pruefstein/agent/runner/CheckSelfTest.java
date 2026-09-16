package com.pruefstein.agent.runner;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Proves this binary can evaluate a check, without a server to fetch one from.
 * <p>
 * The agent shipped for months unable to evaluate anything: JEXL reaches every
 * value in an expression by reflection, native-image dropped the metadata, and
 * every check came back an error — or, worse, silently false. Every test passed
 * throughout, because a JVM run cannot observe missing native metadata, and CI
 * asked the packaged binary only for {@code --help}.
 * <p>
 * This closes that. The cases are the expression shapes the shipped catalog
 * actually uses, applied to the JSON osquery actually returns, so a build that
 * cannot do the one thing the agent exists for says so — in CI, before the
 * archive is published, rather than on somebody's laptop.
 */
@ApplicationScoped
public class CheckSelfTest
{
	/**
	 * @param name
	 *            what the case demonstrates, for the line it prints
	 * @param output
	 *            osquery JSON, as a check would receive it
	 * @param expression
	 *            the JEXL a catalog check would carry
	 * @param expected
	 *            the verdict the expression has to reach
	 */
	public record Case(String name, String output, String expression, boolean expected)
	{
	}

	/**
	 * @param verdict
	 *            what the expression actually decided, or {@code null} if it
	 *            could not be evaluated at all
	 * @param error
	 *            why it could not, or {@code null}
	 */
	public record Outcome(Case scenario, Boolean verdict, String error)
	{
		public boolean passed()
		{
			return error == null && verdict != null && verdict == scenario.expected();
		}

		public String describe()
		{
			if (error != null)
			{
				return "could not evaluate: " + error;
			}
			return "expected " + scenario.expected() + ", got " + verdict;
		}
	}

	/**
	 * Deliberately the shapes that broke, not a broader sample: a row count, a
	 * column read off the first row, the empty-result guard that turns a
	 * missing setting into a verdict, and a numeric comparison against text.
	 * Between them they exercise every reflective call JEXL makes here.
	 */
	static final List<Case> CASES = List.of(
		new Case("a row came back",
			"[{\"filevault_status\":\"on\"}]", "results.size() > 0", true),
		new Case("no rows came back",
			"[]", "results.size() > 0", false),
		new Case("a column on the first row",
			"[{\"global_state\":\"1\"}]", "results.size() > 0 && results[0].global_state == '1'", true),
		new Case("a column that says otherwise",
			"[{\"global_state\":\"0\"}]", "results.size() > 0 && results[0].global_state == '1'", false),
		new Case("an absent setting is the safe default",
			"[]", "results.size() == 0 || results[0].value == '0'", true),
		new Case("a number osquery handed back as text",
			"[{\"value\":\"120\"}]", "results.size() > 0 && results[0].value <= 300", true));

	@Inject
	ComplianceRunner runner;

	public List<Outcome> run()
	{
		List<Outcome> outcomes = new ArrayList<>(CASES.size());
		for (Case scenario : CASES)
		{
			try
			{
				outcomes.add(new Outcome(scenario, runner.evaluate(scenario.output(), scenario.expression()), null));
			}
			catch (Exception e)
			{
				outcomes.add(new Outcome(scenario, null, e.getMessage()));
			}
		}
		return outcomes;
	}
}
