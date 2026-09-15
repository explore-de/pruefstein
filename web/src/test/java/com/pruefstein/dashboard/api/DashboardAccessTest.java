package com.pruefstein.dashboard.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * The estate's totals are an admin's business. A regular user asking for the
 * same address gets their own machines instead — the rule the report list has
 * always applied, now applied one page earlier.
 */
@QuarkusTest
class DashboardAccessTest
{
	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void adminGetsTheFleet()
	{
		given()
			.when().get("/")
			.then()
			.statusCode(200)
			.body(containsString("Fleet compliance overview"))
			.body(containsString("CHECKS DEFINED"));
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	void plainUserNeverSeesFleetTotals()
	{
		given()
			.when().get("/")
			.then()
			.statusCode(200)
			.body(containsString("Your Devices"))
			.body(not(containsString("Fleet compliance overview")))
			.body(not(containsString("CHECKS DEFINED")))
			.body(not(containsString("registered accounts")));
	}

	/**
	 * Nobody has ever reported for alice in a test database, which is exactly
	 * the state a new colleague signs in to. What they are owed then is the
	 * walkthrough, not a grid of their own zeroes.
	 */
	@Test
	@TestSecurity(user = "alice", roles = {})
	void aReaderWithNoDevicesGetsTheWalkthrough()
	{
		given()
			.when().get("/")
			.then()
			.statusCode(200)
			.body(containsString("NOTHING CHECKED YET"))
			.body(containsString("./agent/bin/install.sh"))
			.body(containsString("pruefstein-agent run"));
	}
}
