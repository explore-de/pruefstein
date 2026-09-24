package com.pruefstein.notification;

import java.time.Duration;
import java.time.Instant;

import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceResult;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The outcome mail is only worth sending once the failed checks carry their
 * explanations, but it has to be sent either way in the end.
 */
@QuarkusTest
class ReportMailDispatcherTest
{
	private static final String DEVICE = "mail-dispatch-device";

	@InjectMock
	ReportMailService mailService;

	@Inject
	ReportMailDispatcher dispatcher;

	@Inject
	ReportRepository reportRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	ComplianceGroupRepository groupRepository;

	private Long reportId;
	private Long itemId;
	private Long groupId;

	@BeforeEach
	void setUp()
	{
		Long[] ids = new Long[3];
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceGroup group = new ComplianceGroup();
			group.setName("Mail Dispatch Group");
			groupRepository.persist(group);
			ids[0] = group.id;

			ExpressionCheck item = new ExpressionCheck();
			item.setName("Mail Dispatch Check");
			item.setQuery("SELECT 1;");
			item.setExpectedExpression("results[0] == 1");
			item.setGroup(group);
			itemRepository.persist(item);
			ids[1] = item.id;

			Report report = new Report();
			report.setDeviceId(DEVICE);
			report.setUserId("mail-dispatch-user");
			report.setCheckedAt(Instant.now());
			report.setStatus(ReportStatus.NON_COMPLIANT);
			reportRepository.persist(report);
			ids[2] = report.id;
		});
		groupId = ids[0];
		itemId = ids[1];
		reportId = ids[2];
	}

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> {
			resultRepository.delete("report.deviceId", DEVICE);
			reportRepository.delete("deviceId", DEVICE);
			itemRepository.deleteById(itemId);
			groupRepository.deleteById(groupId);
		});
	}

	@Test
	void aReportWithNothingToExplainMailsStraightAway()
	{
		// given — a passing result, so nothing is waiting on the model
		addResult(true, "[{\"x\":1}]", null);

		// when
		QuarkusTransaction.requiringNew().run(() -> dispatcher.request(reportRepository.findById(reportId)));

		// then
		verify(mailService, timeout(2000)).sendOutcomeMail(reportId);
		assertNull(pendingSince(), "nothing should have been held back");
	}

	@Test
	void aReportWithUnexplainedFailuresWaits()
	{
		// given
		addResult(false, "[{\"x\":0}]", null);

		// when
		QuarkusTransaction.requiringNew().run(() -> dispatcher.request(reportRepository.findById(reportId)));

		// then
		verify(mailService, never()).sendOutcomeMail(reportId);
		assertNotNull(pendingSince(), "the mail should be waiting for an explanation");
	}

	@Test
	void theMailGoesOutOnceTheExplanationsArrive()
	{
		// given — held back
		Long resultId = addResult(false, "[{\"x\":0}]", null);
		QuarkusTransaction.requiringNew().run(() -> dispatcher.request(reportRepository.findById(reportId)));
		assertNotNull(pendingSince());

		// when — the enrichment job has since explained it
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceResult result = resultRepository.findById(resultId);
			result.setAiShortDescription("Disk encryption not enabled");
			result.setAiLongExplanation("Enable FileVault.");
		});
		dispatcher.sendReady();

		// then
		verify(mailService, timeout(2000)).sendOutcomeMail(reportId);
		assertNull(pendingSince(), "a sent mail should no longer be pending");
	}

	/**
	 * A model that never answers must delay the notification, not replace it
	 * with silence — so the wait is bounded and the mail goes out plain.
	 */
	@Test
	void anUnexplainableReportIsMailedAfterTheGracePeriod()
	{
		// given — held back, and waiting since well before the grace period
		addResult(false, "[{\"x\":0}]", null);
		QuarkusTransaction.requiringNew().run(() -> dispatcher.request(reportRepository.findById(reportId)));
		QuarkusTransaction.requiringNew().run(() -> reportRepository.findById(reportId)
			.setMailPendingSince(Instant.now().minus(Duration.ofDays(1))));

		// when
		dispatcher.sendReady();

		// then — still unexplained, sent anyway
		verify(mailService, timeout(2000)).sendOutcomeMail(reportId);
		assertNull(pendingSince());
	}

	/**
	 * The agent records a check that errored or timed out as failed with no
	 * output. Nothing can explain that, and counting it would hold every such
	 * report for the whole grace period.
	 */
	@Test
	void aFailureWithNoOutputDoesNotHoldTheMail()
	{
		// given
		addResult(false, null, null);

		// when
		QuarkusTransaction.requiringNew().run(() -> dispatcher.request(reportRepository.findById(reportId)));

		// then
		verify(mailService, timeout(2000)).sendOutcomeMail(reportId);
		assertNull(pendingSince());
	}

	private Long addResult(boolean passed, String output, String shortDescription)
	{
		Long[] id = new Long[1];
		QuarkusTransaction.requiringNew().run(() -> {
			ComplianceResult result = new ComplianceResult();
			result.setReport(reportRepository.findById(reportId));
			result.setItem(itemRepository.findById(itemId));
			result.setPassed(passed);
			result.setOutput(output);
			result.setAiShortDescription(shortDescription);
			resultRepository.persist(result);
			id[0] = result.id;
		});
		return id[0];
	}

	private Instant pendingSince()
	{
		Instant[] value = new Instant[1];
		QuarkusTransaction.requiringNew()
			.run(() -> value[0] = reportRepository.findById(reportId).getMailPendingSince());
		return value[0];
	}
}
