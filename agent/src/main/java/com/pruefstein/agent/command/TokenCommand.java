package com.pruefstein.agent.command;

import java.util.Optional;
import java.util.concurrent.Callable;

import com.pruefstein.agent.auth.AuthResolver;
import jakarta.inject.Inject;
import picocli.CommandLine;

/**
 * Prints the access token the agent holds, for an MCP client to send to the
 * server's {@code /mcp} endpoint as the signed-in user.
 * <p>
 * Never logs in: it runs as a client's header helper, where nobody sees a
 * device code, so a missing or unrefreshable login is an error that says to run
 * {@code login} instead. Nothing but the token goes to stdout.
 */
@CommandLine.Command(name = "token", description = "Print an access token for MCP clients", mixinStandardHelpOptions = true,
	versionProvider = AgentVersionProvider.class)
public class TokenCommand implements Callable<Integer>
{
	@Inject
	AuthResolver authResolver;

	@CommandLine.Option(
		names = "--header",
		description = "Print it as the JSON header object a headersHelper returns: "
			+ "{\"Authorization\": \"Bearer …\"}")
	boolean header;

	@Override
	public Integer call()
	{
		Optional<String> token = authResolver.currentToken();
		if (token.isEmpty())
		{
			System.err.println("Not logged in. Run: pruefstein-agent login --server <URL>");
			return 1;
		}
		System.out.println(format(token.get(), header));
		return 0;
	}

	/** A JWT holds no quotes or backslashes, so it goes into the JSON as is. */
	static String format(String token, boolean header)
	{
		return header ? "{\"Authorization\": \"Bearer " + token + "\"}" : token;
	}
}
