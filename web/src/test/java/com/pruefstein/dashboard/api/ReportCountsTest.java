package com.pruefstein.dashboard.api;

import java.util.List;

import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.LatestRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The dashboard cards count devices by their newest run, so each device lands
 * in exactly one card and the shares add up over devices, not runs.
 */
class ReportCountsTest
{
	@Test
	void eachDeviceCountsOnceUnderItsCurrentVerdict()
	{
		// given one newest run per device
		List<LatestRun> runs = List.of(
			new LatestRun(1L, "a", null, ReportStatus.COMPLIANT),
			new LatestRun(2L, "b", null, ReportStatus.COMPLIANT),
			new LatestRun(3L, "c", null, ReportStatus.MISSING),
			new LatestRun(4L, "d", null, ReportStatus.NON_COMPLIANT));

		// when
		ReportCounts counts = ReportCounts.of(runs);

		// then
		assertEquals(2, counts.compliant());
		assertEquals(1, counts.missing());
		assertEquals(1, counts.nonCompliant());
		assertEquals(0, counts.open());
		assertEquals(4, counts.total());
		assertEquals(50, counts.compliantPct());
	}

	@Test
	void anEmptyFleetHasNoShares()
	{
		assertEquals(0, ReportCounts.of(List.of()).compliantPct());
	}
}
