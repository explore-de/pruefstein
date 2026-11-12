package com.pruefstein.agent.runner;

import java.util.Arrays;
import java.util.Optional;

/**
 * Compares agent versions the way the server does. Only plain release numbers
 * count: a {@code -SNAPSHOT} or an unknown version is a development copy and
 * is never called outdated.
 */
final class AgentVersion
{
	private AgentVersion()
	{
	}

	static boolean isOutdated(String version, String latest)
	{
		Optional<int[]> running = parse(version);
		Optional<int[]> newest = parse(latest);
		return running.isPresent() && newest.isPresent() && compare(running.get(), newest.get()) < 0;
	}

	private static Optional<int[]> parse(String value)
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
		return Optional.of(Arrays.stream(trimmed.split("\\.")).mapToInt(Integer::parseInt).toArray());
	}

	private static int compare(int[] a, int[] b)
	{
		for (int i = 0; i < Math.max(a.length, b.length); i++)
		{
			int left = i < a.length ? a[i] : 0;
			int right = i < b.length ? b[i] : 0;
			if (left != right)
			{
				return Integer.compare(left, right);
			}
		}
		return 0;
	}
}
