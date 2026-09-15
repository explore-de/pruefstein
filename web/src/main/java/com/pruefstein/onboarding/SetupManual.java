package com.pruefstein.onboarding;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The agent walkthrough, written once and rendered twice: the invitation mail
 * asks a new colleague to follow it, and the page behind {@code /Manual/index}
 * is where they find it again once that mail is buried. Two copies of the same
 * three commands would disagree the first time one of them changed.
 */
@ApplicationScoped
public class SetupManual
{
	@ConfigProperty(name = "pruefstein.web.base-url")
	String baseUrl;

	@ConfigProperty(name = "pruefstein.project.repository-url")
	String repositoryUrl;

	/**
	 * The three commands, in the order they have to be run. Login names this
	 * server outright rather than relying on a default: the agent ships with no
	 * server configured, and the URL it is handed the first time is the one it
	 * keeps.
	 */
	public List<SetupStep> steps()
	{
		return List.of(
			new SetupStep("Install the agent", "./agent/bin/install.sh",
				"Clone the repository and run this from its root. It builds the agent if needed "
					+ "and puts pruefstein-agent on your PATH."),
			new SetupStep("Sign in, once", "pruefstein-agent login --server " + baseUrl(),
				"--server (short: -s) names the Prüfstein server you report to. It is stored "
					+ "alongside your credentials and reused by every later run, so this is the "
					+ "only time you pass it."),
			new SetupStep("Run the checks", "pruefstein-agent run",
				"It prints what each check found, then asks whether to send the report. Nothing "
					+ "reaches the server until you answer yes."));
	}

	public String repositoryUrl()
	{
		return repositoryUrl;
	}

	/** A trailing slash would read as a typo in the middle of a command. */
	public String baseUrl()
	{
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	/**
	 * Where this walkthrough lives in the app. Built by concatenation like the
	 * report links in the mails are: a mail is composed off any request, so
	 * there is no router to ask.
	 */
	public String manualUrl()
	{
		return baseUrl() + "/Manual/index";
	}
}
