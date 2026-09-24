package com.pruefstein.dashboard.api;

import java.util.List;

import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.dashboard.service.FleetDashboard;
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
	FleetDashboard fleetDashboard;

	@Inject
	CurrentUserBean currentUser;

	@Inject
	SetupManual manual;

	@Inject
	ReportingSchedule schedule;

	@CheckedTemplate
	public static class Templates
	{
		private Templates()
		{
		}

		public static native TemplateInstance index(ReportCounts reports, long itemCount, long userCount,
			FleetStats fleet);

		public static native TemplateInstance personal(
			List<DeviceCard> devices,
			List<SetupStep> steps,
			String manualUrl,
			String runCommand,
			String intervalLabel);
	}

	/**
	 * How many reports stand at each verdict, and what share of all of them
	 * that is.
	 */
	public record ReportCounts(long compliant, long nonCompliant, long missing, long open, long total)
	{
		public long compliantPct()
		{
			return percentOfTotal(compliant);
		}

		public long nonCompliantPct()
		{
			return percentOfTotal(nonCompliant);
		}

		public long missingPct()
		{
			return percentOfTotal(missing);
		}

		private long percentOfTotal(long count)
		{
			return total > 0 ? (count * 100) / total : 0;
		}
	}

	@Path("/")
	public TemplateInstance index()
	{
		return currentUser.isAdmin() ? fleet() : mine();
	}

	/** The estate, for the people whose job it is. */
	private TemplateInstance fleet()
	{
		ReportCounts reports = new ReportCounts(
			countWith(ReportStatus.COMPLIANT),
			countWith(ReportStatus.NON_COMPLIANT),
			countWith(ReportStatus.MISSING),
			countWith(ReportStatus.OPEN),
			reportRepository.count());

		return Templates.index(reports, itemRepository.countActive(), userRepository.count(),
			fleetDashboard.stats());
	}

	private long countWith(ReportStatus status)
	{
		return reportRepository.count("status", status);
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
