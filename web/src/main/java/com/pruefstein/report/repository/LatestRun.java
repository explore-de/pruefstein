package com.pruefstein.report.repository;

/**
 * One device's newest run, reduced to the three columns the fleet charts read.
 *
 * <p>
 * Deliberately a projection rather than a
 * {@link com.pruefstein.report.domain.Report}: the dashboard is the landing
 * page, and it asks this question about every device there has ever been. Three
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
 */
public record LatestRun(Long reportId, String deviceId, String osVersion)
{
}
