package com.pruefstein.agent.command;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import com.pruefstein.agent.client.ReportPayload;
import com.pruefstein.agent.runner.ComplianceRunner;
import com.pruefstein.agent.runner.Prompt.Answer;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class RunCommandTest
{
	/**
	 * The REST client wraps the status in one exception and the CDI proxy wraps
	 * that again, so the 401 is never the exception the command catches.
	 */
	@Test
	void recognisesA401AnywhereInTheCauseChain()
	{
		WebApplicationException unauthorized = new WebApplicationException(
			Response.status(Response.Status.UNAUTHORIZED).build());

		assertTrue(RunCommand.isUnauthorized(unauthorized));
		assertTrue(RunCommand.isUnauthorized(new RuntimeException("wrapped", unauthorized)));
		assertTrue(RunCommand.isUnauthorized(
			new IllegalStateException("outer", new RuntimeException("inner", unauthorized))));
	}

	/**
	 * Only a 401 means the credentials are the problem. Re-authenticating over
	 * a 403 or a 500 would send someone to a browser for nothing, and would
	 * hide the real failure behind a login prompt.
	 */
	@Test
	void leavesEveryOtherFailureAlone()
	{
		assertFalse(RunCommand.isUnauthorized(new WebApplicationException(
			Response.status(Response.Status.FORBIDDEN).build())));
		assertFalse(RunCommand.isUnauthorized(new WebApplicationException(
			Response.status(Response.Status.INTERNAL_SERVER_ERROR).build())));
		assertFalse(RunCommand.isUnauthorized(new IOException("connection refused")));
		assertFalse(RunCommand.isUnauthorized(new RuntimeException("no cause at all")));
	}

	/**
	 * A circular chain must not spin forever. {@link Throwable#initCause}
	 * rejects an exception as its own cause, but nothing stops two from
	 * pointing at each other.
	 */
	@Test
	void survivesACircularCauseChain()
	{
		RuntimeException first = new RuntimeException("first");
		RuntimeException second = new RuntimeException("second", first);
		first.initCause(second);

		assertFalse(RunCommand.isUnauthorized(first));
	}

	/**
	 * The whole point of asking: a yes is the only thing that puts a run on the
	 * server, and it is the answer that has to reach the runner untouched.
	 */
	@Test
	void onlyAYesFilesTheReport()
	{
		RunCommand command = new RunCommand();
		RecordingRunner runner = new RecordingRunner();
		command.runner = runner;
		ReportPayload run = run();

		assertEquals(CommandLine.ExitCode.OK, command.act(Answer.YES, run).intValue());
		assertSame(run, runner.submitted, "the run someone said yes to should be the one submitted");
	}

	/**
	 * Deciding not to report is a decision, not a failure — someone looked at
	 * the verdicts, did not like them, and will fix something first.
	 */
	@Test
	void decliningReportsNothingAndStillSucceeds()
	{
		RunCommand command = new RunCommand();
		RecordingRunner runner = new RecordingRunner();
		command.runner = runner;

		assertEquals(CommandLine.ExitCode.OK, command.act(Answer.NO, run()).intValue());
		assertNull(runner.submitted, "a no must not reach the server");
	}

	/**
	 * Nobody there to answer is a different thing from a no: it means a
	 * schedule is running the agent without {@code --yes} and has been
	 * reporting nothing. A zero exit would let that look healthy.
	 */
	@Test
	void anUnansweredPromptEndsNonZero()
	{
		RunCommand command = new RunCommand();
		RecordingRunner runner = new RecordingRunner();
		command.runner = runner;

		assertEquals(CommandLine.ExitCode.SOFTWARE, command.act(Answer.NONE, run()).intValue());
		assertNull(runner.submitted, "an unanswered prompt must not reach the server");
	}

	private static ReportPayload run()
	{
		return new ReportPayload("device", "user", Instant.now(), List.of(), List.of(), null);
	}

	/** Stands in for the runner so nothing needs a server, or osquery. */
	private static final class RecordingRunner extends ComplianceRunner
	{
		private ReportPayload submitted;

		@Override
		public void submit(ReportPayload run)
		{
			submitted = run;
		}
	}
}
