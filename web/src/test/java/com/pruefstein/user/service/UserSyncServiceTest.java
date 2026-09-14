package com.pruefstein.user.service;

import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestTransaction
class UserSyncServiceTest
{
	@Inject
	UserSyncService userSyncService;

	@Inject
	UserRepository userRepository;

	@Test
	void createsNewUserWhenNotFound()
	{
		// given (no user with subject "sub-new" exists)

		// when
		userSyncService.syncUser("sub-new", "new@example.com", "Alice", "Smith");

		// then
		AppUser user = userRepository.findBySubject("sub-new").orElseThrow();
		assertEquals("sub-new", user.getOidcSubject());
		assertEquals("new@example.com", user.getMail());
		assertEquals("Alice", user.getFirstname());
		assertEquals("Smith", user.getLastname());
	}

	@Test
	void updatesExistingUserFields()
	{
		// given
		userSyncService.syncUser("sub-existing", "old@example.com", "Bob", "Jones");

		// when
		userSyncService.syncUser("sub-existing", "new@example.com", "Robert", "Jones");

		// then
		AppUser user = userRepository.findBySubject("sub-existing").orElseThrow();
		assertEquals("new@example.com", user.getMail());
		assertEquals("Robert", user.getFirstname());
	}

	@Test
	void usesSubjectAsFirstnameWhenFirstnameIsNull()
	{
		// given (no pre-existing user)

		// when
		userSyncService.syncUser("sub-nofirst", "x@example.com", null, "Doe");

		// then
		AppUser user = userRepository.findBySubject("sub-nofirst").orElseThrow();
		assertEquals("sub-nofirst", user.getFirstname());
		assertEquals("Doe", user.getLastname());
	}

	@Test
	void usesEmptyStringAsLastnameWhenLastnameIsNull()
	{
		// given (no pre-existing user)

		// when
		userSyncService.syncUser("sub-nolast", "x@example.com", "Jane", null);

		// then
		AppUser user = userRepository.findBySubject("sub-nolast").orElseThrow();
		assertEquals("Jane", user.getFirstname());
		assertEquals("", user.getLastname());
	}

	@Test
	void adoptsHandAddedUserWithMatchingMail()
	{
		// given a user an admin typed in, who has never logged in
		AppUser typedIn = new AppUser();
		typedIn.setFirstname("Vlad");
		typedIn.setLastname("Knyshov");
		typedIn.setMail("adopt-me@example.com");
		userRepository.persist(typedIn);

		// when that person's agent reports for the first time
		AppUser synced = userSyncService.syncUser("sub-adopt", "adopt-me@example.com", "Vlad", "Knyshov");

		// then the typed-in row was claimed rather than a second one created
		assertEquals(typedIn.id, synced.id);
		assertEquals("sub-adopt", synced.getOidcSubject());
		assertEquals(1, userRepository.count("mail", "adopt-me@example.com"));
	}

	@Test
	void adoptionMatchesMailCaseInsensitively()
	{
		// given
		AppUser typedIn = new AppUser();
		typedIn.setFirstname("Alena");
		typedIn.setLastname("Knyshova");
		typedIn.setMail("Mixed.Case@Example.com");
		userRepository.persist(typedIn);

		// when the provider hands the address back lowercased
		AppUser synced = userSyncService.syncUser("sub-case", "mixed.case@example.com", "Alena", "Knyshova");

		// then
		assertEquals(typedIn.id, synced.id);
	}

	@Test
	void doesNotAdoptRowThatAlreadyBelongsToAnotherSubject()
	{
		// given a row already claimed by one identity
		userSyncService.syncUser("sub-owner", "shared@example.com", "First", "Owner");

		// when a different identity arrives under the same address
		AppUser second = userSyncService.syncUser("sub-intruder", "shared@example.com", "Second", "Comer");

		// then it gets its own row and the first keeps its subject
		assertEquals("sub-intruder", second.getOidcSubject());
		assertEquals("sub-owner", userRepository.findBySubject("sub-owner").orElseThrow().getOidcSubject());
		assertEquals(2, userRepository.count("mail", "shared@example.com"));
	}

	@Test
	void createsNewUserWhenSyncCarriesNoMail()
	{
		// given a hand-added row that could only be matched on its address
		AppUser typedIn = new AppUser();
		typedIn.setFirstname("No");
		typedIn.setLastname("Mail");
		typedIn.setMail("nomail-match@example.com");
		userRepository.persist(typedIn);

		// when a sync arrives with no address to match on
		AppUser synced = userSyncService.syncUser("sub-nomail", null, "No", "Mail");

		// then nothing is adopted
		assertNotEquals(typedIn.id, synced.id);
		assertNull(typedIn.getOidcSubject());
	}

	@Test
	void doesNotOverwriteFieldsWithNullOnUpdate()
	{
		// given
		userSyncService.syncUser("sub-nullupdate", "orig@example.com", "Orig", "Name");

		// when
		userSyncService.syncUser("sub-nullupdate", null, null, null);

		// then
		AppUser user = userRepository.findBySubject("sub-nullupdate").orElseThrow();
		assertEquals("orig@example.com", user.getMail());
		assertEquals("Orig", user.getFirstname());
		assertEquals("Name", user.getLastname());
	}
}
