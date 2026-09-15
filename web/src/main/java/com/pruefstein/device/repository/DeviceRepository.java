package com.pruefstein.device.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.pruefstein.device.domain.Device;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DeviceRepository implements PanacheRepository<Device>
{
	public List<Device> findByAppUser(Long appUserId)
	{
		return list("appUser.id", appUserId);
	}

	/**
	 * Everything this person reports for, oldest first so the list does not
	 * reshuffle itself every time one of them checks in.
	 */
	public List<Device> findByKeycloakUser(String keycloakUser)
	{
		return list("keycloakUser = ?1 order by deviceId asc", keycloakUser);
	}

	public Optional<Device> findByDeviceId(String deviceId)
	{
		return find("deviceId", deviceId).firstResultOptional();
	}

	/**
	 * Returns devices whose last report is older than {@code cutoff} and that
	 * have an active periodic flow instance — i.e. devices that are overdue.
	 */
	public List<Device> findOverdue(Instant cutoff)
	{
		return list("lastReportAt < ?1 and periodicFlowInstanceId is not null", cutoff);
	}

	/**
	 * Devices whose next report is close enough to ask for, and that have not
	 * been asked yet this cycle.
	 *
	 * <p>
	 * Already-overdue devices are deliberately excluded: {@code
	 * PeriodicDeadlineJob} files their MISSING report and turns their cycle
	 * over, and a reminder about a deadline that has already passed helps
	 * nobody.
	 *
	 * @param remindFrom
	 *            report older than this and the nudge is due
	 * @param overdueCutoff
	 *            report older than this and it is too late to nudge
	 */
	public List<Device> findDueForReminder(Instant remindFrom, Instant overdueCutoff)
	{
		return list("reminderSentAt is null and lastReportAt <= ?1 and lastReportAt > ?2 "
			+ "and periodicFlowInstanceId is not null", remindFrom, overdueCutoff);
	}
}
