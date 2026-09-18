package com.pruefstein.compliance.bootstrap;

import java.util.List;

import com.pruefstein.compliance.domain.AppBlacklistCheck;
import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.library.ComplianceLibrary;
import com.pruefstein.compliance.library.LibraryEntry;
import com.pruefstein.compliance.library.LibraryInstantiator;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.shared.bootstrap.SeedLedger;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seeding runs on every boot, so what matters is not that it creates the
 * catalog once but that it never touches what an administrator did afterwards.
 */
@QuarkusTest
class CatalogSeederTest
{
	@Inject
	CatalogSeeder seeder;

	@Inject
	ComplianceLibrary library;

	@Inject
	LibraryInstantiator instantiator;

	@Inject
	SeedLedger ledger;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceGroupRepository groupRepository;

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			for (LibraryEntry entry : library.entries())
			{
				itemRepository.delete("name", entry.name());
				ledger.deleteById(entry.key());
			}
			// Only once every check is gone, or the group is still referenced
			for (LibraryEntry entry : library.entries())
			{
				if (entry.group() != null)
				{
					groupRepository.delete("name", entry.group());
				}
			}
		});
	}

	@Test
	void theFirstRunCreatesTheWholeCatalog()
	{
		// when
		int applied = seed();

		// then
		assertEquals(library.entries().size(), applied);
		for (LibraryEntry entry : library.entries())
		{
			ComplianceItem item = find(entry.name());
			assertNotNull(item, entry.name() + " should have been seeded");
			assertEquals(entry.key(), item.getLibraryKey(), entry.name() + " should remember its library entry");
			assertEquals(entry.control(), item.getControl(), entry.name() + " should carry its control");
			if (entry.group() != null)
			{
				assertTrue(groupRepository.find("name", entry.group()).firstResultOptional().isPresent(),
					entry.group() + " should have been created");
			}
		}
	}

	@Test
	void theGeneratedCheckIsSeededWithoutAGroup()
	{
		// when
		seed();

		// then — its SQL comes from the blocked-app rules, and it belongs to no
		// group on the Groups & Items screen
		ComplianceItem blacklist = find("No blacklisted applications installed");
		assertInstanceOf(AppBlacklistCheck.class, blacklist);
		assertNull(blacklist.getGroup());
	}

	@Test
	void aSecondRunAddsNothing()
	{
		// given
		seed();

		// when — every boot from here on
		int applied = seed();

		// then
		assertEquals(0, applied);
		assertEquals(1, itemRepository.count("name", "FileVault enabled"));
	}

	@Test
	void aCheckTheAdministratorDeletedStaysDeleted()
	{
		// given
		seed();
		QuarkusTransaction.requiringNew().run(() -> itemRepository.delete("name", "Gatekeeper enabled"));

		// when
		int applied = seed();

		// then — the ledger remembers it was applied, so it is not resurrected
		assertEquals(0, applied);
		assertNull(find("Gatekeeper enabled"));
	}

	@Test
	void anEditedCheckIsLeftAlone()
	{
		// given — an administrator tightens the screen lock grace period
		seed();
		QuarkusTransaction.requiringNew().run(() -> {
			ExpressionCheck check = (ExpressionCheck)itemRepository
				.list("name", "Screen lock requires a password").get(0);
			check.setExpectedExpression("results.size() > 0 && results[0].grace_period <= 60");
		});

		// when
		seed();

		// then
		ExpressionCheck check = (ExpressionCheck)find("Screen lock requires a password");
		assertEquals("results.size() > 0 && results[0].grace_period <= 60", check.getExpectedExpression());
	}

	@Test
	void aCheckAddedToTheCatalogLaterIsPickedUp()
	{
		// given — everything applied, as after an upgrade of an existing
		// install
		seed();

		// when — the next release adds an entry, which is a key the ledger has
		// never seen
		QuarkusTransaction.requiringNew().run(() -> ledger.deleteById("gatekeeper"));
		QuarkusTransaction.requiringNew().run(() -> itemRepository.delete("name", "Gatekeeper enabled"));
		int applied = seed();

		// then — only that one is created
		assertEquals(1, applied);
		assertNotNull(find("Gatekeeper enabled"));
	}

	@Test
	void aDeletedGroupIsRecreatedForTheCheckThatNeedsIt()
	{
		// given — the group is gone along with the ledger entry of one of its
		// checks. Taken from the library rather than written out, so filing an
		// entry under another theme does not fail this test.
		LibraryEntry filevault = library.find("filevault").orElseThrow();
		String groupName = filevault.group();
		seed();
		QuarkusTransaction.requiringNew().run(() -> {
			// A theme holds many checks, and the group cannot go while any of
			// them still points at it
			for (ComplianceGroup group : groupRepository.list("name", groupName))
			{
				itemRepository.delete("group", group);
				groupRepository.delete(group);
			}
			ledger.deleteById(filevault.key());
		});

		// when
		seed();

		// then — a check has to live somewhere
		assertNotNull(find(filevault.name()).getGroup());
		assertEquals(groupName, find(filevault.name()).getGroup().getName());
	}

	@Test
	void checksSeededBeforeTheLibraryAreLinkedToTheirEntry()
	{
		// given — an install seeded before checks remembered their entry
		seed();
		QuarkusTransaction.requiringNew().run(() -> itemRepository.update("libraryKey = null"));

		// when
		int adopted = QuarkusTransaction.requiringNew().call(() -> instantiator.adoptUntagged(library.entries()));

		// then — the Library screen sees them as in use and does not offer them
		assertEquals(library.entries().size(), adopted);
		assertEquals("filevault", find("FileVault enabled").getLibraryKey());
	}

	private int seed()
	{
		return QuarkusTransaction.requiringNew().call(() -> seeder.seed());
	}

	private ComplianceItem find(String name)
	{
		List<ComplianceItem> found = QuarkusTransaction.requiringNew()
			.call(() -> itemRepository.list("name", name));
		return found.isEmpty() ? null : found.get(0);
	}
}
