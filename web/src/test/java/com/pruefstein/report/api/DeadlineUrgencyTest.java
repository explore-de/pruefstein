package com.pruefstein.report.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.pruefstein.report.domain.ReportStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeadlineUrgencyTest
{
	private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");

	private static DeadlineUrgency inDays(double days)
	{
		Instant deadline = NOW.plus((long)(days * 24 * 60), ChronoUnit.MINUTES);
		return DeadlineUrgency.of(deadline, ReportStatus.OPEN, NOW);
	}

	@Test
	void theRampRunsFromCalmToOverdue()
	{
		// given — the default remediation window is seven days, so a fresh
		// deadline starts calm and reddens as it is used up

		// when / then
		assertEquals(DeadlineUrgency.DISTANT, inDays(7));
		assertEquals(DeadlineUrgency.DISTANT, inDays(6));
		assertEquals(DeadlineUrgency.APPROACHING, inDays(5));
		assertEquals(DeadlineUrgency.APPROACHING, inDays(4));
		assertEquals(DeadlineUrgency.NEAR, inDays(3));
		assertEquals(DeadlineUrgency.NEAR, inDays(2));
		assertEquals(DeadlineUrgency.NEAR, inDays(1));
		assertEquals(DeadlineUrgency.URGENT, inDays(0.5));
	}

	@Test
	void aDeadlineThatHasPassedIsOverdue()
	{
		// given / when / then
		assertEquals(DeadlineUrgency.OVERDUE, inDays(-0.01));
		assertEquals(DeadlineUrgency.OVERDUE, inDays(-3));
	}

	@Test
	void theMomentItRunsOutCountsAsOverdue()
	{
		// given — a deadline exactly now

		// when / then — it has been reached, so it is not still pending
		assertEquals(DeadlineUrgency.OVERDUE, DeadlineUrgency.of(NOW, ReportStatus.OPEN, NOW));
	}

	@Test
	void aFinalizedReportStopsCountingDown()
	{
		// given — the deadline of a report that is no longer open is history,
		// not a countdown. Shouting at the reader about it is noise.
		Instant passed = NOW.minus(2, ChronoUnit.DAYS);

		// when / then
		assertEquals(DeadlineUrgency.NONE, DeadlineUrgency.of(passed, ReportStatus.NON_COMPLIANT, NOW));
		assertEquals(DeadlineUrgency.NONE, DeadlineUrgency.of(passed, ReportStatus.COMPLIANT, NOW));
		assertEquals(DeadlineUrgency.NONE, DeadlineUrgency.of(passed, ReportStatus.MISSING, NOW));
	}

	@Test
	void aReportWithoutADeadlineHasNoUrgency()
	{
		// when / then
		assertEquals(DeadlineUrgency.NONE, DeadlineUrgency.of(null, ReportStatus.OPEN, NOW));
	}

	@Test
	void everyStepCarriesItsOwnStyling()
	{
		// given — the colour is the whole point, so an unstyled step would be
		// a silent hole in the ramp

		// when / then
		for (DeadlineUrgency urgency : DeadlineUrgency.values())
		{
			org.junit.jupiter.api.Assertions.assertNotNull(urgency.getStyle(), urgency + " has a style");
		}
	}

	@Test
	void theHintSpellsOutWhatTheColourMeans()
	{
		// given — colour alone is not a signal anyone can read reliably

		// when / then
		assertEquals("overdue", DeadlineUrgency.hint(NOW.minus(1, ChronoUnit.DAYS), ReportStatus.OPEN, NOW));
		assertEquals("due today", DeadlineUrgency.hint(NOW.plus(3, ChronoUnit.HOURS), ReportStatus.OPEN, NOW));
		assertEquals("1 day left", DeadlineUrgency.hint(NOW.plus(30, ChronoUnit.HOURS), ReportStatus.OPEN, NOW));
		assertEquals("5 days left", DeadlineUrgency.hint(NOW.plus(5, ChronoUnit.DAYS), ReportStatus.OPEN, NOW));
	}

	@Test
	void thereIsNoHintWhenNothingIsCountingDown()
	{
		// when / then — the cell keeps its plain date and no tooltip
		assertNull(DeadlineUrgency.hint(null, ReportStatus.OPEN, NOW));
		assertNull(DeadlineUrgency.hint(NOW.plus(2, ChronoUnit.DAYS), ReportStatus.COMPLIANT, NOW));
	}
}
