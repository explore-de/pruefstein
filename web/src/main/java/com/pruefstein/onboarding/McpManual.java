package com.pruefstein.onboarding;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * How to connect an AI assistant to this server over MCP. The token comes from
 * the agent, which already knows how to sign in, so the walkthrough leans on
 * {@link SetupManual} for the install and login steps rather than inventing a
 * second way to authenticate.
 */
@ApplicationScoped
public class McpManual
{
	/** What the server is registered as in the client, and what it shows. */
	static final String SERVER_NAME = "pruefstein";

	@Inject
	SetupManual setupManual;

	public String mcpUrl()
	{
		return setupManual.baseUrl() + "/mcp";
	}

	/**
	 * A token lasts minutes, so it is fetched per connection by a headersHelper
	 * rather than pasted in once and left to expire.
	 */
	public String claudeCodeCommand()
	{
		return "claude mcp add-json --scope user " + SERVER_NAME
			+ " '{\"type\":\"http\",\"url\":\"" + mcpUrl()
			+ "\",\"headersHelper\":\"pruefstein-agent token --header\"}'";
	}

	public String tokenCommand()
	{
		return "pruefstein-agent token";
	}
}
