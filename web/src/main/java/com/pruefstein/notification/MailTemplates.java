package com.pruefstein.notification;

import java.util.List;

import com.pruefstein.onboarding.SetupStep;
import io.quarkus.mailer.MailTemplate.MailTemplateInstance;
import io.quarkus.qute.CheckedTemplate;

/**
 * Type-safe bindings for the notification mails in
 * {@code src/main/resources/templates/mails}.
 */
@CheckedTemplate(basePath = "mails")
public class MailTemplates
{
	/**
	 * Sent once a report has a verdict — on upload, or when the flow finalises
	 * it.
	 */
	public static native MailTemplateInstance reportOutcome(ReportMailData report);

	/** Sent shortly before the remediation deadline of a still-open report. */
	public static native MailTemplateInstance deadlineReminder(ReportMailData report);

	/**
	 * Sent when a device's proof of compliance is about to go stale, and by the
	 * admin asking for a run out of band.
	 */
	public static native MailTemplateInstance reportDue(ReportRequestMailData request);

	/**
	 * Sent to someone nobody has ever reported for — a new hire, or a colleague
	 * who has not installed the agent yet. The steps come from
	 * {@link com.pruefstein.onboarding.SetupManual} rather than from the
	 * template, so this mail and the page in the app say the same thing.
	 */
	public static native MailTemplateInstance invite(ReportRequestMailData request,
		List<SetupStep> steps, String repositoryUrl, String manualUrl);
}
