package com.pruefstein.compliance.repository;

import java.util.List;
import java.util.Optional;

import com.pruefstein.compliance.domain.ComplianceGroup;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Groups are retired, never deleted, so the catalogue screens want the ones
 * still in force. Plain {@code listAll} and {@code findById} still see retired
 * groups, which is what past reports need to name them.
 */
@ApplicationScoped
public class ComplianceGroupRepository implements PanacheRepository<ComplianceGroup>
{
	private static final String ACTIVE = "retiredAt is null";

	/** Every group still in force. */
	public List<ComplianceGroup> listActive()
	{
		return list(ACTIVE);
	}

	/**
	 * A group still in force, by name. Retired groups are deliberately not
	 * matched: a name that has been withdrawn is free to be used again.
	 */
	public Optional<ComplianceGroup> findActiveByName(String name)
	{
		return find(ACTIVE + " and name = ?1", name).firstResultOptional();
	}
}
