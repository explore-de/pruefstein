package com.pruefstein.notification;

import java.io.IOException;
import java.io.InputStream;

import io.quarkus.mailer.MailTemplate.MailTemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mark at the top of a mail.
 *
 * <p>
 * Attached to the message rather than linked from the server: everything
 * outside {@code /q}, {@code /internal} and the logout path is behind
 * authentication, so a linked logo would send the reader's mail client to a
 * login page and draw nothing. The wordmark sits next to it in the same bar, so
 * the header still reads in a client that blocks images.
 */
@ApplicationScoped
public class MailBranding
{
	/** Referenced from the mail templates as {@code cid:pruefstein-logo}. */
	public static final String LOGO_CID = "pruefstein-logo";

	private static final Logger LOG = LoggerFactory.getLogger(MailBranding.class);

	/**
	 * The stone on its yellow ground — the app icon, which is the mark on a
	 * background.
	 */
	private static final String LOGO_RESOURCE = "web/public/static/apple-touch-icon.png";

	private final byte[] logo = readLogo();

	/**
	 * Adds the logo to a mail. A missing asset costs the mail its picture and
	 * nothing else — an unbranded invitation is still worth sending.
	 */
	public MailTemplateInstance brand(MailTemplateInstance mail)
	{
		if (logo.length == 0)
		{
			return mail;
		}
		return mail.addInlineAttachment("pruefstein.png", logo, "image/png", "<" + LOGO_CID + ">");
	}

	private static byte[] readLogo()
	{
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		try (InputStream in = loader.getResourceAsStream(LOGO_RESOURCE))
		{
			if (in == null)
			{
				LOG.warn("Mail logo {} not on the classpath — mails go out unbranded", LOGO_RESOURCE);
				return new byte[0];
			}
			return in.readAllBytes();
		}
		catch (IOException e)
		{
			LOG.warn("Could not read mail logo {} — mails go out unbranded", LOGO_RESOURCE, e);
			return new byte[0];
		}
	}
}
