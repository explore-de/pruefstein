package com.pruefstein.compliance.bootstrap;

import java.util.List;

import com.pruefstein.compliance.bootstrap.RetiredCheckMigration.Retirement;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
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
 * A withdrawn check has to leave the databases that already seeded it, without
 * taking the reports that name it and without overruling an administrator who
 * made it their own.
 */
@QuarkusTest
class RetiredCheckMigrationTest
{
	private static final Retirement SCREEN_LOCK = RetiredCheckMigration.RETIREMENTS.get(0);

	@Inject
	RetiredCheckMigration migration;

	@Inject
	SeedLedger ledger;

	@Inject
	ComplianceItemRepository itemRepository;

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
	void aCheckStillCarryingItsShippedSqlIsRetired()
	{
		for (String shipped : SCREEN_LOCK.shippedQueries())
		{
			reset();
			givenSeededCheck(shipped);

			assertEquals(1, retire(), shipped);
			assertTrue(find().isRetired(), shipped);
		}
	}

	@Test
	void aCheckTheAdministratorRewroteIsLeftAlone()
	{
		// They are measuring something of their own with it, and withdrawing
		// that is their call
		givenSeededCheck("SELECT enabled FROM screenlock;");

		assertEquals(0, retire());
		assertFalse(find().isRetired());
	}

	@Test
	void theRetirementHappensOnlyOnce()
	{
		givenSeededCheck(SCREEN_LOCK.shippedQueries().get(0));
		retire();

		// The check is gone from the library, so nothing can create it again —
		// but the ledger is what keeps a restored database from being retired a
		// second time and losing an admin's later decision
		assertEquals(0, retire());
	}

	@Test
	void aDatabaseThatNeverHadTheCheckHasNothingToRetire()
	{
		assertEquals(0, retire());
		assertNull(find());
	}

	private void givenSeededCheck(String query)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			ExpressionCheck check = new ExpressionCheck();
			check.setName(SCREEN_LOCK.checkName());
			check.setQuery(query);
			check.setExpectedExpression("results.size() > 0");
			itemRepository.persist(check);
		});
	}

	private void reset()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			itemRepository.delete("name", SCREEN_LOCK.checkName());
			ledger.deleteById(SCREEN_LOCK.ledgerKey());
		});
	}

	private int retire()
	{
		return QuarkusTransaction.requiringNew().call(() -> migration.retire());
	}

	private ComplianceItem find()
	{
		List<ComplianceItem> found = QuarkusTransaction.requiringNew()
			.call(() -> itemRepository.list("name", SCREEN_LOCK.checkName()));
		return found.isEmpty() ? null : found.get(0);
	}
}
