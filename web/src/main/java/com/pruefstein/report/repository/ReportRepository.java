package com.pruefstein.report.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
	private static final String CHECKED_AT = "checkedAt";

	public Optional<Report> findOpenByDeviceAndUser(String deviceId, String userId)
	{
		return find("deviceId = ?1 and userId = ?2 and status = ?3",
			deviceId, userId, ReportStatus.OPEN).firstResultOptional();
	}

	/**
	 * The newest run for one device, scoped to the person who owns it so a
	 * device id guessed from elsewhere cannot be used to read someone else's
	 * report.
	 */
	public Optional<Report> findLatestForDevice(String deviceId, String keycloakUser)
	{
		return find("deviceId = ?1 and keycloakUser = ?2 order by checkedAt desc",
			deviceId, keycloakUser).firstResultOptional();
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
		Sort newestFirst = Sort.by(CHECKED_AT, Sort.Direction.Descending)
			.and("id", Sort.Direction.Descending);
		Map<Long, Report> latest = new LinkedHashMap<>();
		for (Report report : list("appUser.id in ?1", newestFirst, userIds))
		{
			latest.putIfAbsent(report.getAppUser().id, report);
		}
		return latest;
	}

	/**
	 * The newest run of every device that has ever reported, newest first.
	 *
	 * <p>
	 * This is what the fleet charts mean by "the fleet": one row per machine,
	 * as that machine last described itself. Counting runs instead would let a
	 * device that reports every day outvote one that reports every month, and
	 * the estate would look like whatever its chattiest members are running.
	 *
	 * <p>
	 * The reduce keeps the first row seen per device, which the ordering makes
	 * the newest — the same tie-break as {@link #findLatestByUser}, so a device
	 * and its owner never disagree about which run is current.
	 *
	 * <p>
	 * The columns are selected plainly and assembled here rather than through
	 * an HQL {@code select new ...} constructor expression. That form names the
	 * class only inside a string, which the native-image build cannot see, so
	 * the class is left unregistered and the query dies at runtime with "Could
	 * not resolve class ... named for instantiation" — on the native image
	 * production runs, while every JVM test passes. Registering it for
	 * reflection would also work; not needing reflection at all is better,
	 * because then the tests exercise what production executes.
	 */
	public List<LatestRun> findLatestPerDevice()
	{
		List<Object[]> rows = getEntityManager()
			.createQuery("select r.id, r.deviceId, r.osVersion from Report r"
				+ " order by r.checkedAt desc, r.id desc", Object[].class)
			.getResultList();

		Map<String, LatestRun> latest = new LinkedHashMap<>();
		for (Object[] row : rows)
		{
			String deviceId = (String)row[1];
			latest.putIfAbsent(deviceId, new LatestRun((Long)row[0], deviceId, (String)row[2]));
		}
		return List.copyOf(latest.values());
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
				.append(" or lower(keycloakUser) like ?").append(p)
				// A subquery rather than a path: appUser.firstname would inner
				// join, and every run nobody was matched to would drop out of
				// the search
				.append(" or appUser.id in (select u.id from AppUser u where")
				.append(" lower(concat(coalesce(u.firstname, ''), ' ', coalesce(u.lastname, ''))) like ?").append(p)
				.append(" or lower(u.mail) like ?").append(p).append("))");
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

		List<Report> reports = query.isEmpty()
			? listAll(panacheSort)
			: list(query.toString(), panacheSort, params.toArray());
		if ("user".equals(sort))
		{
			reports = sortByUserName(reports, !"asc".equals(dir));
		}
		return reports;
	}

	/**
	 * The user column shows a name that is the person's when one is known and
	 * the login otherwise, which no single column holds — so it is sorted here,
	 * on exactly what the reader sees. Stable, so runs of one person keep the
	 * newest-first order the query gave them. Unnamed runs go last either way.
	 */
	private static List<Report> sortByUserName(List<Report> reports, boolean descending)
	{
		Comparator<String> byName = String.CASE_INSENSITIVE_ORDER;
		if (descending)
		{
			byName = byName.reversed();
		}
		List<Report> sorted = new ArrayList<>(reports);
		sorted.sort(Comparator.comparing(Report::getUserName, Comparator.nullsLast(byName)));
		return sorted;
	}

	private Sort buildSort(String col, String dir)
	{
		String column = switch (col != null ? col : "")
		{
			case "status" -> "status";
			case "deviceId" -> "deviceId";
			// Ordered in memory by the displayed name; newest first within one
			case "user" -> CHECKED_AT;
			default -> CHECKED_AT;
		};
		boolean desc = "user".equals(col) || !"asc".equals(dir);
		return desc ? Sort.by(column).descending() : Sort.by(column).ascending();
	}
}
