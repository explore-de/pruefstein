package com.pruefstein.osversion.service;

import com.pruefstein.osversion.domain.OsVersionStanding;

/**
 * What a report should say about the operating system the device was running.
 *
 * @param name
 *            the OS name as reported, e.g. {@code macOS}
 * @param reported
 *            the version the device reported, or {@code null} if it reported
 *            none
 * @param build
 *            the build identifier, or {@code null}
 * @param latest
 *            the newest release Apple had published when this report was filed,
 *            or {@code null} if that was not known
 * @param standing
 *            how the two compare
 * @param yearsBehind
 *            whole years since the device's train shipped, or {@code 0} when
 *            that cannot be worked out
 */
public record OsVersionAssessment(
	String name,
	String reported,
	String build,
	String latest,
	OsVersionStanding standing,
	int yearsBehind)
{
	public static OsVersionAssessment unknown()
	{
		return new OsVersionAssessment(null, null, null, null, OsVersionStanding.UNKNOWN, 0);
	}

	public String getName()
	{
		return name != null ? name : "macOS";
	}

	public String getReported()
	{
		return reported;
	}

	public String getBuild()
	{
		return build;
	}

	public String getLatest()
	{
		return latest;
	}

	public boolean isKnown()
	{
		return standing != OsVersionStanding.UNKNOWN;
	}

	public boolean isCurrent()
	{
		return standing == OsVersionStanding.CURRENT;
	}

	/** A missing fix — worth saying, not worth alarming anyone. Amber. */
	public boolean isPatchBehind()
	{
		return standing == OsVersionStanding.PATCH_BEHIND;
	}

	/**
	 * An older train, fully patched and still supported by Apple. Amber — not
	 * the newest, but nothing left to fix short of the upgrade.
	 */
	public boolean isOlderTrainPatched()
	{
		return standing == OsVersionStanding.OLDER_TRAIN_PATCHED;
	}

	/** An older feature update within the same train. Red. */
	public boolean isMinorBehind()
	{
		return standing == OsVersionStanding.MINOR_BEHIND;
	}

	/** An older train altogether. Red, and its age gets named. */
	public boolean isMajorBehind()
	{
		return standing == OsVersionStanding.MAJOR_BEHIND;
	}

	/** Whether anything at all is out of date. */
	public boolean isBehind()
	{
		return isPatchBehind() || isOlderTrainPatched() || isMinorBehind() || isMajorBehind();
	}

	public int getYearsBehind()
	{
		return yearsBehind;
	}

	/**
	 * Whether the train's age is worth printing. A train less than a year old
	 * is not worth calling old, and one whose year we cannot work out is not
	 * worth guessing at.
	 */
	public boolean isAgeKnown()
	{
		return yearsBehind >= 1;
	}

	/**
	 * The extra mark a whole-train-behind machine earns, e.g.
	 * {@code 2-year-old} — attributive, so it stays singular however many years
	 * it is.
	 */
	public String getAgeLabel()
	{
		return yearsBehind + "-year-old";
	}
}
