package com.pruefstein.agent.command;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import com.pruefstein.agent.auth.AuthResolver;
import com.pruefstein.agent.client.ReportPayload;
import com.pruefstein.agent.runner.ComplianceRunner;
import com.pruefstein.agent.runner.OsqueryRequirement;
import com.pruefstein.agent.runner.Prompt;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import picocli.CommandLine;

@CommandLine.Command(name = "run", description = "Run all compliance checks, then offer to report them",
	mixinStandardHelpOptions = true, versionProvider = AgentVersionProvider.class)
public class RunCommand implements Callable<Integer>
{
	/** Deep enough for any real wrapping, shallow enough to end a loop. */
	private static final int MAX_CAUSE_DEPTH = 20;

	@CommandLine.Option(names = { "-y", "--yes" },
		description = "Report the run without asking first. What cron and CI need.")
	boolean assumeYes;

	@Inject
	AuthResolver authResolver;

	@Inject
	OsqueryRequirement osqueryRequirement;

	@Inject
	ComplianceRunner runner;

	/**
	 * A {@link Callable} rather than a {@link Runnable} for the sake of the
	 * exit code: a missing osquery or a declined report is not a crash, and
	 * a stack trace would be the wrong way to report it — while a zero exit
	 * would let a cron job believe the machine had been checked.
	 */
	@Override
	public Integer call()
	{
		// Before authenticating: a missing binary is worth knowing about
		// before someone is sent off to a browser to log in.
		if (!osqueryRequirement.ensureAvailable())
		{
			return CommandLine.ExitCode.SOFTWARE;
		}

		try
		{
			authResolver.ensureAuthenticated();
		}
		catch (Exception e)
		{
			throw new RuntimeException("Authentication failed.", e);
		}

		Optional<ReportPayload> run = withFreshCredentials(runner::check);
		if (run.isEmpty())
		{
			// No checks configured on the server: there is nothing to look at
			// and nothing to ask about.
			return CommandLine.ExitCode.OK;
		}
		return report(run.get());
	}

	/**
	 * Asks before anything leaves the machine.
	 * <p>
	 * By this point every verdict is on the screen and the server has been told
	 * nothing, so a run that reads badly can be fixed and repeated — only the
	 * run someone means to stand behind becomes a report. It is also the last
	 * moment at which that is true: a report cannot be recalled once it is
	 * filed.
	 */
	private Integer report(ReportPayload run)
	{
		return act(assumeYes ? Prompt.Answer.YES : Prompt.ask("Report this run? [y/N]"), run);
	}

	/**
	 * Split from the question so the three endings can be tested without a
	 * terminal to type into.
	 */
	Integer act(Prompt.Answer answer, ReportPayload run)
	{
		return switch (answer)
		{
			case YES -> submit(run);
			case NO -> declined();
			case NONE -> unattended();
		};
	}

	private Integer submit(ReportPayload run)
	{
		withFreshCredentials(() -> {
			runner.submit(run);
			return null;
		});
		return CommandLine.ExitCode.OK;
	}

	private static Integer declined()
	{
		System.out.println("Nothing was reported. Run again once you have fixed what you want to fix.");
		return CommandLine.ExitCode.OK;
	}

	/**
	 * Nobody answered, because nobody was there — {@code run} from cron or
	 * launchd, with stdin closed. A zero exit would let that schedule look
	 * healthy for as long as it kept reporting nothing, so it ends non-zero and
	 * names the flag that makes it work unattended.
	 */
	private static Integer unattended()
	{
		// Nothing typed the newline that would have closed the prompt line
		System.out.println();
		System.out.println("Nothing was reported: there was nobody to answer the question. "
			+ "Use 'pruefstein-agent run --yes' for an unattended run.");
		return CommandLine.ExitCode.SOFTWARE;
	}

	/**
	 * Runs one exchange with the server, and gets new credentials if it turns
	 * out the stored ones are no longer good.
	 * <p>
	 * The cached token satisfied its own expiry and the server still refused
	 * it, so only the server could have told us. Getting a new one and asking
	 * again beats a stack trace that says 401 and leaves the machine unchecked.
	 * Wrapping each exchange separately rather than the whole command is what
	 * keeps a token that expires between the checks and the answer from costing
	 * someone their run: only the submission is retried, and nobody is asked
	 * twice.
	 */
	private <T> T withFreshCredentials(Supplier<T> exchange)
	{
		try
		{
			return exchange.get();
		}
		catch (Exception e)
		{
			if (!isUnauthorized(e))
			{
				throw e;
			}
			System.out.println("The server rejected the stored credentials. Authenticating again.");
			try
			{
				authResolver.reauthenticate();
			}
			catch (Exception failure)
			{
				throw new RuntimeException(
					"Could not authenticate again after the server rejected the stored credentials. "
						+ "Run 'pruefstein-agent login'.",
					failure);
			}
			try
			{
				return exchange.get();
			}
			catch (Exception retryFailure)
			{
				if (!isUnauthorized(retryFailure))
				{
					throw retryFailure;
				}
				// A refresh can succeed and still hand back a token the server
				// will not take — a rotated client, a changed audience. Saying
				// so beats a second stack trace that only repeats the 401.
				throw new RuntimeException(
					"The server rejected the credentials again after re-authenticating. "
						+ "Run 'pruefstein-agent login' to authenticate from scratch.",
					retryFailure);
			}
		}
	}

	/**
	 * Walks the cause chain rather than checking the top frame: the REST client
	 * wraps the response status in a {@code ClientWebApplicationException},
	 * which arrives wrapped again once it has crossed the CDI proxy.
	 * <p>
	 * Bounded rather than walked to the end. {@link Throwable#initCause} only
	 * refuses to make an exception its own cause, so a chain that loops back on
	 * itself two links later is legal, and no genuine chain is this deep.
	 */
	static boolean isUnauthorized(Throwable failure)
	{
		Throwable current = failure;
		for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++)
		{
			if (current instanceof WebApplicationException web
				&& web.getResponse() != null
				&& web.getResponse().getStatus() == Response.Status.UNAUTHORIZED.getStatusCode())
			{
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
