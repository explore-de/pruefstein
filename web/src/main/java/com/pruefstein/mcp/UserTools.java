package com.pruefstein.mcp;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import com.pruefstein.user.service.UserAdministration;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * People over MCP, with the permissions of the users screen: admins only, every
 * tool.
 * <p>
 * Absent values are left out rather than sent as {@code null}: the output
 * schema declares each field by its type, and a client that validates against
 * it, as Claude Code does, rejects a {@code null} string.
 */
@RolesAllowed("${pruefstein.security.admin-role:admin}")
public class UserTools
{
	@Inject
	UserRepository userRepository;

	@Inject
	UserAdministration userAdministration;

	/**
	 * @param signedIn
	 *            false for somebody an admin added who has never signed in
	 * @param latestReportId
	 *            {@code null} when nothing has ever been reported for them
	 * @param stale
	 *            the latest report is older than the reporting interval
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record UserDto(Long id, String firstname, String lastname, String mail, boolean signedIn,
		Long latestReportId, ReportStatus latestStatus, Instant latestCheckedAt, boolean stale)
	{
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record UserList(List<UserDto> users)
	{
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Outcome(String message)
	{
	}

	@Tool(description = "Lists every user with their most recent report. Admins only.", structuredContent = true)
	UserList listUsers()
	{
		return new UserList(userAdministration.overview().stream()
			.map(row -> {
				AppUser user = row.user();
				Report latest = row.latestReport();
				return new UserDto(user.id, user.getFirstname(), user.getLastname(), user.getMail(),
					row.signedIn(), latest != null ? latest.id : null, latest != null ? latest.getStatus() : null,
					latest != null ? latest.getCheckedAt() : null, row.stale());
			})
			.toList());
	}

	@Tool(description = "Adds a user and mails them the setup invite, so they can install the agent. Admins only.", structuredContent = true)
	Outcome addUser(
		@ToolArg(description = "First name") @NotBlank String firstname,
		@ToolArg(description = "Last name") @NotBlank String lastname,
		@ToolArg(description = "Email address the invite goes to") @NotBlank @Email String mail)
	{
		AppUser user = userAdministration.create(firstname, lastname, mail);
		return new Outcome("Added user " + user.id + " and sent the setup invite to " + user.getMail());
	}

	@Tool(description = "Asks a user for a fresh compliance report now, ahead of the cycle: one mail per device, "
		+ "or the setup invite when they have no device yet. Admins only.", structuredContent = true)
	Outcome requestReport(@ToolArg(description = "The user's id, as listUsers returns it") Long userId)
	{
		AppUser user = userRepository.findById(userId);
		if (user == null)
		{
			throw new ToolCallException("No user with id " + userId);
		}
		int devices = userAdministration.requestReport(user);
		if (devices == 0)
		{
			return new Outcome("No device yet — sent " + user.getMail() + " the setup invite");
		}
		String which = devices == 1 ? "their device" : devices + " devices";
		return new Outcome("Asked " + user.getMail() + " to re-check " + which);
	}
}
