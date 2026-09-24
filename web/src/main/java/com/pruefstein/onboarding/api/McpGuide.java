package com.pruefstein.onboarding.api;

import com.pruefstein.onboarding.McpManual;
import com.pruefstein.onboarding.SetupManual;
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

/**
 * How to reach Prüfstein from an AI assistant. Open to everyone who can log in,
 * like the reporting walkthrough: every user has tools to call, and the page
 * says which ones only an admin gets.
 */
@SuppressWarnings("unused")
@RolesAllowed("**")
public class McpGuide extends Controller
{
	@Inject
	SetupManual setupManual;

	@Inject
	McpManual mcpManual;

	@CheckedTemplate
	public static class Templates
	{
		public static native TemplateInstance index(McpManual mcp, String brewCommand, String loginCommand);
	}

	public TemplateInstance index()
	{
		return Templates.index(mcpManual, setupManual.steps().get(0).command(),
			setupManual.steps().get(1).command());
	}
}
