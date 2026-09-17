package com.pruefstein.compliance.repository;

import java.util.List;

import com.pruefstein.compliance.domain.ComplianceGroup;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestTransaction
class ComplianceGroupRepositoryTest
{
	@Inject
	ComplianceGroupRepository repository;

	@Test
	void testPersistAndFindById()
	{
		// given
		ComplianceGroup group = new ComplianceGroup();
		group.setName("A.10 Cryptography");
		repository.persist(group);

		// when
		ComplianceGroup found = repository.findById(group.id);

		// then
		assertNotNull(found);
		assertEquals("A.10 Cryptography", found.getName());
	}

	@Test
	void testFindByIdReturnsNullForUnknownId()
	{
		// given (empty DB)

		// when / then
		assertNull(repository.findById(Long.MAX_VALUE));
	}

	@Test
	void testListAll()
	{
		// given
		ComplianceGroup g1 = new ComplianceGroup();
		g1.setName("Group A");
		repository.persist(g1);

		ComplianceGroup g2 = new ComplianceGroup();
		g2.setName("Group B");
		repository.persist(g2);

		// when
		List<ComplianceGroup> all = repository.listAll();

		// then
		assertTrue(all.stream().anyMatch(g -> "Group A".equals(g.getName())));
		assertTrue(all.stream().anyMatch(g -> "Group B".equals(g.getName())));
	}

	@Test
	void testDeleteById()
	{
		// given
		ComplianceGroup group = new ComplianceGroup();
		group.setName("To Delete");
		repository.persist(group);
		Long id = group.id;

		// when
		repository.deleteById(id);

		// then
		assertNull(repository.findById(id));
	}

	@Test
	void findOrCreateByNameReusesTheActiveGroup()
	{
		// given
		ComplianceGroup active = new ComplianceGroup();
		active.setName("A.8 Technological controls");
		repository.persist(active);

		// when
		ComplianceGroup found = repository.findOrCreateByName("A.8 Technological controls");

		// then
		assertEquals(active.id, found.id);
	}

	@Test
	void findOrCreateByNameSkipsARetiredGroup()
	{
		// given — a group of this name existed, and an admin retired it
		ComplianceGroup retired = new ComplianceGroup();
		retired.setName("A.8 Technological controls");
		retired.retire();
		repository.persist(retired);

		// when
		ComplianceGroup found = repository.findOrCreateByName("A.8 Technological controls");

		// then — a check filed here has to be reachable, so it gets a fresh one
		assertNotEquals(retired.id, found.id);
		assertFalse(found.isRetired());
		assertEquals("A.8 Technological controls", found.getName());
	}

	@Test
	void testUpdateName()
	{
		// given
		ComplianceGroup group = new ComplianceGroup();
		group.setName("Original Name");
		repository.persist(group);

		// when
		group.setName("Updated Name");

		// then
		ComplianceGroup found = repository.findById(group.id);
		assertEquals("Updated Name", found.getName());
	}
}
