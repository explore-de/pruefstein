package com.pruefstein.shared.bootstrap;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.pruefstein.compliance.bootstrap.CatalogSeeder;
import com.pruefstein.compliance.domain.AppMatcher;
import com.pruefstein.compliance.domain.BlockedApp;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ComplianceResult;
import com.pruefstein.compliance.domain.MatcherType;
import com.pruefstein.compliance.repository.BlockedAppRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.compliance.service.CheckResolver;
import com.pruefstein.compliance.service.CheckResolver.ResolvedCheck;
import com.pruefstein.compliance.service.ComplianceResultAiService;
import com.pruefstein.compliance.service.ComplianceResultExplanation;
import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class Startup
{
	private static final Logger LOG = LoggerFactory.getLogger(Startup.class);

	/**
	 * Offline fallbacks used when the AI service is unreachable (e.g. no
	 * {@code OPENAI_API_KEY} in the dev environment), so the seeded UI still
	 * demonstrates what a generated tip looks like.
	 */
	private static final ComplianceResultExplanation FILEVAULT_FALLBACK = new ComplianceResultExplanation(
		"FileVault encryption not enabled",
		"The osquery result returned no rows, which means FileVault is off on this device. "
			+ "Without disk encryption, data on the drive is readable if the device is lost or stolen.\n\n"
			+ "To fix: System Settings → Privacy & Security → FileVault → Turn On FileVault. "
			+ "You will need to restart the device and save the recovery key in a secure location.");

	private static final ComplianceResultExplanation AUTO_UPDATES_FALLBACK = new ComplianceResultExplanation(
		"Automatic software updates disabled",
		"The AutomaticCheckEnabled preference is set to 0, meaning macOS will not check for or install updates automatically. "
			+ "Missing security patches leaves the device exposed to known vulnerabilities.\n\n"
			+ "To fix: System Settings → General → Software Update → Automatic Updates → enable all options. "
			+ "Alternatively, run: sudo defaults write /Library/Preferences/com.apple.SoftwareUpdate AutomaticCheckEnabled -bool true");

	@Inject
	ComplianceResultAiService aiService;

	@Inject
	CheckResolver checkResolver;

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

	@Inject
	UserRepository userRepository;

	@Transactional
	public void start(@Observes @Priority(CatalogSeeder.PRIORITY + 100) StartupEvent evt)
	{
		if (LaunchMode.current() == LaunchMode.DEVELOPMENT)
		{
			seedDemoData();
		}
	}

	/**
	 * Demo data for the dev UI. The checks themselves come from
	 * {@link CatalogSeeder}, which every environment shares — this only adds
	 * the blocked-app examples and a few reports to look at.
	 */
	private void seedDemoData()
	{
		seedBlockedApps();
		seedReports(
			requireItem("FileVault enabled"),
			requireItem("Firewall enabled"),
			requireItem("Automatic updates enabled"),
			requireItem("Screen lock timeout \u2264 300 seconds"));
	}

	private ComplianceItem requireItem(String name)
	{
		return itemRepository.find("name", name).firstResultOptional()
			.orElseThrow(() -> new IllegalStateException(
				"Baseline check '" + name + "' is missing; the catalog should have been seeded first"));
	}

	/**
	 * Example rules for the generated blacklist check, which the catalog
	 * already created. Their SQL is rendered from these on every agent run.
	 */
	private void seedBlockedApps()
	{
		// Nextcloud is the reason the rule owns several matchers: it ships as a
		// Homebrew cask and as a plain bundle, and one entry has to catch both.
		addBlockedApp("Nextcloud Desktop",
			"Company data must stay in the approved M365 tenant. Third-party sync clients move it outside the ISMS scope.",
			List.of(
				new AppMatcher(MatcherType.BUNDLE_ID, "com.nextcloud.%"),
				new AppMatcher(MatcherType.HOMEBREW, "nextcloud"),
				new AppMatcher(MatcherType.APP_NAME, "Nextcloud.app")));

		addBlockedApp("TeamViewer",
			"Unmanaged remote access bypasses the approved support channel and its logging.",
			List.of(
				new AppMatcher(MatcherType.BUNDLE_ID, "com.teamviewer.%"),
				new AppMatcher(MatcherType.HOMEBREW, "teamviewer")));

		addBlockedApp("BitTorrent clients",
			"Peer-to-peer file sharing risks unlicensed content and inbound connections on company devices.",
			List.of(
				new AppMatcher(MatcherType.HOMEBREW, "transmission"),
				new AppMatcher(MatcherType.HOMEBREW, "qbittorrent"),
				new AppMatcher(MatcherType.BUNDLE_ID, "org.m0k.transmission")));
	}

	private void addBlockedApp(String label, String reason, List<AppMatcher> matchers)
	{
		BlockedApp app = new BlockedApp();
		app.setLabel(label);
		app.setReason(reason);
		app.setEnabled(true);
		app.setMatchers(new ArrayList<>(matchers));
		blockedAppRepository.persist(app);
	}

	private void seedReports(ComplianceItem fileVault, ComplianceItem firewall,
		ComplianceItem autoUpdates, ComplianceItem screenLock)
	{
		// The people the reports belong to. Without these the Users screen is
		// empty and every report is unattributed, so the last-report column,
		// the STALE badge and the two mail actions all have nothing to show.
		AppUser aliceUser = seedUser("alice", "Alice", "Andersson");
		AppUser bobUser = seedUser("bob", "Bob", "Bergmann");
		AppUser plainUser = seedUser("user", "Uli", "Ulrich");
		// Carol was typed in and nothing has happened since: the invite may
		// never even have landed.
		seedUser(null, "Carol", "Chen");
		// Dan got in and stopped there — signed in, never ran the agent. The
		// same blank row as Carol on the old screen, a different problem.
		seedUser("dev-dan", "Dan", "Doyle");
		// Nina is the dev realm's third login and owns no device on purpose:
		// signing in as her is how the dashboard's setup state gets looked at.
		seedUser("newbie", "Nina", "Neuling");

		// Report 1: fully compliant, finalized yesterday
		Report compliant = new Report();
		compliant.setDeviceId("MacBook-Pro-Alice.local");
		compliant.setUserId("alice");
		compliant.setKeycloakUser("alice");
		compliant.setAppUser(aliceUser);
		compliant.setCheckedAt(Instant.now().minus(1, ChronoUnit.DAYS));
		compliant.setStatus(ReportStatus.COMPLIANT);
		compliant.setFinalizedAt(Instant.now().minus(1, ChronoUnit.DAYS).plusSeconds(5));
		reportRepository.persist(compliant);

		addResult(compliant, fileVault, true, "[{\"filevault_status\":\"on\"}]");
		addResult(compliant, firewall, true, "[{\"global_state\":\"1\"}]");
		addResult(compliant, autoUpdates, true, "[{\"value\":\"1\"}]");
		addResult(compliant, screenLock, true, "[{\"value\":\"120\"}]");

		// Report 2: non-compliant with deadline, checked an hour ago
		Report nonCompliant = new Report();
		nonCompliant.setDeviceId("MacBook-Air-Bob.local");
		nonCompliant.setUserId("bob");
		nonCompliant.setKeycloakUser("bob");
		nonCompliant.setAppUser(bobUser);
		nonCompliant.setCheckedAt(Instant.now().minus(1, ChronoUnit.HOURS));
		nonCompliant.setStatus(ReportStatus.NON_COMPLIANT);
		nonCompliant.setDeadline(Instant.now().plus(6, ChronoUnit.DAYS));
		nonCompliant.setFinalizedAt(Instant.now().minus(1, ChronoUnit.HOURS).plusSeconds(5));
		reportRepository.persist(nonCompliant);

		addResult(nonCompliant, fileVault, false, "[]", FILEVAULT_FALLBACK);
		addResult(nonCompliant, firewall, true, "[{\"global_state\":\"1\"}]");
		addResult(nonCompliant, autoUpdates, false, "[{\"value\":\"0\"}]", AUTO_UPDATES_FALLBACK);
		addResult(nonCompliant, screenLock, true, "[{\"value\":\"240\"}]");

		// Report 3: compliant, for the "user" Keycloak test account
		Report userReport = new Report();
		userReport.setDeviceId("MacBook-Pro-User.local");
		userReport.setUserId("user");
		userReport.setKeycloakUser("user");
		userReport.setAppUser(plainUser);
		userReport.setCheckedAt(Instant.now().minus(2, ChronoUnit.HOURS));
		userReport.setStatus(ReportStatus.COMPLIANT);
		userReport.setFinalizedAt(Instant.now().minus(2, ChronoUnit.HOURS).plusSeconds(5));
		reportRepository.persist(userReport);

		addResult(userReport, fileVault, true, "[{\"filevault_status\":\"on\"}]");
		addResult(userReport, firewall, true, "[{\"global_state\":\"1\"}]");
		addResult(userReport, autoUpdates, true, "[{\"value\":\"1\"}]");
		addResult(userReport, screenLock, true, "[{\"value\":\"180\"}]");

		// Report 4: Uli's older run, well past the 7-day interval. The newest
		// run wins the row, so this one only shows inside the folded group —
		// it is here to give the Reports list a genuinely aged entry.
		Report aged = new Report();
		aged.setDeviceId("MacBook-Pro-User.local");
		aged.setUserId("user");
		aged.setKeycloakUser("user");
		aged.setAppUser(plainUser);
		aged.setCheckedAt(Instant.now().minus(40, ChronoUnit.DAYS));
		aged.setStatus(ReportStatus.COMPLIANT);
		aged.setFinalizedAt(Instant.now().minus(40, ChronoUnit.DAYS).plusSeconds(5));
		reportRepository.persist(aged);

		addResult(aged, fileVault, true, "[{\"filevault_status\":\"on\"}]");
		addResult(aged, firewall, true, "[{\"global_state\":\"1\"}]");
		addResult(aged, autoUpdates, true, "[{\"value\":\"1\"}]");
		addResult(aged, screenLock, true, "[{\"value\":\"180\"}]");

		// Report 5: Uli's other machine, failing two checks with the repair
		// window still open. The one report state the personal dashboard is
		// built around — signing in as "user" has to show both this and the
		// clean machine above, or half the page never gets looked at.
		Report userOpen = new Report();
		userOpen.setDeviceId("MacBook-Air-User.local");
		userOpen.setUserId("user");
		userOpen.setKeycloakUser("user");
		userOpen.setAppUser(plainUser);
		userOpen.setCheckedAt(Instant.now().minus(3, ChronoUnit.HOURS));
		userOpen.setStatus(ReportStatus.OPEN);
		userOpen.setDeadline(Instant.now().plus(5, ChronoUnit.DAYS));
		reportRepository.persist(userOpen);

		addResult(userOpen, fileVault, false, "[]", FILEVAULT_FALLBACK);
		addResult(userOpen, firewall, true, "[{\"global_state\":\"1\"}]");
		addResult(userOpen, autoUpdates, false, "[{\"value\":\"0\"}]", AUTO_UPDATES_FALLBACK);
		addResult(userOpen, screenLock, true, "[{\"value\":\"180\"}]");

		// Device registry — seeded devices carry no periodic flow instance;
		// the first real check-in starts one.
		Device alice = new Device();
		alice.setDeviceId("MacBook-Pro-Alice.local");
		alice.setUserId("alice");
		alice.setKeycloakUser("alice");
		alice.setAppUser(aliceUser);
		alice.setLastReportAt(compliant.getCheckedAt());
		deviceRepository.persist(alice);

		Device bob = new Device();
		bob.setDeviceId("MacBook-Air-Bob.local");
		bob.setUserId("bob");
		bob.setKeycloakUser("bob");
		bob.setAppUser(bobUser);
		bob.setLastReportAt(nonCompliant.getCheckedAt());
		deviceRepository.persist(bob);

		Device user = new Device();
		user.setDeviceId("MacBook-Pro-User.local");
		user.setUserId("user");
		user.setKeycloakUser("user");
		user.setAppUser(plainUser);
		user.setLastReportAt(userReport.getCheckedAt());
		deviceRepository.persist(user);

		Device userAir = new Device();
		userAir.setDeviceId("MacBook-Air-User.local");
		userAir.setUserId("user");
		userAir.setKeycloakUser("user");
		userAir.setAppUser(plainUser);
		userAir.setLastReportAt(userOpen.getCheckedAt());
		deviceRepository.persist(userAir);
	}

	/**
	 * A seeded person. The subject is what a real login would carry;
	 * {@code null} leaves the row in the state an admin's typing leaves it in,
	 * which is the one the mail-address matching has to cope with.
	 */
	private AppUser seedUser(String oidcSubject, String firstname, String lastname)
	{
		AppUser user = new AppUser();
		user.setOidcSubject(oidcSubject);
		user.setFirstname(firstname);
		user.setLastname(lastname);
		user.setMail(firstname.toLowerCase() + "@example.com");
		userRepository.persist(user);
		return user;
	}

	private void addResult(Report report, ComplianceItem item, boolean passed, String output)
	{
		addResult(report, item, passed, output, null);
	}

	private void addResult(Report report, ComplianceItem item, boolean passed, String output,
		ComplianceResultExplanation fallback)
	{
		ComplianceResult result = new ComplianceResult();
		result.setReport(report);
		result.setItem(item);
		result.setPassed(passed);
		result.setOutput(output);

		// The dev database is recreated on every boot, so — unlike a persisted
		// agent report — the seeded tips are regenerated on every dev run.
		if (!passed)
		{
			ComplianceResultExplanation exp = explain(item, output, fallback);
			result.setAiShortDescription(exp.shortDescription());
			result.setAiLongExplanation(exp.longExplanation());
		}
		resultRepository.persist(result);
	}

	private ComplianceResultExplanation explain(ComplianceItem item, String output,
		ComplianceResultExplanation fallback)
	{
		try
		{
			ResolvedCheck resolved = checkResolver.resolve(item);
			return aiService.explain(item.getName(), resolved.query(), resolved.expression(), output);
		}
		catch (Exception e)
		{
			LOG.warn("AI tip for seeded result '{}' fell back to static text.", item.getName(), e);
			return fallback != null ? fallback : new ComplianceResultExplanation(null, null);
		}
	}
}
