package com.pruefstein.report.repository;

import java.time.Instant;
import java.util.List;

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
class ReportRepositoryFilterTest
{
	@Inject
	ReportRepository reportRepository;

	@Inject
	UserRepository userRepository;

	@Test
	void listFilteredWithNoFiltersReturnsAll()
	{
		// given
		reportRepository.persist(report("filter-dev-a", "user-a", ReportStatus.COMPLIANT));
		reportRepository.persist(report("filter-dev-b", "user-b", ReportStatus.NON_COMPLIANT));

		// when
		List<Report> results = reportRepository.listFiltered(null, null, null, null);

		// then
		assertTrue(results.stream().anyMatch(r -> "filter-dev-a".equals(r.getDeviceId())));
		assertTrue(results.stream().anyMatch(r -> "filter-dev-b".equals(r.getDeviceId())));
	}

	@Test
	void listFilteredByStatusReturnsOnlyMatchingStatus()
	{
		// given
		reportRepository.persist(report("filter-compliant-dev", "user", ReportStatus.COMPLIANT));
		reportRepository.persist(report("filter-missing-dev", "user", ReportStatus.MISSING));

		// when
		List<Report> results = reportRepository.listFiltered(ReportStatus.COMPLIANT, null, null, null);

		// then
		assertTrue(results.stream().anyMatch(r -> "filter-compliant-dev".equals(r.getDeviceId())));
		assertFalse(results.stream().anyMatch(r -> "filter-missing-dev".equals(r.getDeviceId())));
	}

	@Test
	void listFilteredBySearchMatchesDeviceId()
	{
		// given
		reportRepository.persist(report("xray-device-42", "user", ReportStatus.OPEN));
		reportRepository.persist(report("unrelated-device", "user", ReportStatus.OPEN));

		// when
		List<Report> results = reportRepository.listFiltered(null, "xray", null, null);

		// then
		assertTrue(results.stream().anyMatch(r -> "xray-device-42".equals(r.getDeviceId())));
		assertFalse(results.stream().anyMatch(r -> "unrelated-device".equals(r.getDeviceId())));
	}

	@Test
	void listFilteredBySearchMatchesUserId()
	{
		// given
		reportRepository.persist(report("search-dev", "searchable-user", ReportStatus.OPEN));
		reportRepository.persist(report("other-dev", "different-user", ReportStatus.OPEN));

		// when
		List<Report> results = reportRepository.listFiltered(null, "searchable", null, null);

		// then
		assertTrue(results.stream().anyMatch(r -> "search-dev".equals(r.getDeviceId())));
		assertFalse(results.stream().anyMatch(r -> "other-dev".equals(r.getDeviceId())));
	}

	@Test
	void listFilteredSortsByCheckedAtAscending()
	{
		// given
		Report older = report("sort-old-dev", "user", ReportStatus.OPEN);
		older.setCheckedAt(Instant.now().minusSeconds(3600));
		Report newer = report("sort-new-dev", "user", ReportStatus.OPEN);
		newer.setCheckedAt(Instant.now());
		reportRepository.persist(older);
		reportRepository.persist(newer);

		// when
		List<Report> results = reportRepository.listFiltered(null, "sort-", null, "asc");

		// then
		int oldIdx = -1, newIdx = -1;
		for (int i = 0; i < results.size(); i++)
		{
			if ("sort-old-dev".equals(results.get(i).getDeviceId())) oldIdx = i;
			if ("sort-new-dev".equals(results.get(i).getDeviceId())) newIdx = i;
		}
		assertTrue(oldIdx >= 0 && newIdx >= 0, "Both reports should appear in results");
		assertTrue(oldIdx < newIdx, "Older report should appear before newer in ascending sort");
	}

	@Test
	void listFilteredBySearchMatchesThePersonsNameAndMail()
	{
		// given — the table shows the name, so the name is what gets typed
		AppUser person = person("Filterine", "Namesake", "filterine@example.com");
		Report named = report("named-dev", "host", ReportStatus.OPEN);
		named.setKeycloakUser("f.namesake");
		named.setAppUser(person);
		reportRepository.persist(named);
		Report unmatched = report("unmatched-dev", "host", ReportStatus.OPEN);
		unmatched.setKeycloakUser("someone-else");
		reportRepository.persist(unmatched);

		// when
		List<Report> byName = reportRepository.listFiltered(null, "filterine namesake", null, null);
		List<Report> byMail = reportRepository.listFiltered(null, "filterine@example", null, null);
		List<Report> byLogin = reportRepository.listFiltered(null, "someone-else", null, null);

		// then — and a run without a person is still searchable by its login
		assertEquals(List.of("named-dev"), byName.stream().map(Report::getDeviceId).toList());
		assertEquals(List.of("named-dev"), byMail.stream().map(Report::getDeviceId).toList());
		assertEquals(List.of("unmatched-dev"), byLogin.stream().map(Report::getDeviceId).toList());
	}

	@Test
	void listFilteredSortsByTheDisplayedUserName()
	{
		// given — Zoe logs in as "a-login", Anna as "z-login": sorting by the
		// login would put them the wrong way round
		Report zoe = report("sortname-zoe", "host", ReportStatus.OPEN);
		zoe.setKeycloakUser("a-login");
		zoe.setAppUser(person("Zoe", "Zander", "zoe@example.com"));
		Report anna = report("sortname-anna", "host", ReportStatus.OPEN);
		anna.setKeycloakUser("z-login");
		anna.setAppUser(person("Anna", "Adler", "anna@example.com"));
		Report nobody = report("sortname-nobody", "host", ReportStatus.OPEN);
		reportRepository.persist(zoe);
		reportRepository.persist(anna);
		reportRepository.persist(nobody);

		// when
		List<String> asc = reportRepository.listFiltered(null, "sortname-", "user", "asc").stream()
			.map(Report::getDeviceId).toList();
		List<String> desc = reportRepository.listFiltered(null, "sortname-", "user", "desc").stream()
			.map(Report::getDeviceId).toList();

		// then — a run with no user at all goes last both ways
		assertEquals(List.of("sortname-anna", "sortname-zoe", "sortname-nobody"), asc);
		assertEquals(List.of("sortname-zoe", "sortname-anna", "sortname-nobody"), desc);
	}

	// ── helpers ──────────────────────────────────────────────────────────

	private Report report(String deviceId, String userId, ReportStatus status)
	{
		Report report = new Report();
		report.setDeviceId(deviceId);
		report.setUserId(userId);
		report.setCheckedAt(Instant.now());
		report.setStatus(status);
		return report;
	}

	private AppUser person(String firstname, String lastname, String mail)
	{
		AppUser user = new AppUser();
		user.setFirstname(firstname);
		user.setLastname(lastname);
		user.setMail(mail);
		userRepository.persist(user);
		return user;
	}
}
