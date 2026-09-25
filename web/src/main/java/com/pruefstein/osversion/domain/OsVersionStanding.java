package com.pruefstein.osversion.domain;

/**
 * How a device's macOS compares to the newest release Apple had published.
 * <p>
 * Within the newest train, the names say which number differs, because that
 * decides how loudly the report says it: a missing fix is a nag, a missing
 * feature update is a problem. On an older train, what matters is whether Apple
 * still patches it and whether the machine has taken those patches — the newest
 * fix of a supported train is a choice, anything short of it is neglect, and a
 * train Apple has dropped cannot be fixed without upgrading.
 */
public enum OsVersionStanding
{
	/** At the newest release, or ahead of it on a beta. */
	CURRENT,

	/** Newest train and feature update, missing a fix. Shown in amber. */
	PATCH_BEHIND,

	/** Newest train, an older feature update. Shown in red. */
	MINOR_BEHIND,

	/**
	 * An older train Apple still ships security fixes for, on its newest fix.
	 * Not the newest macOS, yet nothing left to install short of the upgrade.
	 * Shown in amber.
	 */
	OLDER_TRAIN_PATCHED,

	/**
	 * An older train Apple still ships security fixes for, but not on its
	 * newest fix — or its newest fix is not known. Shown in red, and its age is
	 * named.
	 */
	OLDER_TRAIN_UNPATCHED,

	/**
	 * A train Apple no longer ships security fixes for, or one too old to tell.
	 * Shown in red, and its age is named.
	 */
	UNSUPPORTED_TRAIN,

	/** No version was reported, or it could not be read. */
	UNKNOWN
}
