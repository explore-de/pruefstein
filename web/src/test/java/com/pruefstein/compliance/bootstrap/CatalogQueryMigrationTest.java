package com.pruefstein.compliance.bootstrap;

import java.util.List;

import com.pruefstein.compliance.bootstrap.CatalogQueryMigration.Rewrite;
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
 * The migration exists for databases that already seeded SQL which reported
 * compliant devices as non-compliant. What matters is that it corrects exactly
 * that SQL, once, and never what an administrator has since written.
 */
@QuarkusTest
class CatalogQueryMigrationTest
{
	private static final String AUTO_UPDATES = "auto-updates";

	@Inject
	CatalogQueryMigration migration;

	@Inject
	ComplianceLibrary library;

	@Inject
	SeedLedger ledger;

	@Inject
	ComplianceItemRepository itemRepository;

	/**
	 * Seeding is off under {@code %test}, so nothing here is startup's doing —
	 * this clears what the previous test in the class left behind, so each one
	 * begins from an unclaimed ledger and builds the rows it needs itself.
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
	void theOutdatedQueryIsReplacedWithTheCorrectedOne()
	{
		// given — a database that seeded the release with the broken SQL
		LibraryEntry def = entry(AUTO_UPDATES);
		givenSeededCheck(def.name(), outdatedQuery(AUTO_UPDATES), "results.size() > 0 && results[0].value == '1'");

		// when
		int rewritten = migrate();

		// then
		assertEquals(1, rewritten);
		ExpressionCheck check = find(def.name());
		assertEquals(def.query(), check.getQuery());
		assertEquals(def.expression(), check.getExpectedExpression());
	}

	@Test
	void aQueryTheAdministratorRewroteIsLeftAlone()
	{
		// given — someone already worked around the bug themselves
		LibraryEntry def = entry(AUTO_UPDATES);
		String theirQuery = "SELECT value FROM plist WHERE path = '/Library/Preferences/com.apple.SoftwareUpdate.plist';";
		givenSeededCheck(def.name(), theirQuery, "results.size() > 0");

		// when
		int rewritten = migrate();

		// then — their edit outranks our correction
		assertEquals(0, rewritten);
		ExpressionCheck check = find(def.name());
		assertEquals(theirQuery, check.getQuery());
		assertEquals("results.size() > 0", check.getExpectedExpression());
	}

	@Test
	void theRewriteHappensOnlyOnce()
	{
		// given — corrected on the boot after the upgrade
		LibraryEntry def = entry(AUTO_UPDATES);
		givenSeededCheck(def.name(), outdatedQuery(AUTO_UPDATES), "results.size() > 0 && results[0].value == '1'");
		migrate();

		// when — an administrator deliberately puts the old query back, and the
		// application restarts
		QuarkusTransaction.requiringNew().run(() -> {
			ExpressionCheck theirs = (ExpressionCheck)itemRepository.list("name", def.name()).get(0);
			theirs.setQuery(outdatedQuery(AUTO_UPDATES));
		});
		int rewritten = migrate();

		// then — the ledger remembers, so their choice stands
		assertEquals(0, rewritten);
		assertEquals(outdatedQuery(AUTO_UPDATES), find(def.name()).getQuery());
	}

	@Test
	void aDatabaseSeededAfterTheFixHasNothingToRewrite()
	{
		// given — a fresh install, whose checks the seeder created from the
		// corrected catalog
		LibraryEntry def = entry(AUTO_UPDATES);
		givenSeededCheck(def.name(), def.query(), def.expression());

		// when
		int rewritten = migrate();

		// then
		assertEquals(0, rewritten);
		assertEquals(def.query(), find(def.name()).getQuery());
	}

	private LibraryEntry entry(String checkKey)
	{
		return library.find(checkKey).orElseThrow();
	}

	private static String outdatedQuery(String checkKey)
	{
		return CatalogQueryMigration.REWRITES.stream()
			.filter(rewrite -> rewrite.checkKey().equals(checkKey))
			.findFirst()
			.orElseThrow()
			.outdatedQuery();
	}

	/**
	 * Puts a check into the database the way a past release would have, with
	 * its rewrite key unclaimed so the migration still has it to do.
	 */
	private void givenSeededCheck(String name, String query, String expression)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			ExpressionCheck check = new ExpressionCheck();
			check.setName(name);
			check.setQuery(query);
			check.setExpectedExpression(expression);
			itemRepository.persist(check);
		});
	}

	private void reset()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			for (Rewrite rewrite : CatalogQueryMigration.REWRITES)
			{
				itemRepository.delete("name", entry(rewrite.checkKey()).name());
				ledger.deleteById(rewrite.ledgerKey());
			}
		});
	}

	private int migrate()
	{
		return QuarkusTransaction.requiringNew().call(() -> migration.migrate());
	}

	private ExpressionCheck find(String name)
	{
		List<ComplianceItem> found = QuarkusTransaction.requiringNew()
			.call(() -> itemRepository.list("name", name));
		return found.isEmpty() ? null : (ExpressionCheck)found.get(0);
	}
}
