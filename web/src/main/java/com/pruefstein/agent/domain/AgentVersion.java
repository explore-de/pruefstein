package com.pruefstein.agent.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * A released agent version, e.g. {@code 1.0.7}.
 * <p>
 * Only plain release numbers parse. A {@code -SNAPSHOT} build or an agent that
 * could not name its own version is somebody's development copy, and calling it
 * outdated would only be noise.
 */
public record AgentVersion(int[] parts) implements Comparable<AgentVersion>
{
	/**
	 * @param value
	 *            a version such as {@code 1.0.7}, or a tag such as
	 *            {@code v1.0.7}
	 */
	public static Optional<AgentVersion> parse(String value)
	{
		if (value == null)
		{
			return Optional.empty();
		}
		String trimmed = value.strip();
		if (trimmed.startsWith("v"))
		{
			trimmed = trimmed.substring(1);
		}
		if (!trimmed.matches("\\d+(\\.\\d+)*"))
		{
			return Optional.empty();
		}
		return Optional.of(new AgentVersion(Arrays.stream(trimmed.split("\\.")).mapToInt(Integer::parseInt).toArray()));
	}

	/**
	 * Whether {@code version} is a release older than {@code latest}. Anything
	 * that does not parse is not called outdated.
	 */
	public static boolean isOutdated(String version, String latest)
	{
		Optional<AgentVersion> running = parse(version);
		Optional<AgentVersion> newest = parse(latest);
		return running.isPresent() && newest.isPresent() && running.get().compareTo(newest.get()) < 0;
	}

	@Override
	public int compareTo(AgentVersion other)
	{
		for (int i = 0; i < Math.max(parts.length, other.parts.length); i++)
		{
			int mine = i < parts.length ? parts[i] : 0;
			int theirs = i < other.parts.length ? other.parts[i] : 0;
			if (mine != theirs)
			{
				return Integer.compare(mine, theirs);
			}
		}
		return 0;
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof AgentVersion version && compareTo(version) == 0;
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(parts);
	}

	@Override
	public String toString()
	{
		return String.join(".", Arrays.stream(parts).mapToObj(String::valueOf).toList());
	}
}
