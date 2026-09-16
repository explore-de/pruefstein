package com.pruefstein.osversion.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;

import com.pruefstein.osversion.domain.MacOsVersion;
import com.pruefstein.osversion.domain.OsVersionStanding;
import com.pruefstein.report.domain.Report;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Decides what a report says about its device's operating system.
 * <p>
 * The comparison is against the newest release that existed <em>when the report
 * was filed</em>, stamped onto the report at the time. A report is a statement
 * about a machine on a day, and re-judging it every time Apple ships something
 * would turn a clean report red months later without anything having happened.
 * Reports filed before this was recorded fall back to today's newest release,
 * which is the best that can be said about them.
 */
@ApplicationScoped
public class OsVersionAssessor
{
	@Inject
	MacOsReleaseCatalog catalog;

	public OsVersionAssessment assess(Report report)
	{
		if (report == null || report.getOsVersion() == null || report.getOsVersion().isBlank())
		{
			return OsVersionAssessment.unknown();
		}
		Optional<MacOsVersion> reported = MacOsVersion.parse(report.getOsVersion());
		if (reported.isEmpty())
		{
			return OsVersionAssessment.unknown();
		}

		MacOsVersion latest = latestFor(report);
		if (latest == null)
		{
			// The version is worth showing even with nothing to measure it
			// against; it simply gets no colour.
			return new OsVersionAssessment(report.getOsName(), report.getOsVersion(), report.getOsBuild(),
				null, OsVersionStanding.CURRENT, 0);
		}

		OsVersionStanding standing = reported.get().standingAgainst(latest);
		int years = 0;
		if (standing == OsVersionStanding.MAJOR_BEHIND)
		{
			OptionalInt age = reported.get().trainAgeInYears(LocalDate.now());
			years = age.orElse(0);
		}
		return new OsVersionAssessment(report.getOsName(), report.getOsVersion(), report.getOsBuild(),
			latest.toString(), standing, years);
	}

	private MacOsVersion latestFor(Report report)
	{
		return MacOsVersion.parse(report.getOsLatestVersion())
			.or(() -> catalog.latestPublicVersion())
			.orElse(null);
	}
}
