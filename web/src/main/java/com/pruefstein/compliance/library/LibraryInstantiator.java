package com.pruefstein.compliance.library;

import com.pruefstein.compliance.domain.AppBlacklistCheck;
import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Turns a library entry into a check in force. Shared by first-boot seeding and
 * the Library screen, so a check added by hand is indistinguishable from one
 * seeded on install.
 */
@ApplicationScoped
public class LibraryInstantiator
{
	@Inject
	ComplianceGroupRepository groupRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	/** Whether a check created from this entry is currently in force. */
	public boolean isInUse(LibraryEntry entry)
	{
		return itemRepository.findActiveByLibraryKey(entry.key()).isPresent();
	}

	/**
	 * Creates and persists a new check from the entry. The caller decides
	 * whether a second one is wanted; see {@link #isInUse}.
	 */
	@Transactional(Transactional.TxType.MANDATORY)
	public ComplianceItem instantiate(LibraryEntry entry)
	{
		ComplianceItem item;
		if (entry.isGenerated())
		{
			item = new AppBlacklistCheck();
		}
		else
		{
			ExpressionCheck check = new ExpressionCheck();
			check.setQuery(entry.query());
			check.setExpectedExpression(entry.expression());
			item = check;
		}
		item.setName(entry.name());
		item.setLibraryKey(entry.key());
		if (entry.group() != null)
		{
			item.setGroup(group(entry.group()));
		}
		itemRepository.persist(item);
		return item;
	}

	/**
	 * Tags checks that were seeded before checks remembered their library
	 * entry, so the Library screen does not offer them a second time. Matched
	 * by name, which is what seeding created them with.
	 *
	 * @return how many checks were tagged
	 */
	@Transactional(Transactional.TxType.MANDATORY)
	public int adoptUntagged(Iterable<LibraryEntry> entries)
	{
		int adopted = 0;
		for (LibraryEntry entry : entries)
		{
			for (ComplianceItem item : itemRepository.listWithoutLibraryKey(entry.name()))
			{
				item.setLibraryKey(entry.key());
				adopted++;
			}
		}
		return adopted;
	}

	/**
	 * Groups are matched by name: a check being created needs somewhere to
	 * live, so if its group is gone it is recreated with it. A retired group
	 * counts as gone — a check must not land somewhere no one can reach it.
	 */
	private ComplianceGroup group(String name)
	{
		return groupRepository.findActiveByName(name)
			.orElseGet(() -> {
				ComplianceGroup group = new ComplianceGroup();
				group.setName(name);
				groupRepository.persist(group);
				return group;
			});
	}
}
