package com.pruefstein.notification;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import com.pruefstein.device.domain.Device;
import com.pruefstein.user.domain.AppUser;
import io.quarkus.mailer.MailTemplate.MailTemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asks somebody to run the agent.
 *
 * <p>
 * Two mails with one job between them. The invitation goes to a person nobody
 * has ever reported for and has to explain the agent; the reminder goes to a
 * device whose proof is about to go stale and only has to say when. Which one
 * is sent follows from whether there is a device, so callers never choose.
 *
 * <p>
 * Sending is fire-and-forget, as in {@link ReportMailService}: a mail server
 * that is down must not fail the job or the admin's click.
 */
@ApplicationScoped
public class ReportRequestMailService
{
	private static final Logger LOG = LoggerFactory.getLogger(ReportRequestMailService.class);

	private static final DateTimeFormatter DATE = DateTimeFormatter
		.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
		.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

	@Inject
	ReportRequestMailService.Sender sender;

	@ConfigProperty(name = "pruefstein.web.base-url")
	String baseUrl;

	@ConfigProperty(name = "pruefstein.compliance.reporting-interval-days", defaultValue = "7")
	int reportingIntervalDays;

	/**
	 * The recurring ask, for a device that has reported before. The due date is
	 * the one the periodic job works to, so the mail and the job cannot
	 * disagree about when the report stops counting.
	 */
	public void sendReportDue(Device device)
	{
		recipient(device.getAppUser(), device.getKeycloakUser()).ifPresent(address -> {
			Instant dueAt = device.getLastReportAt() != null
				? device.getLastReportAt().plus(reportingIntervalDays, ChronoUnit.DAYS)
				: Instant.now();
			ReportRequestMailData data = new ReportRequestMailData(
				firstName(device.getAppUser(), device.getKeycloakUser()),
				device.getDeviceId(),
				device.getLastReportAt() != null ? DATE_TIME.format(device.getLastReportAt()) : null,
				DATE.format(dueAt),
				daysUntil(dueAt),
				reportingIntervalDays,
				baseUrl);
			String subject = data.overdue()
				? "Prüfstein: %s is overdue for a compliance report".formatted(device.getDeviceId())
				: "Prüfstein: time to re-check %s — %s left".formatted(device.getDeviceId(), data.dayLabel());
			sender.send(address, subject, MailTemplates.reportDue(data));
		});
	}

	/**
	 * The first ask, for somebody with no device on record. The due date is a
	 * full interval out: the clock on a new hire starts when they are told, not
	 * when the account was typed in.
	 */
	public void sendInvite(AppUser user)
	{
		recipient(user, null).ifPresent(address -> {
			Instant dueAt = Instant.now().plus(reportingIntervalDays, ChronoUnit.DAYS);
			ReportRequestMailData data = new ReportRequestMailData(
				firstName(user, null),
				null,
				null,
				DATE.format(dueAt),
				daysUntil(dueAt),
				reportingIntervalDays,
				baseUrl);
			sender.send(address, "Prüfstein: set up your device compliance check",
				MailTemplates.invite(data));
		});
	}

	private Optional<String> recipient(AppUser user, String fallbackLabel)
	{
		if (user == null || user.getMail() == null || user.getMail().isBlank())
		{
			LOG.warn("No mail address for {} — report request skipped",
				fallbackLabel != null ? fallbackLabel : "user");
			return Optional.empty();
		}
		return Optional.of(user.getMail());
	}

	private static String firstName(AppUser user, String fallback)
	{
		if (user != null && user.getFirstname() != null && !user.getFirstname().isBlank())
		{
			return user.getFirstname();
		}
		return fallback;
	}

	/** Rounded up, so a due date 47 hours out still reads as "2 days". */
	private static long daysUntil(Instant dueAt)
	{
		long hours = Duration.between(Instant.now(), dueAt).toHours();
		return Math.max(0, (long)Math.ceil(hours / 24.0));
	}

	/**
	 * The handover to SMTP, behind a bean so a test can watch what was sent
	 * without standing up a mail server.
	 */
	@ApplicationScoped
	public static class Sender
	{
		public void send(String address, String subject, MailTemplateInstance mail)
		{
			mail.to(address).subject(subject).send().subscribe().with(
				ignored -> LOG.info("Mailed {}: {}", address, subject),
				failure -> LOG.error("Failed to mail {}: {}", address, subject, failure));
		}
	}
}
