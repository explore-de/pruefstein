package com.pruefstein.dashboard.api;

import java.util.List;

import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.dashboard.service.UserDashboard;
import com.pruefstein.onboarding.SetupManual;
import com.pruefstein.onboarding.SetupStep;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.report.service.ReportingSchedule;
import com.pruefstein.user.repository.UserRepository;
import com.pruefstein.user.web.CurrentUserBean;
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Path;

/**
 * Two dashboards behind one address.
 *
 * <p>
 * An admin asks how the fleet is doing, which is a question about a population.
 * Everybody else asks whether they personally have to do something, which is a
 * question about their own two machines — and the fleet's totals are not theirs
 * to see, exactly as {@link com.pruefstein.report.api.Reports#index} already
 * decides for the report list.
 */
@SuppressWarnings("unused")
@RolesAllowed("**")
public class Dashboard extends Controller
{
	@Inject
	UserRepository userRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ReportRepository reportRepository;

	@Inject
	UserDashboard userDashboard;

	@Inject
	CurrentUserBean currentUser;

	@Inject
	SetupManual manual;

	@Inject
	ReportingSchedule schedule;

	@CheckedTemplate
	public static class Templates
	{
		public static native TemplateInstance index(
			long compliantCount,
			long nonCompliantCount,
			long missingCount,
			long openCount,
			long totalCount,
			long compliantPct,
			long nonCompliantPct,
			long missingPct,
			long itemCount,
			long userCount);

		public static native TemplateInstance personal(
			List<DeviceCard> devices,
			List<SetupStep> steps,
			String manualUrl,
			String runCommand,
			String intervalLabel);
	}

	@Path("/")
	public TemplateInstance index()
	{
		return currentUser.isAdmin() ? fleet() : mine();
	}

	/** The estate, for the people whose job it is. */
	private TemplateInstance fleet()
	{
		long total = reportRepository.count();
		long compliant = reportRepository.count("status", ReportStatus.COMPLIANT);
		long nonCompliant = reportRepository.count("status", ReportStatus.NON_COMPLIANT);
		long missing = reportRepository.count("status", ReportStatus.MISSING);
		long open = reportRepository.count("status", ReportStatus.OPEN);

		long compliantPct = total > 0 ? (compliant * 100) / total : 0;
		long nonCompliantPct = total > 0 ? (nonCompliant * 100) / total : 0;
		long missingPct = total > 0 ? (missing * 100) / total : 0;

		return Templates.index(compliant, nonCompliant, missing, open, total,
			compliantPct, nonCompliantPct, missingPct,
			itemRepository.countActive(), userRepository.count());
	}

	/**
	 * The reader's own machines. An identity we cannot name is refused rather
	 * than shown everything, for the same reason the report list refuses it: a
	 * null owner reads as "every owner" one layer down.
	 */
	private TemplateInstance mine()
	{
		String username = currentUser.getUsername();
		if (username == null)
		{
			throw new ForbiddenException();
		}
		return Templates.personal(userDashboard.cardsFor(username), manual.steps(),
			manual.manualUrl(), manual.runCommand(), schedule.intervalLabel());
	}
}
