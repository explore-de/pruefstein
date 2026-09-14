package com.pruefstein.report.api;

import java.time.Duration;
import java.time.Instant;

import com.pruefstein.report.domain.ReportStatus;

/**
 * How loudly the index should say that a remediation window is running out. A
 * presentation concern rather than a domain one — nothing is decided by it, it
 * only decides how red the deadline cell goes.
 *
 * <p>
 * The steps are cut for the default seven-day window: a fresh deadline starts
 * calm and reddens across the week. A longer window configured in
 * {@code pruefstein.compliance.remediation-days} simply stays calm for longer.
 */
public enum DeadlineUrgency
{
	/**
	 * Nothing is counting down: no deadline, or the report is already closed.
	 */
	NONE("text-stone-500"),

	DISTANT("text-stone-500"),

	APPROACHING("text-red-500 font-bold"),

	NEAR("text-red-600 font-black bg-red-100 px-1.5 py-0.5"),

	URGENT("text-white bg-red-500 font-black px-1.5 py-0.5"),

	OVERDUE("text-white bg-red-600 font-black px-1.5 py-0.5 border-2 border-black");

	private final String style;

	DeadlineUrgency(String style)
	{
		this.style = style;
	}

	public String getStyle()
	{
		return style;
	}

	/**
	 * A finalized report's deadline is history rather than a countdown, so only
	 * an open one reddens. The clock is read at render time, which is what the
	 * reader is actually asking about when they look at the column.
	 */
	public static DeadlineUrgency of(Instant deadline, ReportStatus status, Instant now)
	{
		if (deadline == null || status != ReportStatus.OPEN)
		{
			return NONE;
		}
		if (!deadline.isAfter(now))
		{
			return OVERDUE;
		}
		long days = Duration.between(now, deadline).toDays();
		if (days < 1)
		{
			return URGENT;
		}
		if (days <= 3)
		{
			return NEAR;
		}
		if (days <= 5)
		{
			return APPROACHING;
		}
		return DISTANT;
	}

	/**
	 * What the colour means, in words, for the cell's tooltip. Colour on its
	 * own is not a signal every reader can pick up.
	 *
	 * @return {@code null} when nothing is counting down
	 */
	public static String hint(Instant deadline, ReportStatus status, Instant now)
	{
		if (of(deadline, status, now) == NONE)
		{
			return null;
		}
		if (!deadline.isAfter(now))
		{
			return "overdue";
		}
		long days = Duration.between(now, deadline).toDays();
		if (days < 1)
		{
			return "due today";
		}
		return days == 1 ? "1 day left" : days + " days left";
	}
}
