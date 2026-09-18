package com.pruefstein.agent.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerUrlTest
{
	@Test
	void aUrlWithoutASchemeBecomesHttps()
	{
		assertEquals("https://pruefstein.example.com", ServerUrl.normalize("pruefstein.example.com"));
		assertEquals("https://pruefstein.example.com/", ServerUrl.normalize("pruefstein.example.com/"));
	}

	@Test
	void aHostAndPortBecomesHttpsToo()
	{
		// The case that would otherwise parse as a URI with the scheme
		// "localhost" and fail somewhere else entirely
		assertEquals("https://localhost:8080", ServerUrl.normalize("localhost:8080"));
	}

	@Test
	void aSchemeThatIsThereIsLeftAlone()
	{
		assertEquals("https://pruefstein.example.com", ServerUrl.normalize("https://pruefstein.example.com"));
		assertEquals("http://localhost:8080", ServerUrl.normalize("http://localhost:8080"));
	}

	@Test
	void surroundingWhitespaceIsDropped()
	{
		assertEquals("https://pruefstein.example.com", ServerUrl.normalize("  pruefstein.example.com  "));
		assertEquals("http://localhost:8080", ServerUrl.normalize(" http://localhost:8080 "));
	}

	@Test
	void nothingStaysNothing()
	{
		// What a missing server means is the caller's decision, not this
		// method's
		assertNull(ServerUrl.normalize(null));
		assertEquals("", ServerUrl.normalize(""));
		assertEquals("   ", ServerUrl.normalize("   "));
	}
}
