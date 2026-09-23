package com.pruefstein.compliance.repository;

import java.util.Collection;
import java.util.List;

import com.pruefstein.compliance.domain.ComplianceResult;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ComplianceResultRepository implements PanacheRepository<ComplianceResult>
{
	/**
	 * The checks failing across a given set of runs, worst first.
	 *
	 * <p>
	 * The condition is {@link ComplianceResult#isFailing()} written as HQL: a
	 * recorded failure against a check that has since been retired is not held
	 * against anybody, so it is not counted here either.
	 *
	 * <p>
	 * Grouped by the check's id rather than its name, so two checks that happen
	 * to share a name stay two bars.
	 *
	 * <p>
	 * Assembled from plain columns rather than an HQL {@code select new ...}
	 * constructor expression, for the reason spelled out on
	 * {@link com.pruefstein.report.repository.ReportRepository#findLatestPerDevice()}:
	 * that form hides the class name inside a string, where the native-image
	 * build cannot find it, and the query then fails only in production.
	 *
	 * @param reportIds
	 *            the runs to count within — one per device, so a count reads as
	 *            a number of machines
	 */
	public List<ItemFailureCount> countFailuresByItem(Collection<Long> reportIds)
	{
		if (reportIds.isEmpty())
		{
			return List.of();
		}
		List<Object[]> rows = getEntityManager()
			.createQuery("select i.name, i.control, count(c) from ComplianceResult c join c.item i"
				+ " where c.passed = false and i.retiredAt is null and c.report.id in :reportIds"
				+ " group by i.id, i.name, i.control"
				+ " order by count(c) desc, i.name asc", Object[].class)
			.setParameter("reportIds", reportIds)
			.getResultList();

		return rows.stream()
			.map(row -> new ItemFailureCount((String)row[0], (String)row[1], (Long)row[2]))
			.toList();
	}
}
