package com.pruefstein.shared.util;

import java.time.Duration;
import java.time.Instant;

import io.quarkus.qute.TemplateExtension;

/**
 * Add your custom Qute extension methods here.
 */
@TemplateExtension
public class JavaExtensions
{
	private JavaExtensions()
	{
	}

	/**
	 * How long ago an instant was, in the coarsest unit that still says
	 * something.
	 *
	 * <p>
	 * A compliance list is read for staleness, and "3 months ago" answers that
	 * at a glance where a date leaves the reader subtracting from today. Months
	 * and years are approximated from days — close enough for a figure whose
	 * whole job is to be read quickly.
	 *
	 * <p>
	 * A future instant reads as "just now" rather than a negative age: the only
	 * way to get one is a clock that disagrees with ours, and that is not worth
	 * a second wording.
	 */
	public static String ago(Instant instant)
	{
		if (instant == null)
		{
			return "never";
		}
		Duration elapsed = Duration.between(instant, Instant.now());
		long minutes = elapsed.toMinutes();
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return plural(minutes, "minute");
		}
		long hours = elapsed.toHours();
		if (hours < 24)
		{
			return plural(hours, "hour");
		}
		long days = elapsed.toDays();
		if (days < 31)
		{
			return plural(days, "day");
		}
		if (days < 365)
		{
			return plural(days / 30, "month");
		}
		return plural(days / 365, "year");
	}

	private static String plural(long count, String unit)
	{
		return count + " " + unit + (count == 1 ? "" : "s") + " ago";
	}

	/**
	 * This registers the String.capitalise extension method
	 */
	public static String capitalise(String string)
	{
		StringBuilder sb = new StringBuilder();
		for (String part : string.split("\\s+"))
		{
			if (!sb.isEmpty())
			{
				sb.append(" ");
			}
			if (!part.isEmpty())
			{
				sb.append(part.substring(0, 1).toUpperCase());
				sb.append(part.substring(1));
			}
		}
		return sb.toString();
	}
}
