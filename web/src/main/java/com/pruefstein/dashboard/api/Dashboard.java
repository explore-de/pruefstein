package com.pruefstein.dashboard.api;

import java.util.List;

import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.dashboard.service.FleetDashboard;
import com.pruefstein.dashboard.service.UserDashboard;
import com.pruefstein.onboarding.SetupManual;
import com.pruefstein.onboarding.SetupStep;
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

	@Path("/")
	public TemplateInstance index()
	{
		return currentUser.isAdmin() ? fleet() : mine();
	}

	/** The estate, for the people whose job it is. */
	private TemplateInstance fleet()
	{
		// The cards count machines by their newest run, like the charts below
		// them: a device's old verdicts are history, not its standing.
		FleetStats fleet = fleetDashboard.stats();
		return Templates.index(fleet.standings(), itemRepository.countActive(), userRepository.count(),
			fleet);
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
