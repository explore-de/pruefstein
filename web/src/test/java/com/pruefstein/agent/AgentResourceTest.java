package com.pruefstein.agent;

import java.time.Instant;

import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "testuser", roles = "user")
@JwtSecurity(claims = { @Claim(key = "preferred_username", value = "testuser") })
class AgentResourceTest
{
	private static final String DEVICE = "test-device";
	private static final String STAND_IN = "Test-MacBook.local";

	@Inject
	ComplianceGroupRepository groupRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	ReportRepository reportRepository;

	@Inject
	DeviceRepository deviceRepository;

	private Long itemId;
	private Long groupId;

	@BeforeEach
	void setUp()
	{
		Long[] ids = new Long[2];
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceGroup group = new ComplianceGroup();
			group.setName("Test Group");
			groupRepository.persist(group);
			ids[0] = group.id;

			ExpressionCheck item = new ExpressionCheck();
			item.setName("Test Check");
			item.setQuery("SELECT 1;");
			item.setExpectedExpression("results[0] == 1");
			item.setGroup(group);
			itemRepository.persist(item);
			ids[1] = item.id;
		});
		groupId = ids[0];
		itemId = ids[1];
	}

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			for (String deviceId : new String[] { DEVICE, STAND_IN })
			{
				resultRepository.delete("report.deviceId", deviceId);
				reportRepository.delete("deviceId", deviceId);
				deviceRepository.delete("deviceId", deviceId);
			}
			itemRepository.deleteById(itemId);
			groupRepository.deleteById(groupId);
		});
	}

	@Test
	void aCleanRunIsCompliantOnArrival()
	{
		// when
		String reportUrl = push(true);

		// then
		assertTrue(reportUrl.contains("/Reports/show/"), "reportUrl should contain /Reports/show/");
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = reportOf(reportUrl);
			assertEquals(ReportStatus.COMPLIANT, report.getStatus());
			assertNotNull(report.getFinalizedAt(), "a decided report should say when it was decided");
			assertNull(report.getDeadline(), "there is nothing to remediate");
		});
	}

	/**
	 * The agent asked before sending this, so the failures are reported on
	 * purpose. What they buy is time to fix them, not an immediate verdict.
	 */
	@Test
	void aFailingRunOpensAReportWithADeadline()
	{
		// when
		String reportUrl = push(false);

		// then
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = reportOf(reportUrl);
			assertEquals(ReportStatus.OPEN, report.getStatus());
			assertNotNull(report.getDeadline(), "an open report needs a deadline to be closed at");
			assertNull(report.getFinalizedAt());
		});
	}

	/**
	 * Another go at the same failure updates the report someone is already
	 * remediating rather than opening a second one beside it — and crucially
	 * does not move the deadline, or a device could stay open indefinitely by
	 * uploading a failing run every few days.
	 */
	@Test
	void anotherFailingRunKeepsTheReportAndItsDeadline()
	{
		// given
		String first = push(false);
		Instant deadline = QuarkusTransaction.requiringNew()
			.call(() -> reportOf(first).getDeadline());

		// when
		String second = push(false);

		// then
		assertEquals(first, second, "the second run should land on the open report");
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = reportOf(first);
			assertEquals(ReportStatus.OPEN, report.getStatus());
			assertEquals(deadline, report.getDeadline(), "re-uploading must not buy more time");
			assertEquals(1, resultRepository.count("report", report),
				"the run should have replaced the results, not been added to them");
		});
	}

	@Test
	void aCleanRunClosesTheReportItWasOpenedBy()
	{
		// given
		String open = push(false);

		// when — the device fixed what was failing and reported again
		String fixed = push(true);

		// then
		assertEquals(open, fixed, "fixing a report should close that report, not open a new one");
		QuarkusTransaction.requiringNew().run(() -> {
			Report report = reportOf(open);
			assertEquals(ReportStatus.COMPLIANT, report.getStatus());
			assertNotNull(report.getFinalizedAt());
		});
	}

	/**
	 * With nothing open, each submission is its own record: two clean runs are
	 * two reports, not one report checked twice.
	 */
	@Test
	void aRunWithNothingOpenIsAReportOfItsOwn()
	{
		// when
		String first = push(true);
		String second = push(true);

		// then
		assertNotEquals(first, second);
		QuarkusTransaction.requiringNew()
			.run(() -> assertEquals(2, reportRepository.count("deviceId", DEVICE)));
	}

	/**
	 * The agent cannot work the deadline out for itself — it belongs to the
	 * report, not to the run — so the response has to carry it, and only while
	 * there is still something to fix.
	 */
	@Test
	void theResponseCarriesTheDeadlineOfAnOpenReport()
	{
		// given / when
		String deadline = pushFor(false).extract().path("deadline");

		// then
		assertNotNull(deadline, "a failing run should be told how long it has");
	}

	@Test
	void aDecidedReportComesBackWithNoDeadline()
	{
		assertNull(pushFor(true).extract().path("deadline"),
			"a clean run has nothing to remediate, so nothing to count down");
	}

	@Test
	void aRetiredDeviceThatReportsAgainGetsANewCycle()
	{
		// given a Mac whose owner was deleted, and which a new owner now uses
		push(true);
		QuarkusTransaction.requiringNew()
			.run(() -> deviceRepository.findByDeviceId(DEVICE).orElseThrow().retire());

		// when
		push(true);

		// then it is chased for reports again
		QuarkusTransaction.requiringNew().run(() -> assertNotNull(
			deviceRepository.findByDeviceId(DEVICE).orElseThrow().getPeriodicFlowInstanceId()));
	}

	/**
	 * An agent that could not read the hardware UUID filed under the hostname
	 * instead. Once the same Mac reports under its UUID, the hostname record is
	 * a stand-in nobody will ever report for again — left alone, the deadline
	 * job would file a MISSING report for it every cycle.
	 */
	@Test
	void reportingUnderTheUuidRetiresTheHostnameStandIn()
	{
		// given the Mac's first run, filed under its hostname
		pushAs(STAND_IN, STAND_IN, true);

		// when it reports under its UUID
		pushAs(DEVICE, STAND_IN, true);

		// then the stand-in is no longer chased and leaves the fleet figures
		QuarkusTransaction.requiringNew().run(() -> {
			assertNull(deviceRepository.findByDeviceId(STAND_IN).orElseThrow().getPeriodicFlowInstanceId());
			assertTrue(reportRepository.list("deviceId", STAND_IN).stream()
				.allMatch(r -> r.getArchivedAt() != null), "the stand-in's runs should be archived");
			assertNotNull(deviceRepository.findByDeviceId(DEVICE).orElseThrow().getPeriodicFlowInstanceId());
		});
	}

	@Test
	void anotherPersonsHostnameDeviceIsLeftAlone()
	{
		// given a device under that hostname, but somebody else's
		QuarkusTransaction.requiringNew().run(() -> {
			Device other = new Device();
			other.setDeviceId(STAND_IN);
			other.setUserId(STAND_IN);
			other.setKeycloakUser("somebody-else");
			other.setLastReportAt(Instant.now());
			other.setPeriodicFlowInstanceId("no-such-instance");
			deviceRepository.persist(other);
		});

		// when
		pushAs(DEVICE, STAND_IN, true);

		// then
		QuarkusTransaction.requiringNew().run(() -> assertNotNull(
			deviceRepository.findByDeviceId(STAND_IN).orElseThrow().getPeriodicFlowInstanceId()));
	}

	private String push(boolean passed)
	{
		return pushFor(passed).extract().path("reportUrl");
	}

	private ValidatableResponse pushFor(boolean passed)
	{
		return pushAs(DEVICE, "test-user", passed);
	}

	private ValidatableResponse pushAs(String deviceId, String userId, boolean passed)
	{
		String body = """
			{"deviceId":"%s","userId":"%s","checkedAt":"%s",
			 "results":[{"itemId":%d,"passed":%b,"output":"%s"}]}
			""".formatted(deviceId, userId, Instant.now(), itemId, passed, passed ? "ok" : "fail");

		return given()
			.contentType(JSON)
			.body(body)
			.when().post("/api/reports")
			.then()
			.statusCode(200);
	}

	/** The report the given {@code reportUrl} points at. */
	private Report reportOf(String reportUrl)
	{
		return reportRepository.findById(
			Long.valueOf(reportUrl.substring(reportUrl.lastIndexOf('/') + 1)));
	}
}
