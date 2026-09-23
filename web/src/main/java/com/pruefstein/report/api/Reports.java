package com.pruefstein.report.api;

import java.time.Instant;
import java.util.List;

import com.pruefstein.compliance.domain.AppBlacklistCheck;
import com.pruefstein.compliance.domain.BlockedApp;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ComplianceResult;
import com.pruefstein.compliance.domain.InstalledApp;
import com.pruefstein.compliance.repository.BlockedAppRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.compliance.repository.InstalledAppRepository;
import com.pruefstein.compliance.service.BlacklistMatcher;
import com.pruefstein.compliance.service.CheckResolver;
import com.pruefstein.homebrew.service.HomebrewCatalog;
import com.pruefstein.osversion.service.OsVersionAssessment;
import com.pruefstein.osversion.service.OsVersionAssessor;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.user.web.CurrentUserBean;
import io.quarkiverse.renarde.Controller;
import io.quarkus.panache.common.Sort;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.QueryParam;
import org.jboss.resteasy.reactive.RestPath;

@SuppressWarnings("unused")
@RolesAllowed("**")
public class Reports extends Controller
{
	@Inject
	ReportRepository reportRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	CurrentUserBean currentUser;

	@Inject
	CheckResolver checkResolver;

	@Inject
	InstalledAppRepository installedAppRepository;

	@Inject
	HomebrewCatalog homebrewCatalog;

	@Inject
	BlockedAppRepository blockedAppRepository;

	@Inject
	BlacklistMatcher blacklistMatcher;

	@Inject
	OsVersionAssessor osVersionAssessor;

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

		public static native TemplateInstance show(Report report, List<ResultRow> results,
			ResultRow blacklistResult, List<InventoryRow> inventory, long blockedCount,
			long waivedCount, OsVersionAssessment os);
	}

	/**
	 * A result plus the pass condition its check was evaluated against —
	 * resolved here because generated checks hold no expression of their own.
	 */
	/**
	 * One installed application paired with the rule that forbids it, if any,
	 * and where to read up on it, if anywhere.
	 */
	public record InventoryRow(InstalledApp app, BlockedApp rule, String link)
	{
		public String getSource()
		{
			return app.getSource();
		}

		public String getName()
		{
			return app.getName();
		}

		public String getIdentifier()
		{
			return app.getIdentifier();
		}

		public String getVersion()
		{
			return app.getVersion();
		}

		public String getPath()
		{
			return app.getPath();
		}

		public boolean isBlocked()
		{
			return rule != null;
		}

		public String getRuleLabel()
		{
			return rule == null ? null : rule.getLabel();
		}

		/** Lowercased haystack for the client-side filter box. */
		public String getSearchText()
		{
			return (blank(app.getName()) + " " + blank(app.getIdentifier()) + " " + blank(app.getPath()))
				.toLowerCase(java.util.Locale.ROOT);
		}

		public String getSubtitle()
		{
			String identifier = app.getIdentifier();
			if (identifier != null && !identifier.isBlank())
			{
				return identifier;
			}
			return blank(app.getPath());
		}

		public String getDisplayVersion()
		{
			return app.getVersion() == null || app.getVersion().isBlank() ? "—" : app.getVersion();
		}

		private static String blank(String value)
		{
			return value == null ? "" : value;
		}

		/** Pre-filled matcher for the one-click block action. */
		public String getSuggestedPattern()
		{
			if (app.isFromHomebrew())
			{
				return app.getName();
			}
			return app.getIdentifier() != null && !app.getIdentifier().isBlank()
				? app.getIdentifier()
				: app.getName();
		}

		public String getSuggestedMatcherType()
		{
			return app.isFromHomebrew() ? "HOMEBREW" : "BUNDLE_ID";
		}
	}

	public record ResultRow(ComplianceResult result, String expression)
	{
		public ComplianceItem getItem()
		{
			return result.getItem();
		}

		public String getOutput()
		{
			return result.getOutput();
		}

		/**
		 * How the row reads today, which is not always how it was recorded: a
		 * check retired since this report was filed passes, because it is no
		 * longer a rule this device is measured by.
		 */
		public boolean isPassed()
		{
			return !result.isFailing();
		}

		/** Whether the check was retired after this report was filed. */
		public boolean isRetired()
		{
			return result.isCheckRetired();
		}

		/** When it was retired, for the note that explains the green badge. */
		public Instant getRetiredAt()
		{
			return getItem().getRetiredAt();
		}

		/**
		 * A row that reads green only because the check was retired — the one
		 * case where the badge and the recorded answer disagree, and so the one
		 * case the report has to explain.
		 */
		public boolean isWaived()
		{
			return isRetired() && !result.isPassed();
		}

		public String getAiShortDescription()
		{
			return result.getAiShortDescription();
		}

		public String getAiLongExplanation()
		{
			return result.getAiLongExplanation();
		}

		public String getExpression()
		{
			return expression;
		}
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

		// null means "every owner" to the repository, which is right for an
		// admin and a disclosure for anyone else — so an unidentifiable user
		// gets a refusal rather than the whole estate's reports.
		String ownerFilter = null;
		if (!currentUser.isAdmin())
		{
			ownerFilter = currentUser.getUsername();
			if (ownerFilter == null)
			{
				throw new ForbiddenException();
			}
		}
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
		if (!currentUser.isAdmin())
		{
			String username = currentUser.getUsername();
			if (username == null || !username.equals(report.getKeycloakUser()))
			{
				throw new ForbiddenException();
			}
		}
		List<ResultRow> rows = resultRepository.list("report", Sort.by("item.name").ascending(), report).stream()
			.map(r -> new ResultRow(r, checkResolver.resolve(r.getItem()).expression()))
			.toList();

		// The blacklist check gets its own section next to the inventory rather
		// than a row in the per-group table
		ResultRow blacklistResult = rows.stream()
			.filter(r -> r.getItem() instanceof AppBlacklistCheck)
			.findFirst()
			.orElse(null);
		List<ResultRow> results = rows.stream()
			.filter(r -> !(r.getItem() instanceof AppBlacklistCheck))
			.toList();

		List<BlockedApp> rules = blockedAppRepository.listEnabled();
		List<InventoryRow> inventory = installedAppRepository.listForReport(report).stream()
			.map(app -> new InventoryRow(app,
				blacklistMatcher.ruleFor(app.getSource(), app.getName(), app.getIdentifier(), rules),
				homebrewCatalog.linkFor(app.getSource(), app.getName()).orElse(null)))
			.toList();

		long blockedCount = inventory.stream().filter(InventoryRow::isBlocked).count();
		// Counted over every row, blacklist check included, so the note at the
		// top of the report accounts for the section below it too.
		long waivedCount = rows.stream().filter(ResultRow::isWaived).count();
		return Templates.show(report, results, blacklistResult, inventory, blockedCount, waivedCount,
			osVersionAssessor.assess(report));
	}
}
