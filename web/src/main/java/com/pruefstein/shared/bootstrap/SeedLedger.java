package com.pruefstein.shared.bootstrap;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Tracks which seed entries this database has already seen, so seeding can run
 * on every boot without repeating itself or undoing an administrator's work.
 */
@ApplicationScoped
public class SeedLedger implements PanacheRepositoryBase<SeedLedgerEntry, String>
{
	/**
	 * Claims an entry for the caller to apply.
	 *
	 * @return {@code true} if this database has never seen the entry, in which
	 *         case it is now recorded and the caller should create it;
	 *         {@code false} if it was applied before and must be left alone
	 */
	public boolean claim(String entryKey)
	{
		if (findByIdOptional(entryKey).isPresent())
		{
			return false;
		}
		persist(new SeedLedgerEntry(entryKey, Instant.now()));
		return true;
	}

	/**
	 * Moves an entry to a new key, keeping when it was applied. The old row
	 * goes even if the new key is already there, so a database that somehow has
	 * both ends up with one.
	 *
	 * @return {@code 1} if the old key existed and was moved, else {@code 0}
	 */
	public int rename(String oldKey, String newKey)
	{
		SeedLedgerEntry old = findById(oldKey);
		if (old == null)
		{
			return 0;
		}
		if (findByIdOptional(newKey).isEmpty())
		{
			persist(new SeedLedgerEntry(newKey, old.getAppliedAt()));
		}
		delete(old);
		return 1;
	}
}
