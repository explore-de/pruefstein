package com.pruefstein.osversion.domain;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacOsVersionTest
{
	@Test
	void parsesTheThreeShapesAppleShips()
	{
		// given / when / then — full, two-part, and a bare major
		assertEquals(new MacOsVersion(15, 7, 9), MacOsVersion.parse("15.7.9").orElseThrow());
		assertEquals(new MacOsVersion(26, 7, 0), MacOsVersion.parse("26.7").orElseThrow());
		assertEquals(new MacOsVersion(27, 0, 0), MacOsVersion.parse("27").orElseThrow());
	}

	@Test
	void ignoresAnythingAfterTheNumbers()
	{
		// given — osquery has been known to decorate the version

		// when / then
		assertEquals(new MacOsVersion(15, 7, 9), MacOsVersion.parse(" 15.7.9 (build 24G830)").orElseThrow());
	}

	@Test
	void refusesWhatIsNotAVersion()
	{
		// given / when / then
		assertTrue(MacOsVersion.parse(null).isEmpty());
		assertTrue(MacOsVersion.parse("").isEmpty());
		assertTrue(MacOsVersion.parse("Sequoia").isEmpty());
	}

	@Test
	void namesTheCoarsestNumberThatDiffers()
	{
		// given
		MacOsVersion latest = new MacOsVersion(27, 1, 2);

		// when / then
		assertEquals(OsVersionStanding.CURRENT, new MacOsVersion(27, 1, 2).standingAgainst(latest));
		assertEquals(OsVersionStanding.PATCH_BEHIND, new MacOsVersion(27, 1, 1).standingAgainst(latest));
		assertEquals(OsVersionStanding.MINOR_BEHIND, new MacOsVersion(27, 0, 0).standingAgainst(latest));
		assertEquals(OsVersionStanding.MAJOR_BEHIND, new MacOsVersion(26, 7, 0).standingAgainst(latest));
		// A whole train behind outranks being up to date within that train
		assertEquals(OsVersionStanding.MAJOR_BEHIND, new MacOsVersion(15, 9, 9).standingAgainst(latest));
	}

	@Test
	void theNewestFixOfAStillPatchedTrainIsNotRed()
	{
		// given — 27 is newest; Apple still patches 26 and 15
		MacOsVersion latest = new MacOsVersion(27, 0, 0);

		// when / then
		assertEquals(OsVersionStanding.OLDER_TRAIN_PATCHED,
			new MacOsVersion(26, 7, 1).standingAgainst(latest, new MacOsVersion(26, 7, 1)));
		assertEquals(OsVersionStanding.OLDER_TRAIN_PATCHED,
			new MacOsVersion(15, 7, 9).standingAgainst(latest, new MacOsVersion(15, 7, 9)));
	}

	@Test
	void anOlderTrainStaysRedWhenItIsMissingItsOwnFix()
	{
		// given
		MacOsVersion latest = new MacOsVersion(27, 0, 0);

		// when / then — one fix short of its train's newest
		assertEquals(OsVersionStanding.MAJOR_BEHIND,
			new MacOsVersion(26, 7, 0).standingAgainst(latest, new MacOsVersion(26, 7, 1)));
		// and when the train's newest is not known at all
		assertEquals(OsVersionStanding.MAJOR_BEHIND,
			new MacOsVersion(26, 7, 1).standingAgainst(latest, null));
	}

	@Test
	void aTrainApplesStoppedPatchingStaysRedEvenFullyPatched()
	{
		// given — with 27 newest, 14 (2023) is three trains back
		MacOsVersion latest = new MacOsVersion(27, 0, 0);

		// when / then
		assertEquals(OsVersionStanding.MAJOR_BEHIND,
			new MacOsVersion(14, 8, 1).standingAgainst(latest, new MacOsVersion(14, 8, 1)));
		assertEquals(OsVersionStanding.MAJOR_BEHIND,
			new MacOsVersion(10, 15, 7).standingAgainst(latest, new MacOsVersion(10, 15, 7)));
	}

	@Test
	void theNewestTrainIsJudgedAsBeforeWhateverItsTrainSays()
	{
		// given
		MacOsVersion latest = new MacOsVersion(27, 1, 2);

		// when / then — the extra yardstick only ever softens a major gap
		assertEquals(OsVersionStanding.PATCH_BEHIND,
			new MacOsVersion(27, 1, 1).standingAgainst(latest, latest));
		assertEquals(OsVersionStanding.MINOR_BEHIND,
			new MacOsVersion(27, 0, 0).standingAgainst(latest, latest));
	}

	@Test
	void aMachineOnABetaIsNotBehindAnything()
	{
		// given — a device ahead of the newest public release
		MacOsVersion latest = new MacOsVersion(27, 0, 0);

		// when / then
		assertEquals(OsVersionStanding.CURRENT, new MacOsVersion(27, 1, 0).standingAgainst(latest));
		assertEquals(OsVersionStanding.CURRENT, new MacOsVersion(28, 0, 0).standingAgainst(latest));
	}

	@Test
	void knowsWhenBothOfApplesNumberingSchemesShipped()
	{
		// given — sequential 11..15, then year-based from 26

		// when / then
		assertEquals(2020, new MacOsVersion(11, 7, 11).trainReleaseYear().getAsInt());
		assertEquals(2024, new MacOsVersion(15, 7, 9).trainReleaseYear().getAsInt());
		assertEquals(2025, new MacOsVersion(26, 7, 0).trainReleaseYear().getAsInt());
		assertEquals(2026, new MacOsVersion(27, 0, 0).trainReleaseYear().getAsInt());
		// and stays right for a train that does not exist yet
		assertEquals(2027, new MacOsVersion(28, 0, 0).trainReleaseYear().getAsInt());
	}

	@Test
	void hasNoYearForTheSchemeThatEncodedNone()
	{
		// given — every 10.x release shares one major number

		// when / then
		assertTrue(new MacOsVersion(10, 15, 7).trainReleaseYear().isEmpty());
		assertTrue(new MacOsVersion(10, 15, 7).trainAgeInYears(LocalDate.of(2026, 9, 16)).isEmpty());
	}

	@Test
	void agesATrainInCalendarYears()
	{
		// given — macOS 15 shipped in 2024
		MacOsVersion sequoia = new MacOsVersion(15, 7, 9);

		// when / then — two years old all through 2026, not only from autumn
		assertEquals(2, sequoia.trainAgeInYears(LocalDate.of(2026, 1, 4)).getAsInt());
		assertEquals(2, sequoia.trainAgeInYears(LocalDate.of(2026, 9, 16)).getAsInt());
		assertEquals(2, sequoia.trainAgeInYears(LocalDate.of(2026, 12, 31)).getAsInt());
		assertEquals(3, sequoia.trainAgeInYears(LocalDate.of(2027, 1, 1)).getAsInt());
	}

	@Test
	void callsThisYearsTrainNoYearsOld()
	{
		// given — a train from the year it is read in

		// when / then — and so the report prints no age mark at all
		assertEquals(0, new MacOsVersion(26, 7, 0).trainAgeInYears(LocalDate.of(2025, 12, 1)).getAsInt());
	}

	@Test
	void neverReportsANegativeAgeForATrainFromTheFuture()
	{
		// given — a train whose nominal release date has not arrived

		// when / then
		assertEquals(0, new MacOsVersion(28, 0, 0).trainAgeInYears(LocalDate.of(2026, 9, 16)).getAsInt());
	}

	@Test
	void ordersByEachNumberInTurn()
	{
		// given / when / then
		assertTrue(new MacOsVersion(26, 7, 0).compareTo(new MacOsVersion(27, 0, 0)) < 0);
		assertTrue(new MacOsVersion(27, 0, 1).compareTo(new MacOsVersion(27, 0, 0)) > 0);
		assertEquals(0, new MacOsVersion(15, 7, 9).compareTo(new MacOsVersion(15, 7, 9)));
	}

	@Test
	void printsTheWayAppleWritesIt()
	{
		// given / when / then
		assertEquals("15.7.9", new MacOsVersion(15, 7, 9).toString());
		assertEquals("26.7", new MacOsVersion(26, 7, 0).toString());
	}
}
