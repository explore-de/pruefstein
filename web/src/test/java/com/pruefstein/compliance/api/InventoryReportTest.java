package com.pruefstein.compliance.api;

import java.time.Instant;
import java.util.List;

import com.pruefstein.compliance.domain.AppMatcher;
import com.pruefstein.compliance.domain.BlockedApp;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.domain.InstalledApp;
import com.pruefstein.compliance.domain.MatcherType;
import com.pruefstein.compliance.repository.BlockedAppRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.compliance.repository.InstalledAppRepository;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "inventory-user", roles = "admin")
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "inventory-user") })
class InventoryReportTest
{
	private static final String DEVICE = "inventory-test-device";

	@Inject
	InstalledAppRepository installedAppRepository;

	@Inject
	BlockedAppRepository blockedAppRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	ReportRepository reportRepository;

	@Inject
	DeviceRepository deviceRepository;

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			installedAppRepository.delete("report.deviceId", DEVICE);
			reportRepository.delete("deviceId", DEVICE);
			deviceRepository.delete("deviceId", DEVICE);
			blockedAppRepository.delete("label like ?1", "ZZ Inv%");
		});
	}

	private String payload()
	{
		return """
			{"deviceId":"%s","userId":"inventory-user","checkedAt":"%s","results":[],
			 "installedApps":[
			   {"source":"app","name":"Nextcloud.app","identifier":"com.nextcloud.desktopclient",
			    "version":"34.0.0","path":"/Applications/Nextcloud.app"},
			   {"source":"brew:formula","name":"wget","identifier":"wget","version":"1.25.0",
			    "path":"/opt/homebrew/Cellar/wget"}]}
			""".formatted(DEVICE, Instant.now());
	}

	private Long pushReport()
	{
		String url = given()
			.contentType(JSON).body(payload())
			.when().post("/api/reports")
			.then().statusCode(200)
			.extract().path("reportUrl");
		return Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
	}

	@Test
	void inventoryIsPersistedWithTheReport()
	{
		// given / when
		pushReport();

		// then
		QuarkusTransaction.requiringNew().run(() -> {
			List<InstalledApp> apps = installedAppRepository.list("report.deviceId", DEVICE);
			assertEquals(2, apps.size());
			assertTrue(apps.stream().anyMatch(a -> "Nextcloud.app".equals(a.getName())));
			assertTrue(apps.stream().anyMatch(a -> a.isFromHomebrew() && "wget".equals(a.getName())));
		});
	}

	@Test
	void resubmittingToAnOpenReportReplacesTheInventoryInsteadOfAccumulating()
	{
		// given — a failing check keeps the report open, so the next submission
		// lands on the same report rather than creating a new one
		Long[] itemId = new Long[1];
		QuarkusTransaction.requiringNew().run(() -> {
			ExpressionCheck check = new ExpressionCheck();
			check.setName("ZZ Inv Failing Check");
			check.setQuery("SELECT 1;");
			check.setExpectedExpression("results.size() > 99");
			itemRepository.persist(check);
			itemId[0] = check.id;
		});
		String body = """
			{"deviceId":"%s","userId":"inventory-user","checkedAt":"%s",
			 "results":[{"itemId":%d,"passed":false,"output":"[]"}],
			 "installedApps":[
			   {"source":"app","name":"Nextcloud.app","identifier":"com.nextcloud.desktopclient",
			    "version":"34.0.0","path":"/Applications/Nextcloud.app"},
			   {"source":"brew:formula","name":"wget","identifier":"wget","version":"1.25.0",
			    "path":"/opt/homebrew/Cellar/wget"}]}
			""".formatted(DEVICE, Instant.now(), itemId[0]);

		String firstUrl = given().contentType(JSON).body(body)
			.when().post("/api/reports").then().statusCode(200).extract().path("reportUrl");

		// when — the same device reports again
		String secondUrl = given().contentType(JSON).body(body)
			.when().post("/api/reports").then().statusCode(200).extract().path("reportUrl");

		// then — same report, and its inventory was replaced, not appended
		assertEquals(firstUrl, secondUrl);
		QuarkusTransaction.requiringNew()
			.run(() -> assertEquals(2, installedAppRepository.list("report.deviceId", DEVICE).size()));

		// cleanup
		QuarkusTransaction.requiringNew().run(() -> {
			resultRepository.delete("item.id", itemId[0]);
			itemRepository.deleteById(itemId[0]);
		});
	}

	@Test
	void reportListsInventoryAndFlagsBlockedApps()
	{
		// given — one of the two reported apps is forbidden
		QuarkusTransaction.requiringNew().run(() -> {
			BlockedApp rule = new BlockedApp();
			rule.setLabel("ZZ Inv Nextcloud");
			rule.setReason("Not approved");
			rule.setEnabled(true);
			rule.setMatchers(List.of(new AppMatcher(MatcherType.BUNDLE_ID, "com.nextcloud.%")));
			blockedAppRepository.persist(rule);
		});
		Long reportId = pushReport();

		// when / then — both listed, the forbidden one flagged, the other
		// offering a one-click block
		given()
			.when().get("/Reports/show/" + reportId)
			.then()
			.statusCode(200)
			.body(allOf(
				containsString("Installed Applications"),
				containsString("Nextcloud.app"),
				containsString("wget"),
				// the catalogue is not loaded in tests, so the fallback link
				containsString("href=\"https://formulae.brew.sh/formula/wget\""),
				containsString("BLOCKED"),
				containsString("BLOCK")));
	}

	@Test
	void blockingFromTheReportCreatesARuleForThatInstallRoute()
	{
		// given
		Long reportId = pushReport();

		// when — block the Homebrew package straight from the report
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("label", "ZZ Inv wget")
			.formParam("matcherType", "HOMEBREW")
			.formParam("pattern", "wget")
			.formParam("reportId", reportId)
			.when().post("/BlockedApps/blockFromReport")
			.then()
			.statusCode(lessThan(400));

		// then
		QuarkusTransaction.requiringNew().run(() -> {
			BlockedApp rule = blockedAppRepository.find("label", "ZZ Inv wget").firstResult();
			assertNotNull(rule);
			assertEquals(1, rule.getMatchers().size());
			assertEquals(MatcherType.HOMEBREW, rule.getMatchers().getFirst().getType());
			assertTrue(rule.isEnabled());
		});
	}

	@Test
	@TestSecurity(user = "plain", roles = "user")
	@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "plain") })
	void nonAdminCannotBlockFromAReport()
	{
		// given / when / then
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("label", "ZZ Inv Forbidden")
			.formParam("matcherType", "HOMEBREW")
			.formParam("pattern", "wget")
			.when().post("/BlockedApps/blockFromReport")
			.then()
			.statusCode(403);
	}
}
