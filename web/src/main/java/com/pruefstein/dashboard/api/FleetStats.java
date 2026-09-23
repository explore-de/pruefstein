package com.pruefstein.dashboard.api;

import java.util.List;

/**
 * The two fleet charts and the population they were both counted over.
 *
 * <p>
 * One population for both, deliberately: the charts sit under each other on one
 * screen, and a reader who adds up the bars of one and compares them to the
 * other has to arrive somewhere sensible. Both count the newest run of every
 * device exactly once.
 *
 * @param deviceCount
 *            machines that have ever reported — the denominator behind every
 *            percentage in either chart
 */
public record FleetStats(
	long deviceCount,
	List<VersionShare> versions,
	List<ViolationShare> violations)
{
	public static FleetStats empty()
	{
		return new FleetStats(0, List.of(), List.of());
	}

	public boolean hasDevices()
	{
		return deviceCount > 0;
	}

	public boolean hasViolations()
	{
		return !violations.isEmpty();
	}

	public String deviceLabel()
	{
		return deviceCount == 1 ? "1 device" : deviceCount + " devices";
	}
}
