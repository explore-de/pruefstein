package com.pruefstein.compliance.repository;

import java.util.List;
import java.util.Optional;

import com.pruefstein.compliance.domain.AppBlacklistCheck;
import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Checks are retired, never deleted, so almost every caller wants the ones
 * still in force rather than everything the table holds. The {@code active}
 * methods are that list; plain {@code listAll} and {@code findById} still see
 * retired checks, which is what report screens need to explain them.
 */
@ApplicationScoped
public class ComplianceItemRepository implements PanacheRepository<ComplianceItem>
{
	/**
	 * Every authored check whose SQL is exactly {@code query}.
	 *
	 * <p>
	 * Typed against {@link ExpressionCheck} rather than this repository's own
	 * entity because the column belongs to the subclass: the hierarchy shares
	 * one table, so HQL has to name {@code ExpressionCheck} to see it at all.
	 *
	 * @param query
	 *            matched byte for byte — this exists to recognise SQL a release
	 *            shipped, not to search for checks
	 */
	public List<ExpressionCheck> findByQuery(String query)
	{
		return getEntityManager()
			.createQuery("FROM ExpressionCheck WHERE query = :query", ExpressionCheck.class)
			.setParameter("query", query)
			.getResultList();
	}
	private static final String ACTIVE = "retiredAt is null";

	/** Every check still in force. */
	public List<ComplianceItem> listActive()
	{
		return list(ACTIVE);
	}

	/** The checks still in force in one group. */
	public List<ComplianceItem> listActive(ComplianceGroup group)
	{
		return list(ACTIVE + " and group = ?1", group);
	}

	/**
	 * The check in force that was created from a library entry, if any. The
	 * library offers each entry at most once at a time.
	 */
	public Optional<ComplianceItem> findActiveByLibraryKey(String libraryKey)
	{
		return find(ACTIVE + " and libraryKey = ?1", libraryKey).firstResultOptional();
	}

	/** Checks written by hand, or created before the library tracked them. */
	public List<ComplianceItem> listWithoutLibraryKey(String name)
	{
		return list("libraryKey is null and name = ?1", name);
	}

	public long countActive()
	{
		return count(ACTIVE);
	}

	/**
	 * The generated blacklist check, unless it has been retired — in which case
	 * the blocked-apps screen has nothing to drive and says so.
	 */
	public Optional<AppBlacklistCheck> findActiveBlacklistCheck()
	{
		return find("from AppBlacklistCheck where " + ACTIVE)
			.firstResultOptional()
			.map(AppBlacklistCheck.class::cast);
	}
}
