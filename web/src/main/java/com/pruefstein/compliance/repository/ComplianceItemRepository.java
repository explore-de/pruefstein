package com.pruefstein.compliance.repository;

import java.util.List;
import java.util.Optional;

import com.pruefstein.compliance.domain.AppBlacklistCheck;
import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
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
