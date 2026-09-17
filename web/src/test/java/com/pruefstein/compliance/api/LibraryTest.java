package com.pruefstein.compliance.api;

import java.util.List;

import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Seeding is disabled in tests, so every library entry starts out not in use.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
class LibraryTest
{
	private static final String KEY = "gatekeeper";
	private static final String NAME = "Gatekeeper enabled";
	private static final String GROUP = "A.8 Technological controls";

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceGroupRepository groupRepository;

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			itemRepository.delete("libraryKey", KEY);
			groupRepository.delete("name", GROUP);
		});
	}

	@Test
	void indexListsTheLibrary()
	{
		// when / then
		given()
			.when().get("/Library/index")
			.then()
			.statusCode(200)
			.body(containsString("Library"))
			.body(containsString(NAME))
			.body(containsString("A.8.7"))
			.body(containsString("SELECT assessments_enabled FROM gatekeeper;"));
	}

	@Test
	void addingAnEntryCreatesTheCheckInItsGroup()
	{
		// when
		add(KEY);

		// then
		List<ComplianceItem> created = items();
		assertEquals(1, created.size());
		ExpressionCheck check = (ExpressionCheck)created.get(0);
		assertEquals(NAME, check.getName());
		assertEquals("A.8.7", check.getControl());
		assertEquals("results.size() > 0 && results[0].assessments_enabled == '1'", check.getExpectedExpression());
		assertEquals(GROUP, QuarkusTransaction.requiringNew()
			.call(() -> itemRepository.findById(check.id).getGroup().getName()));
	}

	@Test
	void anEntryInForceIsNotAddedTwice()
	{
		// given
		add(KEY);

		// when
		add(KEY);

		// then
		assertEquals(1, items().size());
	}

	@Test
	void aRetiredCheckCanBeAddedAgain()
	{
		// given
		add(KEY);
		QuarkusTransaction.requiringNew().run(() -> itemRepository.find("libraryKey", KEY).firstResult().retire());

		// when
		add(KEY);

		// then — the retired one stays for the reports that name it
		List<ComplianceItem> all = items();
		assertEquals(2, all.size());
		assertEquals(1, all.stream().filter(item -> !item.isRetired()).count());
	}

	@Test
	void anUnknownKeyIsNotFound()
	{
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("key", "does-not-exist")
			.redirects().follow(false)
			.when().post("/Library/add")
			.then()
			.statusCode(404);
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	void nonAdminCannotAdd()
	{
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("key", KEY)
			.when().post("/Library/add")
			.then()
			.statusCode(403);

		assertEquals(0, items().size());
	}

	private void add(String key)
	{
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("key", key)
			.redirects().follow(false)
			.when().post("/Library/add")
			.then()
			.statusCode(lessThan(400));
	}

	private List<ComplianceItem> items()
	{
		return QuarkusTransaction.requiringNew().call(() -> itemRepository.list("libraryKey", KEY));
	}
}
