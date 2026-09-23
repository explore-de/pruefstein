package com.pruefstein.dashboard.api;

/**
 * One bar of the violations chart: a check, and how much of the fleet is
 * currently failing it.
 *
 * <p>
 * Counted over the newest run of each device, so a bar is a number of machines
 * rather than a number of runs — the question the chart answers is "what is
 * broken out there now", not "what has ever been broken".
 *
 * @param name
 *            the check's name
 * @param control
 *            the ISO 27001 Annex A control it answers to, or {@code null} when
 *            the check is not mapped to one
 * @param devices
 *            machines whose newest run failed this check
 * @param fleetPct
 *            those machines as a whole percent of the fleet, floored
 * @param barPct
 *            the bar's length as a percent of the longest bar
 */
public record ViolationShare(
	String name,
	String control,
	long devices,
	int fleetPct,
	int barPct)
{
	public boolean hasControl()
	{
		return control != null && !control.isBlank();
	}

	public String deviceLabel()
	{
		return devices == 1 ? "1 device" : devices + " devices";
	}
}
