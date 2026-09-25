package com.pruefstein.dashboard.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ComplianceResult;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.dashboard.api.FleetStats;
import com.pruefstein.dashboard.api.VersionShare;
import com.pruefstein.dashboard.api.ViolationShare;
import com.pruefstein.osversion.domain.MacOsRelease;
import com.pruefstein.osversion.repository.MacOsReleaseRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FleetDashboardTest
{
	@Inject
	FleetDashboard fleet;

	@Inject
	ReportRepository reportRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceGroupRepository groupRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	MacOsReleaseRepository releaseRepository;

	private final List<Long> reports = new ArrayList<>();

	private final List<Long> items = new ArrayList<>();

	private final List<Long> groups = new ArrayList<>();

	/**
	 * Nothing here runs inside a rolled-back transaction, and these charts
	 * count the whole estate — a report left behind does not just clutter the
	 * next class, it changes its numbers.
	 */
	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			for (Long id : reports)
			{
				resultRepository.delete("report.id = ?1", id);
				reportRepository.deleteById(id);
			}
			items.forEach(itemRepository::deleteById);
			groups.forEach(groupRepository::deleteById);
		});
	}

	/**
	 * The point of the whole projection: a machine that reports every hour is
	 * one machine, on whatever it last said it was running.
	 */
	@Test
	void countsEachDeviceOnceOnItsNewestVersion()
	{
		report("fleet-a.local", "15.7.9", hoursAgo(5));
		report("fleet-a.local", "26.1", hoursAgo(1));
		report("fleet-b.local", "15.7.9", hoursAgo(3));

		FleetStats stats = fleet.stats();

		assertEquals(2, stats.deviceCount());
		assertEquals(List.of("26.1", "15.7.9"), versionLabels(stats));
		assertEquals(1, share(stats, "26.1").devices());
		assertEquals(1, share(stats, "15.7.9").devices());
	}

	/** Newest release at the top, so the chart reads as drift downwards. */
	@Test
	void ordersVersionsNewestFirstRatherThanBySize()
	{
		report("fleet-old-1.local", "14.6", hoursAgo(2));
		report("fleet-old-2.local", "14.6", hoursAgo(2));
		report("fleet-old-3.local", "14.6", hoursAgo(2));
		report("fleet-new.local", "26.1", hoursAgo(2));

		FleetStats stats = fleet.stats();

		assertEquals(List.of("26.1", "14.6"), versionLabels(stats));
		assertEquals(1, share(stats, "26.1").devices());
		assertEquals(3, share(stats, "14.6").devices());
		assertEquals(75, share(stats, "14.6").fleetPct());
	}

	/**
	 * A machine on the newest fix of a train Apple still patches is amber, not
	 * red; one a fix short of that train is still red.
	 */
	@Test
	void theNewestFixOfAStillPatchedTrainReadsAmber()
	{
		release("27.0", "26A428");
		release("15.7.9", "24G830");
		report("fleet-patched.local", "15.7.9", hoursAgo(2));
		report("fleet-unpatched.local", "15.7.8", hoursAgo(2));

		try
		{
			FleetStats stats = fleet.stats();

			VersionShare patched = share(stats, "15.7.9");
			assertTrue(patched.isPatchBehind());
			assertFalse(patched.isBehind());
			assertEquals("older macOS, fully patched", patched.note());
			VersionShare unpatched = share(stats, "15.7.8");
			assertTrue(unpatched.isBehind());
			// named by what it is missing, not by how far it is from the newest
			assertEquals("older macOS, missing 15.7.9", unpatched.note());
		}
		finally
		{
			QuarkusTransaction.requiringNew().run(() -> releaseRepository
				.delete("productVersion in ?1", List.of("27.0", "15.7.9")));
		}
	}

	private void release(String version, String build)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			MacOsRelease release = new MacOsRelease();
			release.setProductVersion(version);
			release.setBuild(build);
			release.setPostingDate(LocalDate.of(2026, 9, 15));
			release.setPublicRelease(true);
			release.setSeenAt(Instant.now());
			releaseRepository.persist(release);
		});
	}

	/**
	 * A machine that never said what it runs is its own bar rather than being
	 * quietly dropped — a fleet with a silent quarter is worth seeing.
	 */
	@Test
	void devicesThatReportedNoVersionGetTheirOwnBar()
	{
		report("fleet-known.local", "26.1", hoursAgo(2));
		report("fleet-silent.local", null, hoursAgo(2));

		FleetStats stats = fleet.stats();

		VersionShare unknown = share(stats, "Unknown");
		assertTrue(unknown.isUnknown());
		assertEquals(1, unknown.devices());
		assertEquals("no version reported", unknown.note());
		// Always last, however it sorts against a real version.
		assertEquals("Unknown", stats.versions().get(stats.versions().size() - 1).version());
	}

	@Test
	void ranksChecksByHowManyDevicesAreFailingThem()
	{
		ComplianceGroup group = group();
		ComplianceItem widespread = item("Fleet FileVault", "A.8.24", group);
		ComplianceItem rare = item("Fleet Firewall", null, group);

		Report one = report("fleet-v1.local", "26.1", hoursAgo(2));
		Report two = report("fleet-v2.local", "26.1", hoursAgo(2));
		result(one, widespread, false);
		result(two, widespread, false);
		result(one, rare, false);
		result(two, rare, true);

		FleetStats stats = fleet.stats();

		assertTrue(stats.hasViolations());
		assertEquals(List.of("Fleet FileVault", "Fleet Firewall"),
			stats.violations().stream().map(ViolationShare::name).toList());

		ViolationShare worst = stats.violations().get(0);
		assertEquals(2, worst.devices());
		assertEquals(100, worst.fleetPct());
		assertEquals(100, worst.barPct());
		assertEquals("A.8.24", worst.control());
		assertTrue(worst.hasControl());

		ViolationShare second = stats.violations().get(1);
		assertEquals(1, second.devices());
		assertEquals(50, second.fleetPct());
		assertEquals(50, second.barPct());
		assertFalse(second.hasControl());
	}

	/**
	 * A failure against a check nobody enforces any more is not held against
	 * the machine on its report, so it is not held against it here either.
	 */
	@Test
	void retiredChecksAreNotCounted()
	{
		ComplianceGroup group = group();
		ComplianceItem retired = item("Fleet Retired", null, group);
		QuarkusTransaction.requiringNew().run(() -> itemRepository.findById(retired.id).retire());

		Report report = report("fleet-retired.local", "26.1", hoursAgo(2));
		result(report, retired, false);

		assertFalse(fleet.stats().hasViolations());
	}

	private static Instant hoursAgo(int hours)
	{
		return Instant.now().minus(hours, ChronoUnit.HOURS);
	}

	private static List<String> versionLabels(FleetStats stats)
	{
		return stats.versions().stream().map(VersionShare::version).toList();
	}

	private static VersionShare share(FleetStats stats, String version)
	{
		return stats.versions().stream()
			.filter(candidate -> candidate.version().equals(version))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no bar for " + version + " in " + versionLabels(stats)));
	}

	private Report report(String deviceId, String osVersion, Instant checkedAt)
	{
		Report report = new Report();
		QuarkusTransaction.requiringNew().run(() -> {
			report.setDeviceId(deviceId);
			report.setUserId("fleet-user");
			report.setKeycloakUser("fleet-user");
			report.setCheckedAt(checkedAt);
			report.setStatus(ReportStatus.COMPLIANT);
			report.setOsName("macOS");
			report.setOsVersion(osVersion);
			reportRepository.persist(report);
			reports.add(report.id);
		});
		return report;
	}

	private ComplianceGroup group()
	{
		ComplianceGroup group = new ComplianceGroup();
		QuarkusTransaction.requiringNew().run(() -> {
			group.setName("Fleet Chart Group");
			groupRepository.persist(group);
			groups.add(group.id);
		});
		return group;
	}

	private ComplianceItem item(String name, String control, ComplianceGroup group)
	{
		ExpressionCheck check = new ExpressionCheck();
		QuarkusTransaction.requiringNew().run(() -> {
			check.setName(name);
			check.setControl(control);
			check.setGroup(groupRepository.findById(group.id));
			check.setQuery("SELECT 1");
			check.setExpectedExpression("results.size() > 0");
			itemRepository.persist(check);
			items.add(check.id);
		});
		return check;
	}

	private void result(Report report, ComplianceItem item, boolean passed)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceResult result = new ComplianceResult();
			result.setReport(reportRepository.findById(report.id));
			result.setItem(itemRepository.findById(item.id));
			result.setPassed(passed);
			result.setOutput("[]");
			resultRepository.persist(result);
		});
	}
}
