package com.pruefstein.report.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.pruefstein.report.domain.Report;

/**
 * One user's runs as the index table shows them: the latest on screen, the rest
 * folded behind a toggle. A user who ran once is a group of one and gets no
 * toggle at all.
 */
public record ReportGroup(Report latest, List<Report> older)
{
	/**
	 * Newest first, so an expanded group reads the way the table does by
	 * default. Equal timestamps fall back to the id, which at least makes the
	 * pick deterministic when two runs land in the same second.
	 */
	private static final Comparator<Report> NEWEST_FIRST = Comparator.comparing(Report::getCheckedAt, Comparator.nullsFirst(Comparator.<Instant> naturalOrder()))
		.thenComparing(report -> report.id, Comparator.nullsFirst(Comparator.<Long> naturalOrder()))
		.reversed();

	/**
	 * Folds repeat runs of one user together, leaving the order the repository
	 * chose intact: a group sits where its visible row — the latest run — sat
	 * in the list. Sorting still governs what the reader sees, because what the
	 * reader sees is exactly what was sorted.
	 */
	public static List<ReportGroup> group(List<Report> reports)
	{
		Map<String, List<Report>> byUser = new LinkedHashMap<>();
		for (Report report : reports)
		{
			byUser.computeIfAbsent(keyFor(report), key -> new ArrayList<>()).add(report);
		}

		List<ReportGroup> groups = new ArrayList<>(byUser.size());
		for (List<Report> runs : byUser.values())
		{
			List<Report> sorted = new ArrayList<>(runs);
			sorted.sort(NEWEST_FIRST);
			groups.add(new ReportGroup(sorted.getFirst(), List.copyOf(sorted.subList(1, sorted.size()))));
		}

		groups.sort(Comparator.comparingInt(group -> reports.indexOf(group.latest())));
		return groups;
	}

	/**
	 * Runs nobody could be resolved for are not one user's repeats — they are
	 * unattributed runs that happen to share a blank. Each keeps its own row.
	 */
	private static String keyFor(Report report)
	{
		String user = report.getKeycloakUser();
		return user == null || user.isBlank() ? "unattributed:" + report.id : "user:" + user;
	}

	public boolean hasOlder()
	{
		return !older.isEmpty();
	}

	public Report getLatest()
	{
		return latest;
	}

	public List<Report> getOlder()
	{
		return older;
	}

	public int getOlderCount()
	{
		return older.size();
	}

	/**
	 * Every run of the group in table order, each carrying whether it is one of
	 * the folded-away ones. The two kinds of row differ only in a flag, so the
	 * template renders them from one piece of markup instead of two that have
	 * to be kept in step.
	 */
	public List<Row> getRows()
	{
		List<Row> rows = new ArrayList<>(older.size() + 1);
		rows.add(new Row(latest, false));
		older.forEach(report -> rows.add(new Row(report, true)));
		return rows;
	}

	public record Row(Report report, boolean older)
	{
		public Report getReport()
		{
			return report;
		}

		public boolean isOlder()
		{
			return older;
		}

		/**
		 * Read against the clock at render time, the way the deadline itself is
		 * counted, so a page left open overnight is not still claiming a week.
		 */
		public DeadlineUrgency getDeadlineUrgency()
		{
			return DeadlineUrgency.of(report.getDeadline(), report.getStatus(), Instant.now());
		}

		public String getDeadlineHint()
		{
			return DeadlineUrgency.hint(report.getDeadline(), report.getStatus(), Instant.now());
		}
	}

	/** Identifies the group to the client-side toggle. */
	public String getKey()
	{
		return "g" + latest.id;
	}
}
