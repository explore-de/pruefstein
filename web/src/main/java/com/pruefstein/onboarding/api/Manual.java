package com.pruefstein.onboarding.api;

import java.util.List;

import com.pruefstein.onboarding.SetupManual;
import com.pruefstein.onboarding.SetupStep;
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

/**
 * The same walkthrough the invitation mail carries, on a page that can be
 * linked to and re-read. Open to everyone who can log in: the people who need
 * it are precisely the ones who have not reported yet.
 */
@SuppressWarnings("unused")
@RolesAllowed("**")
public class Manual extends Controller
{
	@Inject
	SetupManual setupManual;

	@CheckedTemplate
	public static class Templates
	{
		private Templates()
		{
		}

		public static native TemplateInstance index(List<SetupStep> steps, String repositoryUrl,
			String baseUrl);
	}

	public TemplateInstance index()
	{
		return Templates.index(setupManual.steps(), setupManual.repositoryUrl(), setupManual.baseUrl());
	}
}
