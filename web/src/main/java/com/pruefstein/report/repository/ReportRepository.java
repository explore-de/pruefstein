package com.pruefstein.report.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReportRepository implements PanacheRepository<Report>
{
	public Optional<Report> findOpenByDeviceAndUser(String deviceId, String userId)
	{
		return find("deviceId = ?1 and userId = ?2 and status = ?3",
			deviceId, userId, ReportStatus.OPEN).firstResultOptional();
	}

	public List<Report> findExpiredOpen(Instant now)
	{
		return list("status = ?1 and deadline < ?2", ReportStatus.OPEN, now);
	}

	/**
	 * Open reports whose deadline falls inside the reminder window and that
	 * have not been reminded yet. Already-expired reports are excluded — those
	 * are closed by {@code DeadlineJob} rather than reminded about.
	 */
	public List<Report> findDueForReminder(Instant now, Instant threshold)
	{
		return list("status = ?1 and reminderSentAt is null and deadline > ?2 and deadline <= ?3",
			ReportStatus.OPEN, now, threshold);
	}

	/**
	 * The newest run of each of the given users, keyed by user id.
	 *
	 * <p>
	 * One query for a whole page of users rather than one per row: walking
	 * {@code AppUser.reports} instead would be an N+1, and the Users screen
	 * needs exactly one report from each list.
	 *
	 * <p>
	 * The reduce keeps the first report seen per user, which the ordering makes
	 * the newest. Id breaks a tie, so two runs landing in the same second still
	 * pick the same winner every time the page is drawn.
	 */
	public Map<Long, Report> findLatestByUser(Collection<Long> userIds)
	{
		if (userIds.isEmpty())
		{
			return Map.of();
		}
		Sort newestFirst = Sort.by("checkedAt", Sort.Direction.Descending)
			.and("id", Sort.Direction.Descending);
		Map<Long, Report> latest = new LinkedHashMap<>();
		for (Report report : list("appUser.id in ?1", newestFirst, userIds))
		{
			latest.putIfAbsent(report.getAppUser().id, report);
		}
		return latest;
	}

	public List<Report> listFiltered(ReportStatus status, String q, String sort, String dir)
	{
		return listFiltered(status, q, sort, dir, null);
	}

	public List<Report> listFiltered(ReportStatus status, String q, String sort, String dir, String keycloakUser)
	{
		StringBuilder query = new StringBuilder();
		List<Object> params = new ArrayList<>();
		int p = 1;

		if (status != null)
		{
			query.append("status = ?").append(p++);
			params.add(status);
		}

		if (q != null && !q.isBlank())
		{
			if (!query.isEmpty()) query.append(" and ");
			String like = "%" + q.toLowerCase() + "%";
			query.append("(lower(deviceId) like ?").append(p)
				.append(" or lower(userId) like ?").append(p)
				.append(" or lower(keycloakUser) like ?").append(p).append(")");
			params.add(like);
			p++;
		}

		if (keycloakUser != null)
		{
			if (!query.isEmpty()) query.append(" and ");
			query.append("keycloakUser = ?").append(p++);
			params.add(keycloakUser);
		}

		Sort panacheSort = buildSort(sort, dir);

		if (query.isEmpty())
		{
			return listAll(panacheSort);
		}
		return list(query.toString(), panacheSort, params.toArray());
	}

	private Sort buildSort(String col, String dir)
	{
		String column = switch (col != null ? col : "")
		{
			case "status" -> "status";
			case "deviceId" -> "deviceId";
			case "user" -> "keycloakUser";
			default -> "checkedAt";
		};
		boolean desc = !"asc".equals(dir);
		return desc ? Sort.by(column).descending() : Sort.by(column).ascending();
	}
}
