package com.pruefstein.notification;

/**
 * Everything the two "please report" mails render, flattened to plain values
 * for the same reason {@link ReportMailData} is: templates render after the
 * surrounding transaction has closed, so no entity may survive into one.
 *
 * @param deviceId
 *            {@code null} when nobody has ever reported for this person — which
 *            is what makes the mail an invitation rather than a reminder.
 */
public record ReportRequestMailData(
	String name,
	String deviceId,
	String lastCheckedAt,
	String dueAt,
	long daysLeft,
	long intervalDays,
	String url)
{
	/** No device yet, so the mail has to explain the agent from scratch. */
	public boolean firstReport()
	{
		return deviceId == null;
	}

	/** Past due, which the admin-triggered mail can be and the job's cannot. */
	public boolean overdue()
	{
		return daysLeft <= 0;
	}

	public String dayLabel()
	{
		return daysLeft == 1 ? "1 day" : daysLeft + " days";
	}

	public String intervalLabel()
	{
		return intervalDays == 1 ? "1 day" : intervalDays + " days";
	}
}
