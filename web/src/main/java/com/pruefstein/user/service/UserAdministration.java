package com.pruefstein.user.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.notification.ReportRequestMailService;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * What an admin does to a person on the users screen, kept out of the screen so
 * the MCP tools do exactly the same. Does not check who is asking; the callers
 * carry the admin role requirement.
 */
@ApplicationScoped
public class UserAdministration
{
	@Inject
	UserRepository userRepository;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRequestMailService requestMailService;

	@Inject
	ReportRepository reportRepository;

	@ConfigProperty(name = "pruefstein.compliance.reporting-interval-days", defaultValue = "7")
	int reportingIntervalDays;

	/**
	 * One user as the list shows them, with their most recent run alongside.
	 *
	 * @param latestReport
	 *            {@code null} for a user nothing has ever reported for — a
	 *            colleague who has not installed the agent, or one added here
	 *            minutes ago. The screen says so rather than leaving the cell
	 *            blank, because "never reported" is the finding.
	 * @param signedIn
	 *            whether this person has ever authenticated. Only
	 *            {@code create} leaves a row without a subject, so a false here
	 *            means an admin typed them in and nothing has happened since —
	 *            which is a different problem from somebody who signed in and
	 *            never ran the agent, and wants chasing differently.
	 */
	public record UserRow(AppUser user, Report latestReport, boolean stale, boolean signedIn)
	{
		public AppUser getUser()
		{
			return user;
		}

		public Report getLatestReport()
		{
			return latestReport;
		}

		/**
		 * The run is older than the interval everyone reports on, so it no
		 * longer proves much. The verdict on the report itself is left alone —
		 * a pass stays a pass — because what went stale is the evidence, not
		 * the finding.
		 */
		public boolean isStale()
		{
			return stale;
		}

		public boolean isSignedIn()
		{
			return signedIn;
		}
	}

	/** Everyone on record, each with their most recent run alongside. */
	public List<UserRow> overview()
	{
		List<AppUser> users = userRepository.listAll();
		Map<Long, Report> latest = reportRepository.findLatestByUser(
			users.stream().map(user -> user.id).toList());
		Instant staleBefore = Instant.now().minus(reportingIntervalDays, ChronoUnit.DAYS);
		return users.stream()
			.map(user -> {
				Report report = latest.get(user.id);
				boolean stale = report != null
					&& report.getCheckedAt() != null
					&& report.getCheckedAt().isBefore(staleBefore);
				return new UserRow(user, report, stale, user.getOidcSubject() != null);
			})
			.toList();
	}

	@Transactional
	public AppUser create(String firstname, String lastname, String mail)
	{
		AppUser appUser = new AppUser();
		appUser.setFirstname(firstname);
		appUser.setLastname(lastname);
		appUser.setMail(mail);
		userRepository.persist(appUser);
		// A new hire cannot report until somebody tells them how, so adding
		// them here is the invitation.
		requestMailService.sendInvite(appUser);
		return appUser;
	}

	/**
	 * Asks for a fresh run now, ahead of the cycle. One mail per device, since
	 * each machine proves itself separately — and the invite instead when there
	 * is no device to ask about.
	 *
	 * @return how many devices were asked; {@code 0} means the invite went out
	 *         instead
	 */
	@Transactional
	public int requestReport(AppUser appUser)
	{
		List<Device> devices = deviceRepository.findByAppUser(appUser.id);
		if (devices.isEmpty())
		{
			requestMailService.sendInvite(appUser);
		}
		else
		{
			devices.forEach(requestMailService::sendReportDue);
		}
		return devices.size();
	}
}
