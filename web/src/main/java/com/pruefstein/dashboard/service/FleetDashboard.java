package com.pruefstein.dashboard.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.compliance.repository.ItemFailureCount;
import com.pruefstein.dashboard.api.FleetStats;
import com.pruefstein.dashboard.api.VersionShare;
import com.pruefstein.dashboard.api.ViolationShare;
import com.pruefstein.osversion.domain.MacOsVersion;
import com.pruefstein.osversion.domain.OsVersionStanding;
import com.pruefstein.osversion.service.MacOsReleaseCatalog;
import com.pruefstein.report.repository.LatestRun;
import com.pruefstein.report.repository.ReportRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Builds the estate's two charts: what the fleet is running, and what it is
 * failing.
 *
 * <p>
 * The counterpart of {@link UserDashboard}, and the opposite question. One
 * person asks about their own machines; an admin asks about a population, and a
 * population is only readable as a distribution.
 *
 * <p>
 * Every number here is counted over the newest run of every device — see
 * {@link ReportRepository#findLatestPerDevice()} for why that, and not every
 * run ever filed, is the honest denominator.
 */
@ApplicationScoped
public class FleetDashboard
{
	/**
	 * Version rows before the tail is folded. Generous, because the categories
	 * are ordered and folding loses that order: a fleet with a dozen versions
	 * on it should show a dozen bars and let the reader see the drift.
	 */
	private static final int MAX_VERSION_BARS = 12;

	/**
	 * Violation rows. A short list on purpose — the chart exists to name what
	 * to fix first, and a ranking nobody reads past the top of is not a
	 * ranking. The full list lives on the reports screens.
	 */
	private static final int MAX_VIOLATION_BARS = 8;

	@Inject
	ReportRepository reportRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	MacOsReleaseCatalog catalog;

	public FleetStats stats()
	{
		List<LatestRun> runs = reportRepository.findLatestPerDevice();
		if (runs.isEmpty())
		{
			return FleetStats.empty();
		}
		return new FleetStats(runs.size(), versions(runs), violations(runs));
	}

	/**
	 * The version distribution, newest release first.
	 *
	 * <p>
	 * Ordered by version rather than by size because the categories have an
	 * order of their own: read top to bottom, the chart shows how far the
	 * estate has drifted from the current release, which a bar chart sorted by
	 * count would scramble.
	 *
	 * <p>
	 * Standing is measured against the newest release Apple has published
	 * <em>today</em>, unlike a single report, which is judged against what was
	 * newest when it was filed. A report is a statement about a day and must
	 * not change afterwards; this chart is a statement about right now, and a
	 * machine that was current last month but is not current today belongs in
	 * amber.
	 */
	private List<VersionShare> versions(List<LatestRun> runs)
	{
		Map<String, Long> counts = new LinkedHashMap<>();
		long unknown = 0;
		for (LatestRun run : runs)
		{
			String version = run.osVersion();
			if (version == null || version.isBlank())
			{
				unknown++;
				continue;
			}
			counts.merge(version.trim(), 1L, Long::sum);
		}

		Optional<MacOsVersion> latest = catalog.latestPublicVersion();
		Map<Integer, MacOsVersion> latestPerTrain = catalog.latestPublicPerTrain(LocalDate.now(ZoneOffset.UTC));
		List<Map.Entry<String, Long>> ordered = new ArrayList<>(counts.entrySet());
		ordered.sort(newestFirst());

		long max = Math.max(unknown, counts.values().stream().mapToLong(Long::longValue).max().orElse(0));
		List<VersionShare> bars = new ArrayList<>();
		for (Map.Entry<String, Long> entry : ordered.subList(0, Math.min(ordered.size(), MAX_VERSION_BARS)))
		{
			OsVersionStanding standing = MacOsVersion.parse(entry.getKey())
				.map(version -> version.standingAgainst(latest.orElse(null), latestPerTrain.get(version.major())))
				.orElse(OsVersionStanding.UNKNOWN);
			bars.add(share(entry.getKey(), standing, entry.getValue(), runs.size(), max, false, entry.getKey()));
		}

		// Everything past the cap is older than everything shown, so the fold
		// is always the old end of the fleet and always reads red — even if a
		// patched older train hides in it, twelve versions in.
		List<Map.Entry<String, Long>> tail = ordered.subList(Math.min(ordered.size(), MAX_VERSION_BARS),
			ordered.size());
		if (!tail.isEmpty())
		{
			long folded = tail.stream().mapToLong(Map.Entry::getValue).sum();
			bars.add(share(tail.size() + " older versions", OsVersionStanding.MAJOR_BEHIND,
				folded, runs.size(), max, true, null));
		}

		if (unknown > 0)
		{
			bars.add(share("Unknown", OsVersionStanding.UNKNOWN, unknown, runs.size(), max, false, null));
		}
		return List.copyOf(bars);
	}

	/**
	 * Newest version first; anything that does not parse as a version sorts
	 * after everything that does, alphabetically, rather than being guessed at
	 * a position it has not earned.
	 */
	private static Comparator<Map.Entry<String, Long>> newestFirst()
	{
		Comparator<Map.Entry<String, Long>> byVersion = Comparator.comparing(
			entry -> MacOsVersion.parse(entry.getKey()).orElse(null),
			Comparator.nullsLast(Comparator.reverseOrder()));
		return byVersion.thenComparing(Map.Entry::getKey);
	}

	private static VersionShare share(String version, OsVersionStanding standing, long devices,
		long fleet, long max, boolean folded, String reportFilter)
	{
		return new VersionShare(version, standing, devices, percent(devices, fleet), bar(devices, max), folded,
			reportFilter);
	}

	/** The checks the fleet is failing right now, worst first. */
	private List<ViolationShare> violations(List<LatestRun> runs)
	{
		List<Long> reportIds = runs.stream().map(LatestRun::reportId).toList();
		List<ItemFailureCount> counts = resultRepository.countFailuresByItem(reportIds);
		if (counts.isEmpty())
		{
			return List.of();
		}

		long max = counts.get(0).failures();
		return counts.stream()
			.limit(MAX_VIOLATION_BARS)
			.map(count -> new ViolationShare(count.name(), count.control(), count.failures(),
				percent(count.failures(), runs.size()), bar(count.failures(), max)))
			.toList();
	}

	/**
	 * Whole percent, floored, so nothing but a clean sweep ever reads as 100%.
	 */
	private static int percent(long part, long whole)
	{
		return whole == 0 ? 0 : (int)((part * 100L) / whole);
	}

	/**
	 * Bar length as a share of the longest bar. Floored at 2 so the smallest
	 * value is still a mark rather than nothing at all — the count is printed
	 * beside it either way, so the floor cannot mislead about a value.
	 */
	private static int bar(long value, long max)
	{
		if (max == 0)
		{
			return 0;
		}
		return (int)Math.max(2, (value * 100L) / max);
	}
}
