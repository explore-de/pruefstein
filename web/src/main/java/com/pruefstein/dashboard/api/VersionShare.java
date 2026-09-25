package com.pruefstein.dashboard.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.pruefstein.osversion.domain.OsVersionStanding;

/**
 * One bar of the macOS version chart: a version, how many machines are on it,
 * and how far behind that leaves them.
 *
 * <p>
 * Length carries the count and colour carries the standing, so the two say
 * different things — a long red bar is a lot of machines on something old,
 * which is exactly the shape the reader is looking for. Colour never carries
 * the standing alone: {@link #note()} spells it out beside every bar.
 *
 * @param version
 *            the version as the machines reported it, e.g. {@code 15.7.9}, or
 *            the summary label of a folded row
 * @param devices
 *            machines whose newest run reported this version
 * @param fleetPct
 *            those machines as a whole percent of the fleet, floored
 * @param barPct
 *            the bar's length as a percent of the longest bar — the chart has
 *            no axis, so the bars are scaled against each other and every value
 *            is printed
 * @param folded
 *            whether this row stands for several versions that did not fit
 * @param reportFilter
 *            the version the reports screen can be filtered by to list these
 *            machines, or {@code null} for a row that is not one version — the
 *            folded tail and the machines that never said
 */
public record VersionShare(
	String version,
	OsVersionStanding standing,
	long devices,
	int fleetPct,
	int barPct,
	boolean folded,
	String reportFilter)
{
	/** Green: at the newest release Apple has published, or ahead of it. */
	public boolean isCurrent()
	{
		return !folded && standing == OsVersionStanding.CURRENT;
	}

	/** Amber: the right feature update, missing a fix. */
	public boolean isPatchBehind()
	{
		return !folded && standing == OsVersionStanding.PATCH_BEHIND;
	}

	/**
	 * Red: an older feature update, an older train, or a folded tail of both.
	 */
	public boolean isBehind()
	{
		return folded
			|| standing == OsVersionStanding.MINOR_BEHIND
			|| standing == OsVersionStanding.MAJOR_BEHIND;
	}

	/** Grey: the machine never said, so there is nothing to judge. */
	public boolean isUnknown()
	{
		return !folded && standing == OsVersionStanding.UNKNOWN;
	}

	/**
	 * The standing in words, so the colour is never the only thing saying it.
	 */
	public String note()
	{
		if (folded)
		{
			return "older releases";
		}
		return switch (standing)
		{
			case CURRENT -> "up to date";
			case PATCH_BEHIND -> "missing a fix";
			case MINOR_BEHIND -> "an update behind";
			case MAJOR_BEHIND -> "a major version behind";
			case UNKNOWN -> "no version reported";
		};
	}

	/** Whether the bar links to the reports on its version. */
	public boolean hasReports()
	{
		return reportFilter != null;
	}

	/**
	 * The query string that filters the reports screen to this version. Encoded
	 * here because the version is whatever the agent sent.
	 */
	public String reportsQuery()
	{
		return "os=" + URLEncoder.encode(reportFilter, StandardCharsets.UTF_8);
	}

	public String deviceLabel()
	{
		return devices == 1 ? "1 device" : devices + " devices";
	}
}
