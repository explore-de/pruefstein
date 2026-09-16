package com.pruefstein.agent.command;

import com.pruefstein.agent.auth.TokenStore;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(name = "logout", description = "Clear stored credentials", mixinStandardHelpOptions = true,
	versionProvider = AgentVersionProvider.class)
public class LogoutCommand implements Runnable
{
	@Inject
	TokenStore tokenStore;

	@Override
	public void run()
	{
		tokenStore.clear();
		System.out.println("Logged out. Credentials cleared.");
	}
}
