package com.pruefstein.dashboard.api;

/**
 * One thing the reader has to go and fix, as their own dashboard puts it.
 *
 * @param control
 *            the ISO 27001 control family the check belongs to. Shown in small
 *            grey next to the name, because "Disk encryption · A.10
 *            Cryptography" reads as a promise somebody made to a customer where
 *            a bare check name reads as the computer being difficult.
 * @param summary
 *            the model's one-line reading of what the output actually said.
 *            {@code null} when the explanation has not been generated yet — the
 *            row still stands, it just says less.
 */
public record FailingCheck(String name, String control, String summary, String explanation)
{
	public boolean hasExplanation()
	{
		return explanation != null && !explanation.isBlank();
	}
}
