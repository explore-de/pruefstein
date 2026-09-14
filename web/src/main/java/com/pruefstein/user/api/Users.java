package com.pruefstein.user.api;

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
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.POST;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;

@SuppressWarnings("unused")
@RolesAllowed("${pruefstein.security.admin-role:admin}")
public class Users extends Controller
{
	@Inject
	UserRepository userRepository;

	@Inject
	ReportRepository reportRepository;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRequestMailService requestMailService;

	@ConfigProperty(name = "pruefstein.compliance.reporting-interval-days", defaultValue = "7")
	int reportingIntervalDays;

	@CheckedTemplate
	public static class Templates
	{
		public static native TemplateInstance index(List<UserRow> rows);
	}

	/**
	 * One user as the list shows them, with their most recent run alongside.
	 *
	 * @param latestReport
	 *            {@code null} for a user nothing has ever reported for — a
	 *            colleague who has not installed the agent, or one added here
	 *            minutes ago. The screen says so rather than leaving the cell
	 *            blank, because "never reported" is the finding.
	 */
	public record UserRow(AppUser user, Report latestReport, boolean stale)
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
	}

	public TemplateInstance index()
	{
		List<AppUser> users = userRepository.listAll();
		Map<Long, Report> latest = reportRepository.findLatestByUser(
			users.stream().map(user -> user.id).toList());
		Instant staleBefore = Instant.now().minus(reportingIntervalDays, ChronoUnit.DAYS);
		return Templates.index(users.stream()
			.map(user -> {
				Report report = latest.get(user.id);
				boolean stale = report != null
					&& report.getCheckedAt() != null
					&& report.getCheckedAt().isBefore(staleBefore);
				return new UserRow(user, report, stale);
			})
			.toList());
	}

	@POST
	@Transactional
	public void create(
		@RestForm @NotBlank String firstname,
		@RestForm @NotBlank String lastname,
		@RestForm @Email @NotBlank String mail)
	{
		if (validationFailed())
		{
			index();
			return;
		}
		AppUser appUser = new AppUser();
		appUser.setFirstname(firstname);
		appUser.setLastname(lastname);
		appUser.setMail(mail);
		userRepository.persist(appUser);
		// A new hire cannot report until somebody tells them how, so adding
		// them here is the invitation.
		requestMailService.sendInvite(appUser);
		index();
	}

	@POST
	@Transactional
	public void update(
		@RestForm Long id,
		@RestForm @NotBlank String firstname,
		@RestForm @NotBlank String lastname,
		@RestForm @Email @NotBlank String mail)
	{
		if (validationFailed())
		{
			index();
			return;
		}
		AppUser appUser = userRepository.findById(id);
		if (appUser == null)
		{
			notFound();
			return;
		}
		appUser.setFirstname(firstname);
		appUser.setLastname(lastname);
		appUser.setMail(mail);
		index();
	}

	/**
	 * Sends the setup mail again, for the new hire who deleted it or never got
	 * it. Deliberately separate from {@code requestReport}: somebody with no
	 * device needs the instructions, not a deadline.
	 */
	@POST
	@Transactional
	public void invite(@RestForm Long id)
	{
		AppUser appUser = userRepository.findById(id);
		if (appUser == null)
		{
			notFound();
			return;
		}
		requestMailService.sendInvite(appUser);
		flash("message", "Invite sent to " + appUser.getMail());
		index();
	}

	/**
	 * Asks for a fresh run now, ahead of the cycle. One mail per device, since
	 * each machine proves itself separately — and the invite instead when there
	 * is no device to ask about.
	 */
	@POST
	@Transactional
	public void requestReport(@RestForm Long id)
	{
		AppUser appUser = userRepository.findById(id);
		if (appUser == null)
		{
			notFound();
			return;
		}
		List<Device> devices = deviceRepository.findByAppUser(appUser.id);
		if (devices.isEmpty())
		{
			requestMailService.sendInvite(appUser);
			flash("message", "No device yet — sent " + appUser.getMail() + " the setup invite");
		}
		else
		{
			devices.forEach(requestMailService::sendReportDue);
			flash("message", "Asked " + appUser.getMail() + " to re-check "
				+ (devices.size() == 1 ? "their device" : devices.size() + " devices"));
		}
		index();
	}

	@POST
	@Transactional
	public void delete(@RestForm Long id)
	{
		// Their machines outlive the account: the device rows are keyed by
		// hardware, carry the reporting history, and re-link to whoever
		// reports from them next. Cutting the owner loose is what lets the
		// account go without taking that with it.
		deviceRepository.findByAppUser(id).forEach(device -> device.setAppUser(null));
		deviceRepository.flush();
		userRepository.deleteById(id);
		index();
	}
}
