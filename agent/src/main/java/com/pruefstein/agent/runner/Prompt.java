package com.pruefstein.agent.runner;

import java.io.IOError;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one place the agent asks a yes/no question.
 * <p>
 * Reads the answer with {@link IO#readln(String)}, which reads
 * {@code System.in} rather than {@link System#console()} — a native-image build
 * does not reliably hand out a console — through one reader shared by the whole
 * process, so a run that asks twice does not lose the second answer to a buffer
 * that read ahead. A redirected or closed stdin — {@code run} from cron — then
 * reaches EOF and answers {@link Answer#NONE} instead of blocking on a prompt
 * nobody can see. That is a different thing from someone typing "no", and both
 * callers say something different about it, so the two are kept apart.
 */
public final class Prompt
{
	private static final Logger LOG = LoggerFactory.getLogger(Prompt.class);

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
		try
		{
			return interpret(IO.readln(question + " "));
		}
		catch (IOError e)
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
}
