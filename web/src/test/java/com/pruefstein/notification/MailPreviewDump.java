package com.pruefstein.notification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MailTemplate.MailTemplateInstance;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Renders every mail variant to {@code target/mail-preview} so the templates
 * can be reviewed in a browser without wiring up SMTP. Doubles as a smoke test:
 * a broken expression in either template fails here.
 *
 * <p>
 * Override the output directory with {@code -Dpreview.dir=…}.
 */
@QuarkusTest
class MailPreviewDump
{
	private static final Path OUT = Path.of(System.getProperty("preview.dir", "target/mail-preview"));

	@Inject
	MockMailbox mailbox;

	@Test
	void dump() throws Exception
	{
		ReportMailData compliant = new ReportMailData(482, "MBP-C02XK1", "Markus", "COMPLIANT",
			"13 Aug 2026, 09:14", null, 0, List.of(), "https://pruefstein.example.com/Reports/show/482");

		List<ReportMailData.Failure> failures = List.of(
			new ReportMailData.Failure("FileVault Enabled", "Disk Encryption",
				"FileVault is switched off, so everything on this disk is readable if the machine is lost or stolen."),
			new ReportMailData.Failure("Screen Lock After 5 Minutes", "Device Access",
				"The screen never locks on its own — anyone walking past an unattended machine has full access."),
			new ReportMailData.Failure("No Blocked Applications Installed", "Software Inventory",
				"TeamViewer is installed. Unmanaged remote-access tools bypass the company VPN entirely."));

		ReportMailData open = new ReportMailData(483, "MBP-C02XK1", "Markus", "OPEN",
			"13 Aug 2026, 09:14", "20 Aug 2026", 7, failures,
			"https://pruefstein.example.com/Reports/show/483");

		ReportMailData reminder = new ReportMailData(483, "MBP-C02XK1", "Markus", "OPEN",
			"13 Aug 2026, 09:14", "20 Aug 2026", 2, failures,
			"https://pruefstein.example.com/Reports/show/483");

		ReportMailData nonCompliant = new ReportMailData(483, "MBP-C02XK1", "Markus", "NON_COMPLIANT",
			"18 Aug 2026, 11:02", "20 Aug 2026", 0, failures,
			"https://pruefstein.example.com/Reports/show/483");

		// No device on this one — which is what makes it an invitation
		ReportRequestMailData invite = new ReportRequestMailData("Markus", null, null,
			"27 Aug 2026", 7, 7, "https://pruefstein.example.com");

		ReportRequestMailData due = new ReportRequestMailData("Markus", "MBP-C02XK1",
			"13 Aug 2026, 09:14", "20 Aug 2026", 2, 7, "https://pruefstein.example.com");

		ReportRequestMailData overdue = new ReportRequestMailData("Markus", "MBP-C02XK1",
			"13 Aug 2026, 09:14", "20 Aug 2026", 0, 7, "https://pruefstein.example.com");

		Files.createDirectories(OUT);
		write("compliant.html", MailTemplates.reportOutcome(compliant));
		write("open.html", MailTemplates.reportOutcome(open));
		write("noncompliant.html", MailTemplates.reportOutcome(nonCompliant));
		write("reminder.html", MailTemplates.deadlineReminder(reminder));
		write("invite.html", MailTemplates.invite(invite));
		write("report-due.html", MailTemplates.reportDue(due));
		write("report-overdue.html", MailTemplates.reportDue(overdue));
	}

	private void write(String file, MailTemplateInstance template) throws Exception
	{
		mailbox.clear();
		template.to("preview@example.com").subject("preview").send().await().indefinitely();
		List<Mail> sent = mailbox.getMailsSentTo("preview@example.com");
		Files.writeString(OUT.resolve(file), sent.get(0).getHtml());
	}
}
