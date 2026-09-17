package com.pruefstein.agent.auth;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenStoreTest
{
	/**
	 * A native image runs static initialisers at build time, so a path built
	 * once pointed every installed binary at the build runner's home. The path
	 * has to follow {@code user.home} as it is when the agent runs.
	 */
	@Test
	void theCredentialsFileFollowsTheHomeDirectoryAtTheTimeOfAsking()
	{
		String original = System.getProperty("user.home");
		try
		{
			System.setProperty("user.home", "/home/somebody-else");

			assertEquals(Path.of("/home/somebody-else/.config/pruefstein/credentials.json"),
				TokenStore.credentialsFile());
		}
		finally
		{
			System.setProperty("user.home", original);
		}
	}
}
