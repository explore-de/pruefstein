package com.pruefstein.agent.command;

import java.net.ConnectException;

import org.junit.jupiter.api.Test;
import picocli.CommandLine.Help.Ansi;

import static org.junit.jupiter.api.Assertions.*;

class ErrorReporterTest
{
	/**
	 * The failure that used to print as {@code Authentication failed: null}
	 * followed by a stack trace: a message-less {@link ConnectException} under
	 * the command's own wrapper.
	 */
	@Test
	void namesTheServerAndSaysWhatToDoWhenNothingAnswers()
	{
		ConnectException described = new ConnectException(
			"Could not connect to the Prüfstein server at http://localhost:8080.");
		described.initCause(new ConnectException());

		String text = ErrorReporter.describe(new RuntimeException("Authentication failed.", described), Ansi.OFF);

		assertEquals("""
			Error: Authentication failed.
			  Could not connect to the Prüfstein server at http://localhost:8080.
			Nothing answered. Check that the server is running and reachable, \
			or point the agent at another one with 'pruefstein-agent login --server URL'.""",
			text.replace(System.lineSeparator(), "\n"));
		assertFalse(text.contains("null"));
	}

	@Test
	void printsAMessageRepeatedByItsWrapperOnlyOnce()
	{
		IllegalStateException cause = new IllegalStateException("HTTP 500");

		String text = ErrorReporter.describe(new RuntimeException(cause), Ansi.OFF);

		assertEquals("Error: java.lang.IllegalStateException: HTTP 500", text);
	}

	@Test
	void fallsBackToTheExceptionTypeWhenNothingHasAMessage()
	{
		assertEquals("Error: IllegalStateException", ErrorReporter.describe(new IllegalStateException(), Ansi.OFF));
	}
}
