package com.pruefstein.user.api;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.notification.ReportRequestMailService;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import com.pruefstein.user.service.PeopleImport;
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
import org.jboss.resteasy.reactive.multipart.FileUpload;

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

	@Inject
	PeopleImport peopleImport;

	@CheckedTemplate
	public static class Templates
	{
		private Templates()
		{
		}

		public static native TemplateInstance index(List<UserAdministration.UserRow> rows);

		public static native TemplateInstance importReview(String fileName, List<PeopleImport.Candidate> candidates);
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

	/**
	 * Reads whatever list of colleagues the admin has — CSV, Markdown, plain
	 * text — and shows who was found, so nobody is mailed before a person has
	 * looked at the list.
	 */
	@POST
	public TemplateInstance importFile(@RestForm FileUpload file) throws IOException
	{
		if (file == null || file.size() == 0)
		{
			flash(MESSAGE, "Choose a file with the people to invite");
			index();
			return null;
		}
		List<PeopleImport.Candidate> candidates = peopleImport.read(Files.readAllBytes(file.uploadedFile()));
		if (candidates.isEmpty())
		{
			flash(MESSAGE, "No email addresses found in " + file.fileName());
			index();
			return null;
		}
		return Templates.importReview(file.fileName(), candidates);
	}

	/**
	 * Invites the reviewed list. The three fields arrive as parallel lists, one
	 * entry per row the admin kept. Addresses already on record are skipped
	 * again here, since the list may have sat in a tab while somebody was added
	 * by hand.
	 */
	@POST
	@Transactional
	public void inviteAll(
		@RestForm List<String> firstname,
		@RestForm List<String> lastname,
		@RestForm List<String> mail)
	{
		if (mail == null || mail.isEmpty())
		{
			index();
			return;
		}
		Set<String> taken = new HashSet<>();
		userRepository.listAll().forEach(user -> {
			if (user.getMail() != null)
			{
				taken.add(user.getMail().strip().toLowerCase(Locale.ROOT));
			}
		});
		int invited = 0;
		int skipped = 0;
		for (int i = 0; i < mail.size(); i++)
		{
			String address = mail.get(i).strip();
			if (!PeopleImport.isAddress(address) || !taken.add(address.toLowerCase(Locale.ROOT)))
			{
				skipped++;
				continue;
			}
			userAdministration.create(valueAt(firstname, i), valueAt(lastname, i), address);
			invited++;
		}
		flash(MESSAGE, "Invited " + invited + (invited == 1 ? " person" : " people")
			+ (skipped > 0 ? ", skipped " + skipped + " already on record or without a valid address" : ""));
		index();
	}

	private static String valueAt(List<String> values, int i)
	{
		return values != null && i < values.size() ? values.get(i).strip() : "";
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
