package com.pruefstein.dashboard.api;

import java.time.Instant;
import java.util.List;

import com.pruefstein.report.domain.ReportStatus;

/**
 * One machine, as the person who owns it needs to see it: a verdict, a list of
 * things to do about it, and the date the next run is wanted.
 *
 * <p>
 * Deliberately not a {@link com.pruefstein.report.domain.Report}: the page asks
 * questions a report cannot answer on its own ("how many of my checks pass",
 * "when is the next one due"), and a template that walked the entity would be
 * reading lazy collections after the transaction closed.
 *
 * @param reportId
 *            {@code null} when this device has never reported, which is the
 *            state the setup instructions are for.
 */
public record DeviceCard(
	String deviceId,
	Long reportId,
	ReportStatus status,
	Instant checkedAt,
	Instant deadline,
	int passed,
	int total,
	List<FailingCheck> failures,
	Instant dueAt,
	long daysUntilDue,
	boolean overdue)
{
	public boolean neverReported()
	{
		return reportId == null;
	}

	/** Nothing to do: everything passed, and the window has not run out. */
	public boolean isClear()
	{
		return status == ReportStatus.COMPLIANT;
	}

	/**
	 * Something failed and the repair window is still open — the one state the
	 * page exists to act on.
	 */
	public boolean isActionable()
	{
		return status == ReportStatus.OPEN;
	}

	/** The window ran out, or nobody ever answered. Still fixable, but late. */
	public boolean isLate()
	{
		return status == ReportStatus.NON_COMPLIANT || status == ReportStatus.MISSING;
	}

	public int failureCount()
	{
		return failures.size();
	}

	/**
	 * Whole percent, floored, so a run with one failure never reads as 100%.
	 */
	public int passedPct()
	{
		return total == 0 ? 0 : (int)((passed * 100L) / total);
	}

	public String dueLabel()
	{
		return daysUntilDue == 1 ? "1 day" : daysUntilDue + " days";
	}

	public String failureLabel()
	{
		return failureCount() == 1 ? "1 check needs fixing" : failureCount() + " checks need fixing";
	}
}
