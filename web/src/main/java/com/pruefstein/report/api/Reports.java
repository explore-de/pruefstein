package com.pruefstein.report.api;

import java.util.List;

import com.pruefstein.osversion.service.OsVersionAssessment;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.report.service.ReportAccess;
import com.pruefstein.report.service.ReportDetails;
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;
import org.jboss.resteasy.reactive.RestPath;

@SuppressWarnings("unused")
@RolesAllowed("**")
public class Reports extends Controller
{
	@Inject
	ReportRepository reportRepository;

	@Inject
	ReportAccess reportAccess;

	@Inject
	ReportDetails reportDetails;

	@CheckedTemplate
	public static class Templates
	{
		public static native TemplateInstance index(
			List<ReportGroup> groups,
			String statusFilter,
			String q,
			String sort,
			String dir,
			boolean allRuns);

		public static native TemplateInstance show(Report report, List<ReportDetails.ResultRow> results,
			ReportDetails.ResultRow blacklistResult, List<ReportDetails.InventoryRow> inventory, long blockedCount,
			long waivedCount, OsVersionAssessment os);
	}

	public TemplateInstance index(
		@QueryParam("status") String statusParam,
		@QueryParam("q") String q,
		@QueryParam("sort") String sort,
		@QueryParam("dir") String dir,
		@QueryParam("all") String allParam)
	{
		ReportStatus statusFilter = null;
		if (statusParam != null && !statusParam.isBlank())
		{
			try
			{
				statusFilter = ReportStatus.valueOf(statusParam);
			}
			catch (IllegalArgumentException ignored)
			{
			}
		}

		String activeStatus = statusFilter != null ? statusFilter.name() : "";
		String activeQ = q != null ? q : "";
		String activeSort = sort != null ? sort : "checkedAt";
		String activeDir = dir != null ? dir : "desc";
		// A missing parameter is an unticked box, and the box is the one the
		// page opens with — so judging a user by their latest run only is what
		// a plain /Reports/index means.
		boolean allRuns = "1".equals(allParam);

		String ownerFilter = reportAccess.ownerFilter();
		// Held back from the query while only latest runs count, so the group
		// is built from the user's whole history and the status is read off
		// the run that is actually current. Pushed into the query otherwise,
		// which is what makes "latest" mean the latest matching run instead.
		List<Report> reports = reportRepository.listFiltered(
			allRuns ? statusFilter : null, activeQ, activeSort, activeDir, ownerFilter);
		// Grouped after filtering, so "latest" means the latest run the reader
		// asked to see rather than one the filter just took off the page.
		List<ReportGroup> groups = ReportGroup.group(reports);
		if (!allRuns && statusFilter != null)
		{
			// Applied to the group rather than the run: a user who has since
			// put their machine right is no longer non-compliant, however many
			// failing runs are behind them. Their earlier runs stay in the
			// fold of the groups that do survive, because that history is what
			// the fold is for.
			ReportStatus wanted = statusFilter;
			groups = groups.stream().filter(group -> group.latest().getStatus() == wanted).toList();
		}
		return Templates.index(groups, activeStatus, activeQ, activeSort, activeDir, allRuns);
	}

	public TemplateInstance show(@RestPath Long id)
	{
		Report report = reportRepository.findById(id);
		if (report == null)
		{
			notFound();
			return null;
		}
		reportAccess.checkReadable(report);
		ReportDetails.Details details = reportDetails.of(report);
		return Templates.show(report, details.results(), details.blacklistResult(), details.inventory(),
			details.blockedCount(), details.waivedCount(), details.os());
	}
}
