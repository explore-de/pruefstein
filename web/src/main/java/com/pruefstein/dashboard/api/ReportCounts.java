package com.pruefstein.dashboard.api;

import java.util.List;

import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.LatestRun;

/**
 * How many devices stand at each verdict, read off each device's newest run,
 * and what share of all devices that is.
 *
 * <p>
 * Counting every run ever filed would let a device's past failures outweigh its
 * present: a machine that was put right months ago would still count as
 * non-compliant once for every time it was.
 */
public record ReportCounts(long compliant, long nonCompliant, long missing, long open, long total)
{
	public static final ReportCounts EMPTY = new ReportCounts(0, 0, 0, 0, 0);

	public static ReportCounts of(List<LatestRun> runs)
	{
		return new ReportCounts(
			count(runs, ReportStatus.COMPLIANT),
			count(runs, ReportStatus.NON_COMPLIANT),
			count(runs, ReportStatus.MISSING),
			count(runs, ReportStatus.OPEN),
			runs.size());
	}

	private static long count(List<LatestRun> runs, ReportStatus status)
	{
		return runs.stream().filter(run -> run.status() == status).count();
	}

	public long compliantPct()
	{
		return percentOfTotal(compliant);
	}

	public long nonCompliantPct()
	{
		return percentOfTotal(nonCompliant);
	}

	public long missingPct()
	{
		return percentOfTotal(missing);
	}

	private long percentOfTotal(long count)
	{
		return total > 0 ? (count * 100) / total : 0;
	}
}
