package com.pruefstein.notification;

import java.util.List;

import com.pruefstein.onboarding.SetupManual;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import io.quarkus.mailer.Attachment;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the invitation has to carry for somebody who has never seen the agent:
 * the server to report to, the source to read first, and a mark that survives a
 * client with images switched off.
 */
@QuarkusTest
class InviteMailTest
{
	private static final String ADDRESS = "invite-test@example.com";

	@Inject
	ReportRequestMailService mailService;

	@Inject
	UserRepository userRepository;

	@Inject
	SetupManual manual;

	@Inject
	MockMailbox mailbox;

	@BeforeEach
	void setUp()
	{
		mailbox.clear();
	}

	@Test
	void namesTheServerOnTheLoginCommand()
	{
		mailService.sendInvite(invitee());

		assertTrue(html().contains("pruefstein-agent login --server " + manual.baseUrl()),
			"the invitation has to say which server the agent reports to");
	}

	@Test
	void linksTheRepository()
	{
		mailService.sendInvite(invitee());

		assertTrue(html().contains(manual.repositoryUrl()));
	}

	@Test
	void pointsAtTheSameManualInTheApp()
	{
		mailService.sendInvite(invitee());

		assertTrue(html().contains(manual.manualUrl()));
	}

	/**
	 * Attached rather than linked, because everything the mail could link to is
	 * behind the login — and referenced by the content id the template uses.
	 */
	@Test
	void carriesTheLogoAsAnInlineAttachment()
	{
		mailService.sendInvite(invitee());

		Attachment logo = onlyMail().getAttachments().stream()
			.filter(Attachment::isInlineAttachment)
			.findFirst()
			.orElseThrow(() -> new AssertionError("no inline attachment on the invitation"));

		assertEquals("image/png", logo.getContentType());
		assertEquals("<" + MailBranding.LOGO_CID + ">", logo.getContentId());
		assertTrue(html().contains("cid:" + MailBranding.LOGO_CID),
			"the template has to reference the attachment it is sent with");
	}

	private AppUser invitee()
	{
		return QuarkusTransaction.requiringNew().call(() -> {
			AppUser user = new AppUser();
			user.setFirstname("Invited");
			user.setLastname("Colleague");
			user.setMail(ADDRESS);
			userRepository.persist(user);
			return user;
		});
	}

	private String html()
	{
		return onlyMail().getHtml();
	}

	private Mail onlyMail()
	{
		List<Mail> sent = mailbox.getMailsSentTo(ADDRESS);
		assertEquals(1, sent.size(), "exactly one invitation should have been sent");
		return sent.get(0);
	}
}
