package com.pruefstein.agent.command;

import com.pruefstein.agent.auth.AuthResolver;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(name = "login", description = "Authenticate with the Prüfstein server", mixinStandardHelpOptions = true,
	versionProvider = AgentVersionProvider.class)
public class LoginCommand implements Runnable
{
	@Inject
	AuthResolver authResolver;

	@CommandLine.Option(
		names = {"-s", "--server"},
		paramLabel = "URL",
		description = "Prüfstein server to report to, e.g. https://pruefstein.example.com. "
			+ "Stored with the credentials and reused by every later run.")
	String server;

	@Override
	public void run()
	{
		try
		{
			authResolver.ensureAuthenticated(server);
			System.out.println("Login successful. Credentials cached for future runs.");
		}
		catch (Exception e)
		{
			throw new RuntimeException("Login failed: " + e.getMessage(), e);
		}
	}
}
