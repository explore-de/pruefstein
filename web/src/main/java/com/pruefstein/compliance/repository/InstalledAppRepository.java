package com.pruefstein.compliance.repository;

import java.util.Collection;
import java.util.List;

import com.pruefstein.compliance.domain.InstalledApp;
import com.pruefstein.report.domain.Report;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InstalledAppRepository implements PanacheRepository<InstalledApp>
{
	public List<InstalledApp> listForReport(Report report)
	{
		return list("report", Sort.by("source").and("name"), report);
	}

	/**
	 * Every installation across the given reports, as rows of
	 * {@code source, name, identifier, report id}.
	 */
	public List<Object[]> listInstalls(Collection<Long> reportIds)
	{
		if (reportIds.isEmpty())
		{
			return List.of();
		}
		return getEntityManager()
			.createQuery("select distinct a.source, a.name, a.identifier, a.report.id"
				+ " from InstalledApp a where a.report.id in :ids", Object[].class)
			.setParameter("ids", reportIds)
			.getResultList();
	}

	/**
	 * Every distinct application across the given reports, as rows of
	 * {@code source, name, identifier, newest version, device count}.
	 * <p>
	 * Plain columns rather than {@code select new}, for the same native-image
	 * reason as {@code ReportRepository#findLatestPerDevice}.
	 */
	public List<Object[]> summarize(Collection<Long> reportIds)
	{
		if (reportIds.isEmpty())
		{
			return List.of();
		}
		return getEntityManager()
			.createQuery("select a.source, a.name, a.identifier, max(a.version), count(distinct a.report.deviceId)"
				+ " from InstalledApp a where a.report.id in :ids"
				+ " group by a.source, a.name, a.identifier"
				+ " order by lower(a.name), a.source", Object[].class)
			.setParameter("ids", reportIds)
			.getResultList();
	}
}
