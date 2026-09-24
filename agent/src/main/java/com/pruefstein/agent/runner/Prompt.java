package com.pruefstein.agent.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one place the agent asks a yes/no question.
 * <p>
 * Reads the answer off {@code System.in} rather than {@link System#console()},
 * which a native-image build does not reliably hand out. A redirected or closed
 * stdin — {@code run} from cron — then reaches EOF and answers
 * {@link Answer#NONE} instead of blocking on a prompt nobody can see. That is a
 * different thing from someone typing "no", and both callers say something
 * different about it, so the two are kept apart.
 */
public final class Prompt
{
	private static final Logger LOG = LoggerFactory.getLogger(Prompt.class);

	/**
	 * One reader for the process. A second {@link BufferedReader} over
	 * {@code System.in} would start with an empty buffer of its own and lose
	 * whatever the first one had already read ahead, which on a run that asks
	 * twice would swallow the second answer.
	 */
	private static BufferedReader stdin;

	/**
	 * Anything short of an explicit yes is a {@link Answer#NO}, a bare Enter
	 * included. The question the agent asks leads somewhere that is not easily
	 * taken back — a report filed against someone's machine — so it does not
	 * get the benefit of the doubt.
	 */
	public enum Answer
	{
		YES, NO, NONE
	}

	private Prompt()
	{
	}

	/**
	 * @param question
	 *            asked as written, so it carries its own {@code [y/n]}
	 */
	public static Answer ask(String question)
	{
		System.out.print(question + " ");
		System.out.flush();
		try
		{
			return interpret(reader().readLine());
		}
		catch (IOException e)
		{
			LOG.warn("Could not read the answer from stdin.", e);
			return Answer.NONE;
		}
	}

	static Answer interpret(String answer)
	{
		if (answer == null)
		{
			return Answer.NONE;
		}
		String normalized = answer.strip().toLowerCase(Locale.ROOT);
		return normalized.equals("y") || normalized.equals("yes") ? Answer.YES : Answer.NO;
	}

	private static synchronized BufferedReader reader()
	{
		if (stdin == null)
		{
			stdin = new BufferedReader(new InputStreamReader(System.in));
		}
		return stdin;
	}
}
