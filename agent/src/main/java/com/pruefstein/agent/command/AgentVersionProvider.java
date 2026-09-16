package com.pruefstein.agent.command;

import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine;

/**
 * Answers {@code --version}.
 * <p>
 * Picocli offers the flag as soon as a command mixes in the standard help
 * options, but prints nothing at all unless something supplies the text — which
 * is how the agent shipped a {@code --version} that succeeded silently.
 * <p>
 * The number comes from {@code quarkus.application.version}, which Quarkus sets
 * from the pom and bakes into the native image, rather than from a constant
 * here that would drift from the build it is printed by.
 */
public class AgentVersionProvider implements CommandLine.IVersionProvider
{
	static final String UNKNOWN = "unknown";

	@Override
	public String[] getVersion()
	{
		return new String[] { "pruefstein-agent " + version() };
	}

	static String version()
	{
		try
		{
			return ConfigProvider.getConfig()
				.getOptionalValue("quarkus.application.version", String.class)
				.filter(value -> !value.isBlank())
				.orElse(UNKNOWN);
		}
		catch (Exception e)
		{
			// Asking the version is never worth a stack trace at the user.
			return UNKNOWN;
		}
	}
}
