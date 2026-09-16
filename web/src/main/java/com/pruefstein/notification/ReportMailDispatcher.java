package com.pruefstein.notification;

import java.time.Duration;
import java.time.Instant;

import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.repository.ReportRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides when a report's outcome mail is allowed to go out.
 *
 * <p>
 * The mail's whole value is the per-check explanation, and explanations are
 * written after the report is stored rather than during the request, so firing
 * the mail the moment a status is decided sends a list of failed check names
 * and nothing else. A report with unexplained failures therefore waits, and the
 * enrichment job sends it once they are explained.
 *
 * <p>
 * Bounded by a grace period, because "wait for the explanations" must not mean
 * "never send it": a model that stays unreachable, or a check whose output it
 * refuses, would otherwise silence the notification entirely. Late and plain
 * beats absent.
 */
@ApplicationScoped
public class ReportMailDispatcher
{
	private static final Logger LOG = LoggerFactory.getLogger(ReportMailDispatcher.class);

	@Inject
	Event<ReportMailTrigger> mailTrigger;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	ReportRepository reportRepository;

	/** How long a mail waits for explanations before going out without them. */
	@ConfigProperty(name = "pruefstein.compliance.mail-explanation-grace", defaultValue = "5m")
	Duration grace;

	/**
	 * Asks for the outcome mail of a report whose status has just been decided.
	 * Sent straight away when there is nothing left to explain — a compliant
	 * report, or one whose failures already carry explanations — and held back
	 * otherwise.
	 *
	 * <p>
	 * Callers must already be in a transaction.
	 */
	public void request(Report report)
	{
		if (unexplainedFailures(report) == 0)
		{
			mailTrigger.fire(new ReportMailTrigger(report.id));
			return;
		}
		report.setMailPendingSince(Instant.now());
		LOG.debug("Holding the outcome mail for report {} until its failed checks are explained",
			(Object)report.id);
	}

	/**
	 * Sends the mails that were waiting and are now ready. Driven by the
	 * enrichment job, which is what makes them ready in the first place.
	 */
	@Transactional
	public void sendReady()
	{
		for (Report report : reportRepository.list("mailPendingSince is not null"))
		{
			long unexplained = unexplainedFailures(report);
			boolean waitedLongEnough = report.getMailPendingSince().plus(grace).isBefore(Instant.now());

			if (unexplained > 0 && !waitedLongEnough)
			{
				continue;
			}
			if (unexplained > 0)
			{
				LOG.warn("Sending the outcome mail for report {} with {} unexplained failed check(s) "
					+ "after waiting {}", report.id, unexplained, grace);
			}

			report.setMailPendingSince(null);
			mailTrigger.fire(new ReportMailTrigger(report.id));
		}
	}

	/**
	 * Deliberately the same predicate the enrichment job selects on: a failed
	 * check with no output is never going to be explained, so counting it here
	 * would hold the mail for the full grace period every time.
	 */
	private long unexplainedFailures(Report report)
	{
		return resultRepository.count(
			"report = ?1 and passed = false and item.retiredAt is null"
				+ " and aiShortDescription is null and output is not null",
			report);
	}
}
