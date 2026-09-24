package com.pruefstein.report.service;

import com.pruefstein.report.domain.Report;
import com.pruefstein.user.web.CurrentUserBean;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

/**
 * Who may read which report: an admin reads every one, anybody else only the
 * runs filed under their own login.
 * <p>
 * One rule for every way in — the report screens and the MCP tools ask here, so
 * a reader cannot see more through one than through the other.
 */
@RequestScoped
public class ReportAccess
{
	@Inject
	CurrentUserBean currentUser;

	/**
	 * The owner to narrow a report listing to, or {@code null} for every owner.
	 * <p>
	 * null means "every owner" to the repository, which is right for an admin
	 * and a disclosure for anyone else — so an unidentifiable user gets a
	 * refusal rather than the whole estate's reports.
	 */
	public String ownerFilter()
	{
		if (currentUser.isAdmin())
		{
			return null;
		}
		String username = currentUser.getUsername();
		if (username == null)
		{
			throw new ForbiddenException();
		}
		return username;
	}

	/** Refuses a report the current user may not read. */
	public void checkReadable(Report report)
	{
		if (currentUser.isAdmin())
		{
			return;
		}
		String username = currentUser.getUsername();
		if (username == null || !username.equals(report.getKeycloakUser()))
		{
			throw new ForbiddenException();
		}
	}
}
