package com.pruefstein.report.api;

import java.time.Instant;

import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * People are shown by name. The address they log in with is what the agent
 * reports, but it is not how anyone reading the list thinks of a colleague — it
 * stays on hover, for when two people share a name.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "admin") })
class ReportsUserNameTest
{
	@Inject
	ReportRepository reportRepository;

	@Inject
	UserRepository userRepository;

	private Long namedId;
	private Long unmatchedId;
	private Long userId;

	@BeforeEach
	void setUp()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			AppUser person = new AppUser();
			person.setFirstname("Hannelore");
			person.setLastname("Namensfeld");
			person.setMail("hannelore.namensfeld@example.com");
			userRepository.persist(person);
			userId = person.id;

			namedId = persist("usernames-device-named", "hannelore.namensfeld@example.com", person);
			unmatchedId = persist("usernames-device-unmatched", "nobody-matched@example.com", null);
		});
	}

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			reportRepository.deleteById(namedId);
			reportRepository.deleteById(unmatchedId);
			userRepository.deleteById(userId);
		});
	}

	private Long persist(String deviceId, String login, AppUser person)
	{
		Report report = new Report();
		report.setDeviceId(deviceId);
		report.setUserId("host");
		report.setKeycloakUser(login);
		report.setAppUser(person);
		report.setCheckedAt(Instant.now());
		report.setStatus(ReportStatus.COMPLIANT);
		reportRepository.persist(report);
		return report.id;
	}

	@Test
	void theListShowsTheNameWithTheMailOnHover()
	{
		given()
			.when().get("/Reports/index?q=usernames-device-named")
			.then()
			.statusCode(200)
			.body(containsString("title=\"hannelore.namensfeld@example.com\">Hannelore Namensfeld</span>"));
	}

	@Test
	void aRunNobodyWasMatchedToFallsBackToItsLogin()
	{
		given()
			.when().get("/Reports/index?q=usernames-device-unmatched")
			.then()
			.statusCode(200)
			.body(containsString(">nobody-matched@example.com</span>"));
	}

	@Test
	void theUserComesBeforeTheDevice()
	{
		// when
		String html = given()
			.when().get("/Reports/index?q=usernames-device-named")
			.then()
			.statusCode(200)
			.extract().asString();

		// then — in the header and in the row
		assertTrue(html.indexOf("setSort('user')") < html.indexOf("setSort('deviceId')"),
			"the user column heads the table");
		assertTrue(html.indexOf("Hannelore Namensfeld") < html.indexOf("usernames-device-named</td>"),
			"the name is read before the device id");
	}

	@Test
	void theReportPageShowsTheNameToo()
	{
		given()
			.when().get("/Reports/show/" + namedId)
			.then()
			.statusCode(200)
			.body(containsString("Hannelore Namensfeld"));
	}
}
