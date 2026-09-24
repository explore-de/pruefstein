package com.pruefstein.agent.command;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.enterprise.context.ApplicationScoped;
import picocli.CommandLine;

@TopCommand
@ApplicationScoped
@CommandLine.Command(
	name = "pruefstein-agent",
	description = "Prüfstein compliance agent",
	subcommands = {LoginCommand.class, LogoutCommand.class, RunCommand.class, SelfTestCommand.class,
		TokenCommand.class},
	mixinStandardHelpOptions = true,
	versionProvider = AgentVersionProvider.class)
public class MainCommand implements Runnable
{
	@Override
	public void run()
	{
		CommandLine.usage(this, System.out);
	}
}
