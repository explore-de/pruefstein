package com.pruefstein.agent.client;

/**
 * @param latest
 *            the newest agent released, or {@code null} while the server does
 *            not know
 */
public record AgentRelease(String latest)
{
}
