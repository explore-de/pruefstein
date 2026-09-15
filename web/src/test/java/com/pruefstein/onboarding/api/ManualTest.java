package com.pruefstein.onboarding.api;

import com.pruefstein.onboarding.SetupManual;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestSecurity(user = "alice", roles = {})
class ManualTest
{
	@Inject
	SetupManual manual;

	@Test
	void pageCarriesTheThreeCommands()
	{
		given()
			.when().get("/Manual/index")
			.then()
			.statusCode(200)
			.body(containsString("brew install " + manual.brewFormula()))
			.body(containsString("pruefstein-agent run"));
	}

	/**
	 * The point of the page: somebody who lost the mail can still find out
	 * which server to name and how to name it.
	 */
	@Test
	void loginStepNamesThisServer()
	{
		given()
			.when().get("/Manual/index")
			.then()
			.statusCode(200)
			.body(containsString("pruefstein-agent login --server " + manual.baseUrl()));
	}

	@Test
	void pageLinksTheRepository()
	{
		given()
			.when().get("/Manual/index")
			.then()
			.statusCode(200)
			.body(containsString(manual.repositoryUrl()));
	}
}
