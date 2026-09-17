package com.pruefstein.agent;

import java.nio.file.Files;
import java.time.Instant;

import com.pruefstein.agent.auth.Credentials;
import com.pruefstein.agent.auth.TokenStore;
import com.pruefstein.agent.command.LoginCommand;
import com.pruefstein.agent.command.LogoutCommand;
import com.pruefstein.agent.command.MainCommand;
import com.pruefstein.agent.command.RunCommand;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SmokeTest
{
	@Inject
	@TopCommand
	Object topCommand;

	@Inject
	LogoutCommand logoutCommand;

	@Inject
	TokenStore tokenStore;

	@Test
	void applicationStartsAndTopCommandResolves()
	{
		assertNotNull(topCommand);
		assertInstanceOf(MainCommand.class, topCommand);
	}

	@Test
	void helpOutputContainsSubcommands()
	{
		CommandLine cmd = new CommandLine(topCommand);
		String help = cmd.getUsageMessage();
		assertTrue(help.contains("login"), "help should list 'login' subcommand");
		assertTrue(help.contains("logout"), "help should list 'logout' subcommand");
		assertTrue(help.contains("run"), "help should list 'run' subcommand");
	}

	/**
	 * The flag a schedule needs: without it an unattended {@code run} stops at
	 * a question nobody answers.
	 */
	@Test
	void runOffersAWayPastTheQuestion()
	{
		String help = new CommandLine(new RunCommand()).getUsageMessage();
		assertTrue(help.contains("--yes"), "run should offer '--yes'");
	}

	@Test
	void loginAcceptsAServerToReportTo()
	{
		String help = new CommandLine(new LoginCommand()).getUsageMessage();
		assertTrue(help.contains("--server"), "login should offer '--server'");
	}

	@Test
	void logoutClearsCredentials()
	{
		tokenStore.save(new Credentials("http://localhost:8080", "http://localhost:8180/realms/pruefstein",
			"pruefstein-web", "openid offline_access", "tok", "ref", Instant.now().plusSeconds(300)));
		assertTrue(Files.exists(TokenStore.credentialsFile()), "credentials file should exist after save");

		logoutCommand.run();

		assertFalse(Files.exists(TokenStore.credentialsFile()), "credentials file should be deleted after logout");
	}

	@Test
	void credentialsRoundTripThroughTheStore()
	{
		Credentials saved = new Credentials("https://pruefstein.example.com",
			"https://login.microsoftonline.com/tenant/v2.0", "client", "api://client/.default offline_access",
			"tok", "ref", Instant.now().plusSeconds(300));
		tokenStore.save(saved);

		Credentials loaded = tokenStore.load().orElseThrow();
		assertEquals(saved, loaded);
		assertEquals(saved.serverConfig(), loaded.serverConfig());

		logoutCommand.run();
	}
}
