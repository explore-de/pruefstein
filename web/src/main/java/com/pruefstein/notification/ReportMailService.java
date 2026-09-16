package com.pruefstein.notification;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.pruefstein.compliance.domain.ComplianceResult;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.user.domain.AppUser;
import io.quarkus.mailer.MailTemplate.MailTemplateInstance;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders and sends the two report notification mails.
 *
 * <p>
 * Sending is fire-and-forget: a mail server that is down must never fail a
 * report upload or a scheduled job, so failures are logged and nothing is
 * retried.
 */
@ApplicationScoped
public class ReportMailService
{
	private static final Logger LOG = LoggerFactory.getLogger(ReportMailService.class);

	private static final DateTimeFormatter DATE = DateTimeFormatter
		.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
		.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

	@Inject
	ReportRepository reportRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@ConfigProperty(name = "pruefstein.web.base-url")
	String baseUrl;

	/**
	 * Tells the reporting user whether their device came out compliant. Runs in
	 * its own transaction because the callers observe
	 * {@code TransactionPhase.AFTER_SUCCESS} — by then the triggering
	 * transaction is gone.
	 */
	@Transactional(Transactional.TxType.REQUIRES_NEW)
	public void sendOutcomeMail(long reportId)
	{
		Report report = reportRepository.findById(reportId);
		if (report == null)
		{
			LOG.warn("Report {} vanished before its outcome mail could be sent", reportId);
			return;
		}
		recipient(report).ifPresent(address -> {
			ReportMailData data = describe(report);
			send(address, outcomeSubject(data), MailTemplates.reportOutcome(data), reportId);
		});
	}

	/**
	 * Warns that an open report is about to hit its deadline. Called from the
	 * scheduler's transaction, so the report is still attached.
	 */
	public void sendDeadlineReminder(Report report)
	{
		recipient(report).ifPresent(address -> {
			ReportMailData data = describe(report);
			String subject = "Prüfstein: %s left to fix %s on %s"
				.formatted(data.dayLabel(), data.failureLabel(), data.deviceId());
			send(address, subject, MailTemplates.deadlineReminder(data), report.id);
		});
	}

	private String outcomeSubject(ReportMailData data)
	{
		if (data.compliant())
		{
			return "Prüfstein: %s is compliant".formatted(data.deviceId());
		}
		if (data.nonCompliant())
		{
			return "Prüfstein: %s is marked non-compliant".formatted(data.deviceId());
		}
		return "Prüfstein: %s failed on %s — fix by %s"
			.formatted(data.failureLabel(), data.deviceId(), data.deadline());
	}

	private Optional<String> recipient(Report report)
	{
		AppUser user = report.getAppUser();
		if (user == null || user.getMail() == null || user.getMail().isBlank())
		{
			LOG.warn("No mail address on report {} ({}) — notification skipped",
				report.id, report.getKeycloakUser());
			return Optional.empty();
		}
		return Optional.of(user.getMail());
	}

	private ReportMailData describe(Report report)
	{
		List<ReportMailData.Failure> failures = resultRepository
			.list("report = ?1 and passed = false and item.retiredAt is null",
				Sort.by("item.name").ascending(), report)
			.stream()
			.map(ReportMailService::toFailure)
			.toList();

		AppUser user = report.getAppUser();
		String name = user != null && user.getFirstname() != null && !user.getFirstname().isBlank()
			? user.getFirstname()
			: report.getKeycloakUser();

		Instant deadline = report.getDeadline();
		return new ReportMailData(
			report.id,
			report.getDeviceId(),
			name,
			report.getStatus().name(),
			report.getCheckedAt() != null ? DATE_TIME.format(report.getCheckedAt()) : null,
			deadline != null ? DATE.format(deadline) : null,
			daysUntil(deadline),
			failures,
			baseUrl + "/Reports/show/" + report.id);
	}

	private static ReportMailData.Failure toFailure(ComplianceResult result)
	{
		return new ReportMailData.Failure(
			result.getItem().getName(),
			result.getItem().getGroup() != null ? result.getItem().getGroup().getName() : null,
			result.getAiShortDescription());
	}

	/** Rounded up, so a deadline 47 hours out still reads as "2 days". */
	private static long daysUntil(Instant deadline)
	{
		if (deadline == null)
		{
			return 0;
		}
		long hours = Duration.between(Instant.now(), deadline).toHours();
		return Math.max(0, (long)Math.ceil(hours / 24.0));
	}

	private void send(String address, String subject, MailTemplateInstance mail, long reportId)
	{
		mail.to(address).subject(subject).send().subscribe().with(
			ignored -> LOG.info("Mailed report {} to {}: {}", reportId, address, subject),
			failure -> LOG.error("Failed to mail report {} to {}", reportId, address, failure));
	}
}
