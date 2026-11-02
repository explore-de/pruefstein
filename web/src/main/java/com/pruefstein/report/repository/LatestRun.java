package com.pruefstein.report.repository;

import com.pruefstein.report.domain.ReportStatus;

/**
 * One device's newest run, reduced to the columns the fleet dashboard reads.
 *
 * <p>
 * Deliberately a projection rather than a
 * {@link com.pruefstein.report.domain.Report}: the dashboard is the landing
 * page, and it asks this question about every device there has ever been. A few
 * small columns per row stay cheap to select at that width; hydrating the whole
 * entity, with its results collection behind it, would not.
 *
 * @param reportId
 *            the run itself, so the violation counts can be scoped to it
 * @param deviceId
 *            the machine the run came from
 * @param osVersion
 *            the marketing version that machine reported, or {@code null} when
 *            it reported none
 * @param status
 *            the run's verdict, which is the device's standing right now
 */
public record LatestRun(Long reportId, String deviceId, String osVersion, ReportStatus status)
{
}
