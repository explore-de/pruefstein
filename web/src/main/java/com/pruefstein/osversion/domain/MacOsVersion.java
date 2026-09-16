package com.pruefstein.osversion.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A macOS marketing version — {@code 15.7.9}, {@code 26.7}, {@code 27.0} —
 * parsed into the three numbers a comparison actually needs.
 *
 * @param major
 *            the train, e.g. 15 for Sequoia or 26 for Tahoe
 * @param minor
 *            the feature update within the train
 * @param patch
 *            the fix within the feature update; 0 when the version omits it
 */
public record MacOsVersion(int major, int minor, int patch) implements Comparable<MacOsVersion>
{
	private static final Pattern VERSION = Pattern.compile("^\\s*(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

	/**
	 * Apple numbered macOS sequentially from 11 (Big Sur, 2020) to 15 (Sequoia,
	 * 2024), then switched to naming a train after the year it ships into: 26
	 * shipped in 2025, 27 in 2026. Both runs are closed formulas, so the year
	 * of a train released after this was written still comes out right.
	 * Anything outside them — 10.x, where the major says nothing about the year
	 * — has no answer here.
	 */
	private static final int FIRST_SEQUENTIAL = 11;

	private static final int LAST_SEQUENTIAL = 15;

	private static final int FIRST_YEAR_BASED = 26;

	/**
	 * Parses a version as osquery reports it, ignoring anything after the three
	 * numbers.
	 *
	 * @return the version, or empty if the text does not start with one
	 */
	public static Optional<MacOsVersion> parse(String raw)
	{
		if (raw == null || raw.isBlank())
		{
			return Optional.empty();
		}
		Matcher matcher = VERSION.matcher(raw);
		if (!matcher.find())
		{
			return Optional.empty();
		}
		return Optional.of(new MacOsVersion(
			Integer.parseInt(matcher.group(1)),
			matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)),
			matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3))));
	}

	/**
	 * How far behind {@code latest} this version is — the coarsest number that
	 * differs, because that is the one that matters. A version at or ahead of
	 * the latest release is {@link OsVersionStanding#CURRENT}; a machine on a
	 * beta is not behind anything.
	 */
	public OsVersionStanding standingAgainst(MacOsVersion latest)
	{
		if (latest == null || compareTo(latest) >= 0)
		{
			return OsVersionStanding.CURRENT;
		}
		if (major != latest.major)
		{
			return OsVersionStanding.MAJOR_BEHIND;
		}
		if (minor != latest.minor)
		{
			return OsVersionStanding.MINOR_BEHIND;
		}
		return OsVersionStanding.PATCH_BEHIND;
	}

	/**
	 * The calendar year this train first shipped, where the major number says
	 * so. See {@link #FIRST_SEQUENTIAL} for why it sometimes does not.
	 */
	public OptionalInt trainReleaseYear()
	{
		if (major >= FIRST_SEQUENTIAL && major <= LAST_SEQUENTIAL)
		{
			return OptionalInt.of(2009 + major);
		}
		if (major >= FIRST_YEAR_BASED)
		{
			return OptionalInt.of(1999 + major);
		}
		return OptionalInt.empty();
	}

	/**
	 * How many years old this train is, counted the way people say it: the
	 * difference between its release year and the year it is being read in.
	 * <p>
	 * Not measured from an exact release date, because there isn't one to
	 * measure from — Apple has shipped a major anywhere between mid-September
	 * and mid-November, and the feed gives the date of a train's latest patch,
	 * not of the train. Counting calendar years is what someone means when they
	 * call a machine two versions old, and it never turns "released two years
	 * ago" into "one", which dating it to a nominal day in autumn does for the
	 * fortnight before the anniversary.
	 *
	 * @return the age in years, never negative, or empty when the train's year
	 *         is unknown
	 */
	public OptionalInt trainAgeInYears(LocalDate on)
	{
		OptionalInt year = trainReleaseYear();
		if (year.isEmpty())
		{
			return OptionalInt.empty();
		}
		return OptionalInt.of(Math.max(0, on.getYear() - year.getAsInt()));
	}

	@Override
	public int compareTo(MacOsVersion other)
	{
		int byMajor = Integer.compare(major, other.major);
		if (byMajor != 0)
		{
			return byMajor;
		}
		int byMinor = Integer.compare(minor, other.minor);
		return byMinor != 0 ? byMinor : Integer.compare(patch, other.patch);
	}

	@Override
	public String toString()
	{
		return patch == 0 ? major + "." + minor : major + "." + minor + "." + patch;
	}
}
