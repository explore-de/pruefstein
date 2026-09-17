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
	private Optional<ComplianceGroup> findActiveByName(String name)
	{
		return find(ACTIVE + " and name = ?1", name).firstResultOptional();
	}

	/**
	 * The active group of this name, created if there is none.
	 *
	 * <p>
	 * Groups are matched by name rather than ledgered: a check being seeded or
	 * re-filed needs somewhere to live, so if its group is gone it is recreated
	 * along with it. A retired group counts as gone: a check must not land
	 * somewhere no one can reach it.
	 */
	public ComplianceGroup findOrCreateByName(String name)
	{
		return findActiveByName(name)
			.orElseGet(() -> {
				ComplianceGroup group = new ComplianceGroup();
				group.setName(name);
				persist(group);
				return group;
			});
	}
}
