package com.pruefstein.user.api;

import java.util.List;

import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.notification.ReportRequestMailService;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import com.pruefstein.user.service.UserAdministration;
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.POST;
import org.jboss.resteasy.reactive.RestForm;

@SuppressWarnings("unused")
@RolesAllowed("${pruefstein.security.admin-role:admin}")
public class Users extends Controller
{
	private static final String MESSAGE = "message";

	@Inject
	UserRepository userRepository;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	ReportRequestMailService requestMailService;

	@Inject
	UserAdministration userAdministration;

	@CheckedTemplate
	public static class Templates
	{
		private Templates()
		{
		}

		public static native TemplateInstance index(List<UserAdministration.UserRow> rows);
	}

	public TemplateInstance index()
	{
		return Templates.index(userAdministration.overview());
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
		userAdministration.create(firstname, lastname, mail);
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
		flash(MESSAGE, "Invite sent to " + appUser.getMail());
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
		int devices = userAdministration.requestReport(appUser);
		if (devices == 0)
		{
			flash(MESSAGE, "No device yet — sent " + appUser.getMail() + " the setup invite");
		}
		else
		{
			flash(MESSAGE, "Asked " + appUser.getMail() + " to re-check "
				+ (devices == 1 ? "their device" : devices + " devices"));
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
