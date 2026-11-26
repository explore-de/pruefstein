package com.pruefstein.report.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.pruefstein.compliance.domain.BlockedApp;
import com.pruefstein.compliance.domain.InstalledApp;
import com.pruefstein.compliance.repository.BlockedAppRepository;
import com.pruefstein.compliance.repository.InstalledAppRepository;
import com.pruefstein.compliance.service.BlacklistMatcher;
import com.pruefstein.homebrew.service.HomebrewCatalog;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.repository.LatestRun;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.report.service.ReportDetails.InventoryRow;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * Every application installed anywhere, one row per application rather than per
 * device, so an admin can screen the estate without opening each report.
 * <p>
 * Read from each device's newest run only: an app removed since an older run is
 * not installed any more, and counting it would keep it on the list forever.
 * Scoped by {@link ReportAccess} like the reports themselves — an admin sees
 * the fleet, anybody else only their own devices.
 */
@RequestScoped
public class FleetInventory
{
	@Inject
	ReportAccess reportAccess;

	@Inject
	ReportRepository reportRepository;

	@Inject
	InstalledAppRepository installedAppRepository;

	@Inject
	BlockedAppRepository blockedAppRepository;

	@Inject
	BlacklistMatcher blacklistMatcher;

	@Inject
	HomebrewCatalog homebrewCatalog;

	/**
	 * One device that has the application, by its latest run — so an admin
	 * knows whom to ask about it, and can open the report it came in with.
	 */
	public record Holder(Long reportId, String deviceId, String userName)
	{
	}

	/** One application and the devices that currently have it. */
	public record FleetApp(InventoryRow row, List<Holder> holders)
	{
		public int devices()
		{
			return holders.size();
		}

		/**
		 * Names the application across requests — what the AI analysis answers
		 * with, and what the page finds its row by.
		 */
		public String getKey()
		{
			return key(row.getSource(), row.getName(), row.getIdentifier());
		}
	}

	public List<FleetApp> list()
	{
		List<Long> reportIds = reportRepository.findLatestPerDevice(reportAccess.ownerFilter()).stream()
			.map(LatestRun::reportId)
			.toList();
		List<BlockedApp> rules = blockedAppRepository.listEnabled();
		Map<String, List<Holder>> holders = holdersByKey(reportIds);
		return installedAppRepository.summarize(reportIds).stream()
			.map(columns -> toFleetApp(columns, rules, holders))
			.toList();
	}

	/**
	 * Who has which application, keyed like {@link FleetApp#getKey()}. One
	 * report per device, so each holder is a distinct device.
	 */
	private Map<String, List<Holder>> holdersByKey(List<Long> reportIds)
	{
		if (reportIds.isEmpty())
		{
			return Map.of();
		}
		Map<Long, Report> reports = reportRepository.listWithUsers(reportIds).stream()
			.collect(Collectors.toMap(report -> report.id, Function.identity()));
		Map<String, List<Holder>> holders = new HashMap<>();
		for (Object[] columns : installedAppRepository.listInstalls(reportIds))
		{
			Report report = reports.get((Long)columns[3]);
			holders.computeIfAbsent(key((String)columns[0], (String)columns[1], (String)columns[2]),
				k -> new ArrayList<>())
				.add(new Holder(report.id, report.getDeviceId(), report.getUserName()));
		}
		holders.values().forEach(list -> list.sort(Comparator.comparing(Holder::deviceId,
			Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))));
		return holders;
	}

	private static String key(String source, String name, String identifier)
	{
		return source + "|" + name + "|" + (identifier == null ? "" : identifier);
	}

	private FleetApp toFleetApp(Object[] columns, List<BlockedApp> rules, Map<String, List<Holder>> holders)
	{
		// Transient, only so the row reads like a report's inventory row
		InstalledApp app = new InstalledApp();
		app.setSource((String)columns[0]);
		app.setName((String)columns[1]);
		app.setIdentifier((String)columns[2]);
		app.setVersion((String)columns[3]);
		InventoryRow row = new InventoryRow(app,
			blacklistMatcher.ruleFor(app.getSource(), app.getName(), app.getIdentifier(), rules),
			homebrewCatalog.linkFor(app.getSource(), app.getName()).orElse(null));
		return new FleetApp(row, holders.getOrDefault(key(app.getSource(), app.getName(), app.getIdentifier()),
			List.of()));
	}
}
