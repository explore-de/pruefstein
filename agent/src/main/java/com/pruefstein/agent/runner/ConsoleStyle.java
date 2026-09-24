package com.pruefstein.agent.runner;

import picocli.CommandLine.Help.Ansi;

/**
 * The colours the run output is painted in.
 * <p>
 * Everything goes through picocli's {@link Ansi#AUTO}, which is already on the
 * classpath and already knows when not to colour: no terminal, {@code TERM=dumb},
 * {@code NO_COLOR} set, output piped into a file or a cron mail. Escape codes
 * leaking into a redirected log would be worse than no colour at all, and this
 * is the one place that decision is made.
 * <p>
 * The markup is evaluated per call rather than cached in a constant on purpose:
 * a native-image build would otherwise freeze the answer from build time, when
 * there is no terminal at all, and the binary would never colour anything.
 */
public final class ConsoleStyle
{
	/** Wide enough to underline the summary, narrow enough for any terminal. */
	private static final int RULE_WIDTH = 44;

	private ConsoleStyle()
	{
	}

	/** Green {@code [PASS]} or red {@code [FAIL]} — the one thing being scanned for. */
	public static String verdict(boolean passed)
	{
		return style(passed ? "@|bold,green [PASS]|@" : "@|bold,red [FAIL]|@");
	}

	/** A check that never produced a verdict — red, like a failure, because it is one. */
	public static String errorTag()
	{
		return style("@|bold,red [ERROR]|@");
	}

	/**
	 * The line someone actually reads: bold, and green when the whole run
	 * passed. A run with failures is left in the terminal's own foreground
	 * colour rather than painted — the red already sits on the {@code [FAIL]}
	 * lines above, where it names which check went wrong, and repeating it on
	 * the total says nothing new. Deliberately not an explicit black either:
	 * that would vanish on a dark background.
	 */
	public static String summary(long passed, long total)
	{
		String text = "Done: " + passed + "/" + total + " checks passed";
		return style(passed == total ? "@|bold,green " + text + "|@" : "@|bold " + text + "|@");
	}

	/**
	 * What a run costs if it is left as it is — red, unlike the summary above
	 * it. The summary is left unpainted because the {@code [FAIL]} lines have
	 * already said which checks are red; this line says something the run
	 * itself does not, that a deadline is counting down and the report goes on
	 * record as non-compliant at the end of it. It is the last line printed
	 * and the one worth losing nothing to.
	 */
	public static String notice(String text)
	{
		return style("@|bold,red " + text + "|@");
	}

	/** Separates the summary from the per-check lines above it. */
	public static String rule()
	{
		return style("@|faint " + "─".repeat(RULE_WIDTH) + "|@");
	}

	private static String style(String markup)
	{
		return Ansi.AUTO.string(markup);
	}
}
