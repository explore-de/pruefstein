package com.pruefstein.report.bootstrap;

import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.shared.bootstrap.SeedLedger;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives the MISSING reports filed before they carried a deadline the one they
 * broke.
 *
 * <p>
 * A MISSING report is filed by the hourly deadline job, so its
 * {@code checkedAt} lies within the hour after the due date it missed — close
 * enough to stand in for it. Ledgered, so it runs once per database; drop it
 * once every deployment has.
 */
@ApplicationScoped
public class MissingDeadlineBackfill
{
	static final String LEDGER_KEY = "report#missing-deadline-backfill";

	private static final Logger LOG = LoggerFactory.getLogger(MissingDeadlineBackfill.class);

	@Inject
	SeedLedger ledger;

	@Inject
	ReportRepository reportRepository;

	@Transactional
	void backfillOnStartup(@Observes StartupEvent event)
	{
		if (!ledger.claim(LEDGER_KEY))
		{
			return;
		}
		int updated = reportRepository.update("deadline = checkedAt where status = ?1 and deadline is null",
			ReportStatus.MISSING);
		if (updated > 0)
		{
			LOG.info("Gave {} MISSING report(s) the deadline they missed", updated);
		}
	}
}
