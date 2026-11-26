package com.pruefstein.compliance.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.pruefstein.compliance.domain.InstalledApp;
import com.pruefstein.compliance.repository.InstalledAppRepository;
import com.pruefstein.compliance.service.AppRiskAiService;
import com.pruefstein.compliance.service.AppRiskAnalysis;
import com.pruefstein.compliance.service.AppRiskAnalysis.Finding;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class AppsTest
{
	private static final String DEVICE_PREFIX = "zzfleet-";

	@Inject
	ReportRepository reportRepository;

	@Inject
	InstalledAppRepository installedAppRepository;

	@InjectMock
	AppRiskAiService appRiskAiService;

	@BeforeEach
	void setUp()
	{
		Instant now = Instant.now();
		// alice's laptop: an old run with an app removed since, and the
		// current one
		seed("zzfleet-alice-laptop", "alice", now.minus(10, ChronoUnit.DAYS), "ZZFleetRemoved.app", "ZZFleetShared.app");
		seed("zzfleet-alice-laptop", "alice", now, "ZZFleetShared.app", "ZZFleetAliceOnly.app");
		seed("zzfleet-bob-laptop", "bob", now, "ZZFleetShared.app", "ZZFleetBobOnly.app");
	}

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			installedAppRepository.delete("report.deviceId like ?1", DEVICE_PREFIX + "%");
			reportRepository.delete("deviceId like ?1", DEVICE_PREFIX + "%");
		});
	}

	private void seed(String deviceId, String owner, Instant checkedAt, String... appNames)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = new Report();
			report.setDeviceId(deviceId);
			report.setUserId(owner);
			report.setKeycloakUser(owner);
			report.setCheckedAt(checkedAt);
			reportRepository.persist(report);
			for (String name : appNames)
			{
				InstalledApp app = new InstalledApp();
				app.setReport(report);
				app.setSource("app");
				app.setName(name);
				app.setIdentifier("com.example." + name.toLowerCase().replace(".app", ""));
				app.setVersion("1.0");
				installedAppRepository.persist(app);
			}
		});
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void adminSeesEveryAppFromEachDevicesLatestRun()
	{
		// given — seeded in setUp

		// when / then — the shared app once, counted on both devices; the app
		// removed since the old run is gone
		given()
			.when().get("/Apps/index")
			.then()
			.statusCode(200)
			.body(allOf(
				containsString("All Apps"),
				containsString("ZZFleetAliceOnly.app"),
				containsString("ZZFleetBobOnly.app"),
				not(containsString("ZZFleetRemoved.app"))))
			.body(matchesPattern("(?s).*ZZFleetShared\\.app</p>(?:(?!</tr>).)*>2</button>.*"));
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "alice") })
	void anyoneElseSeesOnlyTheirOwnDevices()
	{
		// given — seeded in setUp

		// when / then
		given()
			.when().get("/Apps/index")
			.then()
			.statusCode(200)
			.body(allOf(
				containsString("ZZFleetAliceOnly.app"),
				not(containsString("ZZFleetBobOnly.app")),
				not(containsString("zzfleet-bob-laptop")),
				not(containsString("ANALYZE WITH AI"))));
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void eachAppNamesTheDevicesThatHaveItAndLinksTheirLatestReport()
	{
		// given — seeded in setUp
		Long bobsReport = QuarkusTransaction.requiringNew().call(() -> reportRepository
			.find("deviceId", "zzfleet-bob-laptop").firstResult().id);

		// when / then — the shared app lists both laptops, bob's linked to his
		// run
		given()
			.when().get("/Apps/index")
			.then()
			.statusCode(200)
			.body(matchesPattern("(?s).*ZZFleetShared\\.app</p>(?:(?!</tr>).)*zzfleet-alice-laptop"
				+ "(?:(?!</tr>).)*/Reports/show/" + bobsReport + "\"(?:(?!</tr>).)*zzfleet-bob-laptop.*"));
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void blockedTabIsSelectable()
	{
		// given / when / then
		given()
			.when().get("/Apps/index?tab=blocked")
			.then()
			.statusCode(200)
			.body(containsString("appsPage('blocked')"));
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void analysisAnswersByTheKeysItWasGiven()
	{
		// given — the AI flags the second app, invents a sixth, and makes up a
		// category
		when(appRiskAiService.analyze(any())).thenReturn(new AppRiskAnalysis(List.of(
			new Finding(2, "REMOTE_ACCESS", "Lets anyone control this Mac."),
			new Finding(6, "FILE_SHARING", "Not in the batch."),
			new Finding(1, "torrent", " Peer-to-peer file sharing. "))));

		// when / then
		given()
			.contentType(JSON)
			.body("""
				{"apps":[
				  {"key":"app|Transmission.app|org.m0k.transmission","source":"app","name":"Transmission.app","identifier":"org.m0k.transmission"},
				  {"key":"app|TeamViewer.app|com.teamviewer.TeamViewer","source":"app","name":"TeamViewer.app","identifier":"com.teamviewer.TeamViewer"}]}
				""")
			.when().post("/ai/analyze-apps")
			.then()
			.statusCode(200)
			.body("size()", equalTo(2))
			.body("find { it.key == 'app|TeamViewer.app|com.teamviewer.TeamViewer' }.category", equalTo("REMOTE_ACCESS"))
			.body("find { it.key == 'app|Transmission.app|org.m0k.transmission' }.category", equalTo("OTHER"))
			.body("find { it.key == 'app|Transmission.app|org.m0k.transmission' }.reason",
				equalTo("Peer-to-peer file sharing."));
	}

	@Test
	@TestSecurity(user = "admin", roles = { "admin" })
	void analysisRefusesAnOversizedBatch()
	{
		// given
		StringBuilder apps = new StringBuilder();
		for (int i = 0; i <= AiSuggestResource.MAX_APPS_PER_REQUEST; i++)
		{
			apps.append(i == 0 ? "" : ",").append("{\"key\":\"k").append(i).append("\",\"name\":\"n\"}");
		}

		// when / then
		given()
			.contentType(JSON)
			.body("{\"apps\":[" + apps + "]}")
			.when().post("/ai/analyze-apps")
			.then()
			.statusCode(400);
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	void nonAdminCannotAnalyze()
	{
		// given / when / then
		given()
			.contentType(JSON)
			.body("{\"apps\":[{\"key\":\"k\",\"name\":\"n\"}]}")
			.when().post("/ai/analyze-apps")
			.then()
			.statusCode(403);
	}
}
