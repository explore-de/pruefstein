package com.pruefstein.agent.command;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenCommandTest
{
	@Test
	void printsTheBareTokenByDefault()
	{
		assertEquals("abc.def.ghi", TokenCommand.format("abc.def.ghi", false));
	}

	/** What an MCP client's headersHelper has to print: one JSON object. */
	@Test
	void printsAnAuthorizationHeaderObjectForAHeadersHelper()
	{
		JsonObject headers = new JsonObject(TokenCommand.format("abc.def.ghi", true));

		assertEquals("Bearer abc.def.ghi", headers.getString("Authorization"));
	}
}
