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
		return getEntityManager()
			.createQuery("select new com.pruefstein.compliance.repository.ItemFailureCount("
				+ "i.name, i.control, count(c)) from ComplianceResult c join c.item i"
				+ " where c.passed = false and i.retiredAt is null and c.report.id in :reportIds"
				+ " group by i.id, i.name, i.control"
				+ " order by count(c) desc, i.name asc", ItemFailureCount.class)
			.setParameter("reportIds", reportIds)
			.getResultList();
	}
}
