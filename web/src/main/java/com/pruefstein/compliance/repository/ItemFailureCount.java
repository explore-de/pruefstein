package com.pruefstein.compliance.repository;

/**
 * How many machines one check is currently failing on.
 *
 * @param name
 *            the check's name, as the compliance screens show it
 * @param control
 *            the ISO 27001 Annex A control it answers to, or {@code null}
 * @param failures
 *            the number of runs in the counted set that recorded a failure —
 *            one per device, since the set is one run per device
 */
public record ItemFailureCount(String name, String control, long failures)
{
}
