package com.pruefstein.shared.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaExtensionsAgoTest
{
	@Test
	void readsNullAsNever()
	{
		assertEquals("never", JavaExtensions.ago(null));
	}

	@Test
	void collapsesTheLastMinuteToJustNow()
	{
		assertEquals("just now", JavaExtensions.ago(Instant.now().minusSeconds(30)));
	}

	@Test
	void countsMinutesHoursAndDays()
	{
		Instant now = Instant.now();
		assertEquals("5 minutes ago", JavaExtensions.ago(now.minus(5, ChronoUnit.MINUTES)));
		assertEquals("3 hours ago", JavaExtensions.ago(now.minus(3, ChronoUnit.HOURS)));
		assertEquals("9 days ago", JavaExtensions.ago(now.minus(9, ChronoUnit.DAYS)));
	}

	@Test
	void dropsThePluralForOne()
	{
		Instant now = Instant.now();
		assertEquals("1 minute ago", JavaExtensions.ago(now.minus(1, ChronoUnit.MINUTES)));
		assertEquals("1 hour ago", JavaExtensions.ago(now.minus(1, ChronoUnit.HOURS)));
		assertEquals("1 day ago", JavaExtensions.ago(now.minus(1, ChronoUnit.DAYS)));
	}

	@Test
	void switchesToMonthsAndYearsOnceDaysStopHelping()
	{
		Instant now = Instant.now();
		assertEquals("30 days ago", JavaExtensions.ago(now.minus(30, ChronoUnit.DAYS)));
		assertEquals("2 months ago", JavaExtensions.ago(now.minus(75, ChronoUnit.DAYS)));
		assertEquals("2 years ago", JavaExtensions.ago(now.minus(900, ChronoUnit.DAYS)));
	}

	@Test
	void readsAFutureInstantAsJustNowRatherThanANegativeAge()
	{
		assertEquals("just now", JavaExtensions.ago(Instant.now().plus(2, ChronoUnit.HOURS)));
	}
}
