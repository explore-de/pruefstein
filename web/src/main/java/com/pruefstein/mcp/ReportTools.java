package com.pruefstein.mcp;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.osversion.service.OsVersionAssessment;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.report.service.ReportAccess;
import com.pruefstein.report.service.ReportDetails;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.WrapBusinessError;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

/**
 * Reports over MCP, with the permissions of the report screens: an admin reads
 * every report, anybody else only their own — both decided by
 * {@link ReportAccess}.
 * <p>
 * Absent values are left out rather than sent as {@code null}: the output
 * schema declares each field by its type, and a client that validates against
 * it, as Claude Code does, rejects a {@code null} string.
 */
@RolesAllowed("**")
@WrapBusinessError(ForbiddenException.class)
public class ReportTools
{
	/** A listing is for finding a report, not for reading the estate. */
	private static final int MAX_LISTED = 200;

	@Inject
	ReportRepository reportRepository;

	@Inject
	ReportAccess reportAccess;

	@Inject
	ReportDetails reportDetails;

	/**
	 * @param deadline
	 *            when an open report runs out of time to be fixed; {@code null}
	 *            once it is settled
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ReportSummary(Long id, String user, String userMail, String deviceId, Instant checkedAt,
		ReportStatus status, Instant deadline, String osVersion)
	{
		static ReportSummary of(Report report)
		{
			return new ReportSummary(report.id, report.getUserName(), report.getUserMail(), report.getDeviceId(),
				report.getCheckedAt(), report.getStatus(), report.getDeadline(), report.getOsVersion());
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ReportList(List<ReportSummary> reports, boolean truncated)
	{
	}

	/**
	 * @param passed
	 *            how the row reads today — a check retired since counts as
	 *            passed
	 * @param waived
	 *            passed only because the check was retired; the device did fail
	 *            it when it reported
	 * @param output
	 *            what osquery returned on the device, as JSON rows
	 * @param explanation
	 *            a model-written explanation of a failure and how to fix it,
	 *            once the enrichment job has got to it
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CheckResult(String check, String group, String control, boolean passed, boolean waived,
		String expectedExpression, String output, String summary, String explanation)
	{
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record InstalledApp(String source, String name, String identifier, String version, String blockedBy)
	{
	}

	/**
	 * @param latestOfTrain
	 *            the newest release of the device's own train Apple had
	 *            published when the report was filed
	 * @param standing
	 *            how the reported version compares to the newest Apple had
	 *            published when the report was filed
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OsVersion(String name, String reported, String build, String latest, String latestOfTrain,
		String standing)
	{
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ReportView(ReportSummary report, OsVersion os, List<CheckResult> checks,
		CheckResult blocklist, List<InstalledApp> blockedApps, int installedAppCount)
	{
	}

	@Tool(description = "Lists compliance reports, newest first. An admin sees every user's reports, anybody "
		+ "else only their own. Status is one of OPEN (failing, still inside its fix window), COMPLIANT, "
		+ "NON_COMPLIANT (the fix window ran out) or MISSING.", structuredContent = true)
	ReportList listReports(
		@ToolArg(description = "Only reports with this status", required = false) ReportStatus status,
		@ToolArg(description = "Matches device id or user, case-insensitively", required = false) String search)
	{
		List<Report> reports = reportRepository.listFiltered(status, search, "checkedAt", "desc",
			reportAccess.ownerFilter());
		return new ReportList(reports.stream().limit(MAX_LISTED).map(ReportSummary::of).toList(),
			reports.size() > MAX_LISTED);
	}

	@Tool(description = "Reads one compliance report in full: every check with whether it passed, the condition "
		+ "it was judged by, what the device returned and an explanation of any failure; the installed apps the "
		+ "blocklist forbids; and how the macOS version compares to Apple's latest. os.standing is one of "
		+ "CURRENT, PATCH_BEHIND (newest macOS, missing a fix), MINOR_BEHIND (newest macOS, missing a feature "
		+ "update), OLDER_TRAIN_PATCHED (older macOS Apple still patches, fully patched), OLDER_TRAIN_UNPATCHED "
		+ "(older macOS Apple still patches, missing its latestOfTrain), UNSUPPORTED_TRAIN (older macOS Apple no "
		+ "longer patches) or UNKNOWN.", structuredContent = true)
	ReportView getReport(@ToolArg(description = "The report's id") Long id)
	{
		Report report = reportRepository.findById(id);
		if (report == null)
		{
			throw new ToolCallException("No report with id " + id);
		}
		reportAccess.checkReadable(report);
		ReportDetails.Details details = reportDetails.of(report);
		OsVersionAssessment os = details.os();
		return new ReportView(
			ReportSummary.of(report),
			new OsVersion(os.getName(), os.reported(), os.build(), os.latest(), os.latestOfTrain(),
				os.standing().name()),
			details.results().stream().map(ReportTools::toResult).toList(),
			details.blacklistResult() != null ? toResult(details.blacklistResult()) : null,
			details.inventory().stream()
				.filter(ReportDetails.InventoryRow::isBlocked)
				.map(row -> new InstalledApp(row.getSource(), row.getName(), row.getIdentifier(), row.getVersion(),
					row.getRuleLabel()))
				.toList(),
			details.inventory().size());
	}

	private static CheckResult toResult(ReportDetails.ResultRow row)
	{
		ComplianceItem item = row.getItem();
		return new CheckResult(item.getName(), item.getGroup() != null ? item.getGroup().getName() : null,
			item.getControl(), row.isPassed(), row.isWaived(), row.getExpression(), row.getOutput(),
			row.getAiShortDescription(), row.getAiLongExplanation());
	}
}
