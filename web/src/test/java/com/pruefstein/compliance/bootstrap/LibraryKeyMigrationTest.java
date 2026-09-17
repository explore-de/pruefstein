package com.pruefstein.compliance.bootstrap;

import java.util.List;

import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.library.ComplianceLibrary;
import com.pruefstein.compliance.library.LibraryEntry;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.shared.bootstrap.SeedLedger;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A database seeded while the library keys carried the 2013 prefixes must come
 * through the rename seeing every one of its checks as already seeded and
 * already in force.
 */
@QuarkusTest
class LibraryKeyMigrationTest
{
	private static final String LEDGER_KEY = "library-keys#drop-2013-prefixes";
	private static final String OLD_KEY = "a10.filevault";
	private static final String NEW_KEY = "filevault";
	private static final String OLD_REWRITE_KEY = "a12.auto-updates#plist";
	private static final String NEW_REWRITE_KEY = "auto-updates#plist";
	private static final String FILEVAULT = "FileVault enabled";

	@Inject
	LibraryKeyMigration migration;

	@Inject
	ComplianceLibrary library;

	@Inject
	SeedLedger ledger;

	@Inject
	ComplianceItemRepository itemRepository;

	/**
	 * The rename runs at startup and claims its ledger key; each test starts
	 * from before that, with the rows it needs built by hand.
	 */
	@BeforeEach
	void clearPreviousTest()
	{
		reset();
	}

	@AfterEach
	void tearDown()
	{
		reset();
	}

	@Test
	void aCheckSeededUnderAnOldKeyIsRenamedAndStaysSeeded()
	{
		// given — a database from a release that used the 2013 prefixes
		QuarkusTransaction.requiringNew().run(() -> {
			ledger.claim(OLD_KEY);
			ExpressionCheck check = new ExpressionCheck();
			check.setName(FILEVAULT);
			check.setLibraryKey(OLD_KEY);
			check.setQuery("SELECT 1;");
			check.setExpectedExpression("results.size() > 0");
			itemRepository.persist(check);
		});

		// when
		int renamed = migrate();

		// then — the ledger and the check both know the new key, and the
		// seeder will find the entry already claimed rather than seed it again
		assertEquals(2, renamed);
		assertEquals(NEW_KEY, find(FILEVAULT).getLibraryKey());
		QuarkusTransaction.requiringNew().run(() -> {
			assertNull(ledger.findById(OLD_KEY), "the old ledger row should be gone");
			assertFalse(ledger.claim(NEW_KEY), "the new key should count as already seeded");
		});
	}

	@Test
	void theRewriteLedgerKeysMoveWithTheirChecks()
	{
		// given
		QuarkusTransaction.requiringNew().run(() -> ledger.claim(OLD_REWRITE_KEY));

		// when
		migrate();

		// then — a rewrite done under the old name is not done again
		QuarkusTransaction.requiringNew().run(() -> {
			assertNull(ledger.findById(OLD_REWRITE_KEY));
			assertFalse(ledger.claim(NEW_REWRITE_KEY));
		});
	}

	@Test
	void aDatabaseThatNeverSawTheOldKeysHasNothingToRename()
	{
		// when
		int renamed = migrate();

		// then
		assertEquals(0, renamed);
	}

	@Test
	void theRenameHappensOnlyOnce()
	{
		// given — renamed on the boot after the upgrade
		migrate();
		QuarkusTransaction.requiringNew().run(() -> ledger.claim(OLD_KEY));

		// when — the application restarts
		int renamed = migrate();

		// then — the ledger remembers, so nothing is touched twice
		assertEquals(0, renamed);
	}

	@Test
	void everyRenameLeadsToAKeyTheLibraryStillHas()
	{
		// then — a mapping onto a key that no longer exists would move a check
		// onto an entry the Library screen cannot show, and an old key without
		// a prefix would mean the map is not what it says it is
		List<String> keys = library.entries().stream().map(LibraryEntry::key).toList();
		LibraryKeyMigration.RENAMES.forEach((old, renamed) -> {
			assertTrue(old.matches("a(9|10|12|13)\\..+"), old + " does not carry a 2013 prefix");
			String entryKey = renamed.contains("#") ? renamed.substring(0, renamed.indexOf('#')) : renamed;
			assertTrue(keys.contains(entryKey), old + " maps to " + renamed + ", which the library does not have");
		});
		for (String key : keys)
		{
			assertFalse(key.matches("a(9|10|12|13)\\..+"), key + " still carries a 2013 prefix");
		}
	}

	private int migrate()
	{
		return QuarkusTransaction.requiringNew().call(() -> migration.migrate());
	}

	private ComplianceItem find(String name)
	{
		return QuarkusTransaction.requiringNew().call(() -> itemRepository.find("name", name).firstResult());
	}

	private void reset()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			itemRepository.delete("name", FILEVAULT);
			for (String key : List.of(LEDGER_KEY, OLD_KEY, NEW_KEY, OLD_REWRITE_KEY, NEW_REWRITE_KEY))
			{
				ledger.deleteById(key);
			}
		});
	}
}
