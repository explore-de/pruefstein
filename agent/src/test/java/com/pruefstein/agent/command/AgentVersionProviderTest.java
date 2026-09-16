package com.pruefstein.agent.command;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code --version} answered nothing at all for as long as the command existed:
 * picocli offers the flag the moment a command mixes in the standard help
 * options, and prints an empty string unless something provides the text. Exit
 * code 0, no output, nothing to notice.
 */
@QuarkusTest
class AgentVersionProviderTest
{
	@Test
	void namesTheCommandAndTheVersionItWasBuiltAt()
	{
		// given
		AgentVersionProvider provider = new AgentVersionProvider();

		// when
		String[] lines = provider.getVersion();

		// then
		assertEquals(1, lines.length);
		assertTrue(lines[0].startsWith("pruefstein-agent "), () -> "unexpected: " + lines[0]);
		assertFalse(lines[0].endsWith(AgentVersionProvider.UNKNOWN),
			"the build stamps quarkus.application.version, so it should never be unknown here");
	}

	@Test
	void everyCommandAnswersIt()
	{
		// given — each subcommand mixes in the standard help options of its
		// own, so each needs the provider; inheriting it is not a thing
		for (Class<?> command : new Class<?>[] {
			MainCommand.class, RunCommand.class, LoginCommand.class, LogoutCommand.class })
		{
			// when
			CommandLine.Command annotation = command.getAnnotation(CommandLine.Command.class);

			// then
			assertTrue(annotation.mixinStandardHelpOptions(), command.getSimpleName() + " offers --version");
			assertEquals(AgentVersionProvider.class, annotation.versionProvider(),
				command.getSimpleName() + " must be able to answer it");
		}
	}
}
