package com.pruefstein.osversion.domain;

/**
 * How a device's macOS compares to the newest release Apple had published.
 * <p>
 * The names say which number differs, because that is what decides how loudly
 * the report says it: a missing fix is a nag, a missing feature update is a
 * problem, and a whole train behind is a machine nobody has looked at in a year
 * or more — unless it is on the newest fix of a train Apple still patches,
 * which is a choice rather than neglect.
 */
public enum OsVersionStanding
{
	/** At the newest release, or ahead of it on a beta. */
	CURRENT,

	/** Same train and feature update, missing a fix. Shown in amber. */
	PATCH_BEHIND,

	/**
	 * An older train, but on its newest fix, and a train Apple still ships
	 * security fixes for. Not the newest macOS, yet nothing left to install
	 * short of the upgrade. Shown in amber.
	 */
	OLDER_TRAIN_PATCHED,

	/** Same train, an older feature update. Shown in red. */
	MINOR_BEHIND,

	/** An older train altogether. Shown in red, and its age is named. */
	MAJOR_BEHIND,

	/** No version was reported, or it could not be read. */
	UNKNOWN
}
