package com.pruefstein.agent.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVersionTest
{
	@Test
	void anOlderReleaseIsOutdated()
	{
		assertTrue(AgentVersion.isOutdated("1.0.5", "1.0.7"));
		assertTrue(AgentVersion.isOutdated("1.0.9", "1.0.10"));
		assertTrue(AgentVersion.isOutdated("1.0", "1.0.1"));
	}

	@Test
	void theLatestOrNewerIsNot()
	{
		assertFalse(AgentVersion.isOutdated("1.0.7", "1.0.7"));
		assertFalse(AgentVersion.isOutdated("1.0.7", "v1.0.7"));
		assertFalse(AgentVersion.isOutdated("1.1.0", "1.0.7"));
	}

	/** A development build or an unknown version is never called outdated. */
	@Test
	void whatDoesNotParseIsNot()
	{
		assertFalse(AgentVersion.isOutdated("1.0.6-SNAPSHOT", "1.0.7"));
		assertFalse(AgentVersion.isOutdated("unknown", "1.0.7"));
		assertFalse(AgentVersion.isOutdated(null, "1.0.7"));
		assertFalse(AgentVersion.isOutdated("1.0.5", null));
	}
}
