package com.pruefstein.agent.command;

import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import picocli.CommandLine;

@ApplicationScoped
public class CommandLineProducer
{
	/** Replaces the one Quarkus would build, only to report failures through {@link ErrorReporter}. */
	@Produces
	CommandLine commandLine(PicocliCommandLineFactory factory)
	{
		return factory.create().setExecutionExceptionHandler(new ErrorReporter());
	}
}
