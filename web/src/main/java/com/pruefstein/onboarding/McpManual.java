package com.pruefstein.onboarding;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * How to connect an AI assistant to this server over MCP. The client logs in by
 * itself: the 401 from {@code /mcp} points it at the protected resource
 * metadata, and from there at the IdP. What it cannot discover is which client
 * to log in as and where the IdP may send it back, because neither IdP
 * registers clients on demand — so those two come from here.
 */
@ApplicationScoped
public class McpManual
{
	/** What the server is registered as in the client, and what it shows. */
	static final String SERVER_NAME = "pruefstein";

	@Inject
	SetupManual setupManual;

	@ConfigProperty(name = "pruefstein.mcp.client-id")
	String clientId;

	@ConfigProperty(name = "pruefstein.mcp.callback-port")
	int callbackPort;

	public String mcpUrl()
	{
		return setupManual.baseUrl() + "/mcp";
	}

	public String clientId()
	{
		return clientId;
	}

	/** The one redirect URI the IdP has registered for MCP clients. */
	public String redirectUri()
	{
		return "http://localhost:" + callbackPort + "/callback";
	}

	public String claudeCodeCommand()
	{
		return "claude mcp add --transport http --scope user --client-id " + clientId
			+ " --callback-port " + callbackPort + " " + SERVER_NAME + " " + mcpUrl();
	}
}
