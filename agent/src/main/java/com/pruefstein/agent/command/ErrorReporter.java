package com.pruefstein.agent.command;

import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

/**
 * Turns a command that failed into a few lines someone can act on, instead of
 * forty lines of stack trace whose only useful word is buried in the middle.
 * <p>
 * The stack trace is not thrown away, only moved behind the switch that is
 * already there for when the agent needs looking into: with
 * {@code PRUEFSTEIN_AGENT_LOG_LEVEL=DEBUG} it is printed after the summary.
 */
public class ErrorReporter implements CommandLine.IExecutionExceptionHandler
{
	/** Deep enough for any real wrapping, shallow enough to end a loop. */
	private static final int MAX_CAUSE_DEPTH = 20;

	@Override
	public int handleExecutionException(Exception failure, CommandLine commandLine,
		CommandLine.ParseResult parseResult)
	{
		PrintWriter err = commandLine.getErr();
		err.println(describe(failure, Ansi.AUTO));
		if (debugEnabled())
		{
			failure.printStackTrace(err);
		}
		else
		{
			err.println(Ansi.AUTO.string(
				"@|faint For the full stack trace: PRUEFSTEIN_AGENT_LOG_LEVEL=DEBUG pruefstein-agent "
					+ String.join(" ", parseResult.originalArgs()) + "|@"));
		}
		err.flush();
		return commandLine.getCommandSpec().exitCodeOnExecutionException();
	}

	/**
	 * The message of each exception in the chain, once each: wrappers tend to
	 * repeat the message of what they wrap, and an exception such as
	 * {@link ConnectException} often has no message at all — which is how
	 * {@code Authentication failed: null} came about.
	 */
	static String describe(Throwable failure, Ansi ansi)
	{
		List<String> messages = new ArrayList<>();
		String hint = null;
		Throwable current = failure;
		for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++)
		{
			String message = current.getMessage();
			if (message != null && !message.isBlank() && !"null".equals(message)
				&& messages.stream().noneMatch(seen -> seen.contains(message)))
			{
				messages.add(message);
			}
			if (hint == null)
			{
				hint = hintFor(current);
			}
			current = current.getCause();
		}
		if (messages.isEmpty())
		{
			messages.add(failure.getClass().getSimpleName());
		}

		StringBuilder out = new StringBuilder(ansi.string("@|bold,red Error:|@ " + messages.getFirst()));
		for (String message : messages.subList(1, messages.size()))
		{
			out.append(System.lineSeparator()).append("  ").append(message);
		}
		if (hint != null)
		{
			out.append(System.lineSeparator()).append(ansi.string("@|yellow " + hint + "|@"));
		}
		return out.toString();
	}

	/** What the low-level network failures, which say nothing themselves, usually mean. */
	private static String hintFor(Throwable failure)
	{
		if (failure instanceof ConnectException || failure instanceof HttpConnectTimeoutException)
		{
			return "Nothing answered. Check that the server is running and reachable, "
				+ "or point the agent at another one with 'pruefstein-agent login --server URL'.";
		}
		if (failure instanceof UnknownHostException)
		{
			return "The host name does not resolve. Check the server URL, "
				+ "or point the agent at another one with 'pruefstein-agent login --server URL'.";
		}
		return null;
	}

	private static boolean debugEnabled()
	{
		String level = ConfigProvider.getConfig()
			.getOptionalValue("pruefstein.agent.log-level", String.class)
			.orElse("WARN");
		return level.equalsIgnoreCase("DEBUG") || level.equalsIgnoreCase("TRACE") || level.equalsIgnoreCase("ALL");
	}
}
