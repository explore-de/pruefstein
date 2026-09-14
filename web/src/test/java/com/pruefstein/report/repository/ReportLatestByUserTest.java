package com.pruefstein.report.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestTransaction
class ReportLatestByUserTest
{
	@Inject
	ReportRepository reportRepository;

	@Inject
	UserRepository userRepository;

	@Test
	void picksTheNewestRunPerUser()
	{
		// given two users with several runs each
		AppUser alice = user("latest-alice@example.com");
		AppUser bob = user("latest-bob@example.com");

		Instant now = Instant.now();
		report(alice, now.minus(9, ChronoUnit.DAYS), ReportStatus.NON_COMPLIANT);
		Report aliceNewest = report(alice, now.minus(1, ChronoUnit.DAYS), ReportStatus.COMPLIANT);
		report(alice, now.minus(5, ChronoUnit.DAYS), ReportStatus.COMPLIANT);
		Report bobNewest = report(bob, now.minus(2, ChronoUnit.DAYS), ReportStatus.OPEN);
		report(bob, now.minus(30, ChronoUnit.DAYS), ReportStatus.MISSING);

		// when
		Map<Long, Report> latest = reportRepository.findLatestByUser(List.of(alice.id, bob.id));

		// then
		assertEquals(aliceNewest.id, latest.get(alice.id).id);
		assertEquals(bobNewest.id, latest.get(bob.id).id);
	}

	@Test
	void leavesOutUsersWithNoRuns()
	{
		// given
		AppUser neverReported = user("latest-never@example.com");

		// when
		Map<Long, Report> latest = reportRepository.findLatestByUser(List.of(neverReported.id));

		// then the caller sees a missing key, which the screen reads as "never"
		assertFalse(latest.containsKey(neverReported.id));
	}

	@Test
	void breaksATieOnIdSoThePickIsStable()
	{
		// given two runs landing in the same instant
		AppUser user = user("latest-tie@example.com");
		Instant sameMoment = Instant.now().minus(1, ChronoUnit.HOURS);
		report(user, sameMoment, ReportStatus.NON_COMPLIANT);
		Report second = report(user, sameMoment, ReportStatus.COMPLIANT);

		// when
		Map<Long, Report> latest = reportRepository.findLatestByUser(List.of(user.id));

		// then the later-persisted one wins, every time
		assertEquals(second.id, latest.get(user.id).id);
	}

	@Test
	void returnsNothingForAnEmptyUserList()
	{
		// given (a Users screen with no users on it)

		// when / then — no query, no error
		assertTrue(reportRepository.findLatestByUser(List.of()).isEmpty());
	}

	private AppUser user(String mail)
	{
		AppUser user = new AppUser();
		user.setFirstname("Latest");
		user.setLastname("Test");
		user.setMail(mail);
		userRepository.persist(user);
		return user;
	}

	private Report report(AppUser user, Instant checkedAt, ReportStatus status)
	{
		Report report = new Report();
		report.setAppUser(user);
		report.setDeviceId("device-" + user.id);
		report.setUserId("uid-" + user.id);
		report.setCheckedAt(checkedAt);
		report.setStatus(status);
		reportRepository.persist(report);
		return report;
	}
}
