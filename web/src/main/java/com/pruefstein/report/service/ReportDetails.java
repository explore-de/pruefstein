package com.pruefstein.report.service;

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
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Everything a single report says, read the way a person reads it: each check
 * with the condition it was judged by, the inventory against the blocklist, and
 * the operating system against Apple's releases.
 * <p>
 * Shared by the report page and the MCP tools, so both explain a report the
 * same way. Does not check who is asking — see {@link ReportAccess}.
 */
@ApplicationScoped
public class ReportDetails
{
	@Inject
	ComplianceResultRepository resultRepository;

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

	/**
	 * @param blacklistResult
	 *            the blocklist check, kept out of {@code results} because it
	 *            reads as a section of its own; {@code null} if it did not run
	 * @param waivedCount
	 *            rows that read green only because their check was retired,
	 *            blacklist check included
	 */
	public record Details(List<ResultRow> results, ResultRow blacklistResult, List<InventoryRow> inventory,
		long blockedCount, long waivedCount, OsVersionAssessment os)
	{
	}

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

	/**
	 * A result plus the pass condition its check was evaluated against —
	 * resolved here because generated checks hold no expression of their own.
	 */
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

	public Details of(Report report)
	{
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
		return new Details(results, blacklistResult, inventory, blockedCount, waivedCount,
			osVersionAssessor.assess(report));
	}
}
