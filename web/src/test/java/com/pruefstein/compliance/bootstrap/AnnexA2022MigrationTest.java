package com.pruefstein.compliance.bootstrap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
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
 * A database seeded before the 2022 revision has its checks in groups named for
 * the old domains. Re-filing them has to move ours without disturbing anything
 * an administrator put there.
 *
 * <p>
 * Everything a test needs it creates itself and finds again by id, never by
 * name: other classes use the same real names for their own rows, and which
 * class runs first differs between machines.
 */
@QuarkusTest
class AnnexA2022MigrationTest
{
	private static final String LEDGER_KEY = "annex-a-2022#themes";
	private static final String OLD_CRYPTO_GROUP = "A.10 Cryptography";
	private static final String OWN_GROUP = "Encryption, filed our way";
	private static final String FILEVAULT = "FileVault enabled";

	@Inject
	AnnexA2022Migration migration;

	@Inject
	SeedLedger ledger;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceGroupRepository groupRepository;

	/** The groups this test made, by name, so two checks can share one. */
	private final Map<String, Long> groups = new HashMap<>();

	private final List<Long> checks = new ArrayList<>();

	/**
	 * Seeding is off under {@code %test}, so the re-filing has not run at
	 * startup; each test begins from an unclaimed ledger all the same.
	 */
	@BeforeEach
	void unclaimTheLedger()
	{
		QuarkusTransaction.requiringNew().run(() -> ledger.deleteById(LEDGER_KEY));
	}

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			checks.forEach(itemRepository::deleteById);
			// The migration may already have dropped an emptied group
			groups.values().forEach(groupRepository::deleteById);
			ledger.deleteById(LEDGER_KEY);
		});
	}

	@Test
	void aCheckInAnOldDomainGroupMovesToItsThemeAndGainsItsControl()
	{
		// given — the row a pre-2022 release left behind
		Long filevault = givenLegacyCheck(FILEVAULT, OLD_CRYPTO_GROUP);

		// when
		int refiled = migrate();

		// then
		assertTrue(refiled > 0, "the seeded check should have been re-filed");
		ComplianceItem check = find(filevault);
		assertEquals("A.8 Technological controls", check.getGroup().getName());
		assertEquals("A.8.24", check.getControl());
	}

	@Test
	void theEmptiedOldGroupIsRemoved()
	{
		// given
		givenLegacyCheck(FILEVAULT, OLD_CRYPTO_GROUP);

		// when
		migrate();

		// then — nothing is left in it, so it stops cluttering the screen
		assertNull(findGroup(groups.get(OLD_CRYPTO_GROUP)), OLD_CRYPTO_GROUP + " should have been dropped once empty");
	}

	@Test
	void anOldGroupKeepingTheAdministratorsOwnCheckSurvives()
	{
		// given — they filed a check of their own alongside ours
		givenLegacyCheck(FILEVAULT, OLD_CRYPTO_GROUP);
		Long theirs = givenLegacyCheck("Our own crypto check", OLD_CRYPTO_GROUP);

		// when
		migrate();

		// then — deleting the group would take their work with it
		Long oldGroup = groups.get(OLD_CRYPTO_GROUP);
		assertNotNull(findGroup(oldGroup), OLD_CRYPTO_GROUP + " should have been kept");
		assertEquals(oldGroup, find(theirs).getGroup().id);
	}

	@Test
	void aCheckTheAdministratorAlreadyReFiledStaysInTheirGroup()
	{
		// given — before the upgrade they had moved our check into a group of
		// their own
		Long filevault = givenLegacyCheck(FILEVAULT, OWN_GROUP);

		// when
		migrate();

		// then — where it sits is their call; the control is a fact about the
		// check and is recorded regardless
		ComplianceItem check = find(filevault);
		assertEquals(groups.get(OWN_GROUP), check.getGroup().id);
		assertEquals("A.8.24", check.getControl());
	}

	@Test
	void theGeneratedCheckKeepsItsControlWithoutGainingAGroup()
	{
		// given
		Long blacklist = givenLegacyCheck("No blacklisted applications installed", OLD_CRYPTO_GROUP);

		// when
		migrate();

		// then — it belongs on the Blocked Apps screen, not in a group, but the
		// control still applies to it
		ComplianceItem check = find(blacklist);
		assertNull(check.getGroup());
		assertEquals("A.8.19", check.getControl());
	}

	@Test
	void theReFilingHappensOnlyOnce()
	{
		// given — migrated, then an administrator deliberately moves a check
		// back into a group of their own
		Long filevault = givenLegacyCheck(FILEVAULT, OLD_CRYPTO_GROUP);
		migrate();
		QuarkusTransaction.requiringNew().run(() -> itemRepository.findById(filevault).setGroup(ownGroup("Where I want it")));

		// when — the application restarts
		int refiled = migrate();

		// then — the ledger remembers, so their choice stands
		assertEquals(0, refiled);
		assertEquals(groups.get("Where I want it"), find(filevault).getGroup().id);
	}

	/**
	 * @return the id of the check, which is the only reliable way back to it
	 */
	private Long givenLegacyCheck(String name, String groupName)
	{
		return QuarkusTransaction.requiringNew().call(() -> {
			ExpressionCheck check = new ExpressionCheck();
			check.setName(name);
			check.setQuery("SELECT 1;");
			check.setExpectedExpression("results.size() > 0");
			check.setGroup(ownGroup(groupName));
			itemRepository.persist(check);
			checks.add(check.id);
			return check.id;
		});
	}

	/**
	 * A group of this test's own, created rather than looked up: a group of the
	 * same name may already exist, left there by another class.
	 */
	private ComplianceGroup ownGroup(String name)
	{
		Long id = groups.get(name);
		if (id != null)
		{
			return groupRepository.findById(id);
		}
		ComplianceGroup group = new ComplianceGroup();
		group.setName(name);
		groupRepository.persist(group);
		groups.put(name, group.id);
		return group;
	}

	private int migrate()
	{
		return QuarkusTransaction.requiringNew().call(() -> migration.migrate());
	}

	private ComplianceItem find(Long id)
	{
		return QuarkusTransaction.requiringNew().call(() -> itemRepository.findById(id));
	}

	private ComplianceGroup findGroup(Long id)
	{
		return QuarkusTransaction.requiringNew().call(() -> groupRepository.findById(id));
	}
}
