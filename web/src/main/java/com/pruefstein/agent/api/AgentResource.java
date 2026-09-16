package com.pruefstein.agent.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ComplianceResult;
import com.pruefstein.compliance.domain.InstalledApp;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.compliance.repository.InstalledAppRepository;
import com.pruefstein.compliance.service.CheckResolver;
import com.pruefstein.compliance.service.CheckResolver.ResolvedCheck;
import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.notification.ReportMailDispatcher;
import com.pruefstein.osversion.service.MacOsReleaseCatalog;
import com.pruefstein.report.domain.Report;
import com.pruefstein.report.domain.ReportStatus;
import com.pruefstein.report.flow.PeriodicFlowTrigger;
import com.pruefstein.report.flow.PeriodicReportingFlow;
import com.pruefstein.report.flow.WorkflowStartTrigger;
import com.pruefstein.report.repository.ReportRepository;
import com.pruefstein.report.service.ReportFinalizer;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.service.UserSyncService;
import io.quarkus.oidc.Tenant;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Tenant("api")
@Authenticated
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class AgentResource
{
	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	CheckResolver checkResolver;

	@Inject
	InstalledAppRepository installedAppRepository;

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	MacOsReleaseCatalog releaseCatalog;

	@Inject
	ReportRepository reportRepository;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	PeriodicReportingFlow periodicReportingFlow;

	@Inject
	Event<PeriodicFlowTrigger> periodicFlowTrigger;

	@Inject
	Event<WorkflowStartTrigger> workflowStartTrigger;

	@Inject
	ReportMailDispatcher mailDispatcher;

	@Inject
	ReportFinalizer finalizer;

	@Inject
	UserSyncService userSyncService;

	@Inject
	JsonWebToken jwt;

	@ConfigProperty(name = "pruefstein.compliance.remediation-days", defaultValue = "7")
	int remediationDays;

	public record CheckDto(Long id, String name, String query, String expectedExpression)
	{
	}

	public record ResultPayload(Long itemId, boolean passed, String output)
	{
	}

	public record InstalledAppPayload(String source, String name, String identifier, String version, String path)
	{
	}

	/**
	 * The operating system the device was running when it was checked. Absent
	 * from agents older than this field, and from a machine whose osquery could
	 * not answer, so every reader has to cope with {@code null}.
	 */
	public record OsVersionPayload(String name, String version, String build, String platform)
	{
	}

	public record ReportPayload(String deviceId, String userId, Instant checkedAt, List<ResultPayload> results,
		List<InstalledAppPayload> installedApps, OsVersionPayload osVersion)
	{
	}

	/**
	 * @param deadline
	 *            when the report stops being fixable, or {@code null} if it is
	 *            already decided. The agent has no way of working this out —
	 *            the window belongs to the report, so a device reporting a
	 *            second failing run gets back what is left of the first one's.
	 */
	// Handed back through a raw jakarta Response, so Quarkus never sees it as
	// the endpoint's entity type and leaves it out of the native image's
	// reflection data — Jackson then cannot serialize it at runtime.
	@RegisterForReflection
	public record ReportResponse(String reportUrl, Instant deadline)
	{
	}

	@GET
	@Path("/checks")
	@Transactional
	public List<CheckDto> getChecks()
	{
		// Generated checks are rendered per request rather than stored, so a
		// blacklist edit takes effect on the next agent run with nothing to
		// resync. Retired checks are left out for the same reason: the agent
		// asks again on every run, so it simply stops asking this one.
		return itemRepository.listActive().stream()
			.map(item -> {
				ResolvedCheck resolved = checkResolver.resolve(item);
				return new CheckDto(item.id, item.getName(), resolved.query(), resolved.expression());
			})
			.toList();
	}

	/**
	 * Takes one submitted run.
	 *
	 * <p>
	 * Submitting is a decision now — the agent runs the checks, shows them, and
	 * asks before it sends anything — so what arrives here is a machine someone
	 * meant to report on. A clean run is compliant on the spot. A run with
	 * failures opens a report with a deadline, and stays open until it is fixed
	 * or the deadline decides it.
	 */
	@POST
	@Path("/reports")
	@Consumes(MediaType.APPLICATION_JSON)
	@Transactional
	public Response pushReport(ReportPayload payload, @Context UriInfo uriInfo)
	{
		// An agent that fetched the catalogue before a check was retired still
		// reports on it. Those answers are kept — they are what the device
		// said — but a check no longer in force cannot open a report or hold
		// one open, so the verdict is taken without them.
		boolean allPassed = payload.results().stream()
			.filter(this::stillInForce)
			.allMatch(ResultPayload::passed);

		Report report = reportRepository.findOpenByDeviceAndUser(payload.deviceId(), payload.userId())
			.map(open -> anotherAttempt(open, payload, allPassed))
			.orElseGet(() -> firstAttempt(payload, allPassed));

		// Agents authenticate as their own bearer identity, which never passes
		// through the web login augmentor — so the local user record (and with
		// it the address every notification goes to) is synced here.
		AppUser reportingUser = syncReportingUser();
		report.setAppUser(reportingUser);

		recordOsVersion(report, payload.osVersion());
		replaceInventory(report, payload.installedApps());
		upsertDevice(payload, reportingUser);

		String reportUrl = uriInfo.getBaseUri().resolve("Reports/show/" + report.id).toString();
		Instant deadline = report.getStatus() == ReportStatus.OPEN ? report.getDeadline() : null;
		return Response.ok(new ReportResponse(reportUrl, deadline)).build();
	}

	/**
	 * The first run this device has submitted since its last report was
	 * decided. A clean one is finished as it arrives; a failing one gets the
	 * remediation window, and {@code DeadlineJob} has the last word on it.
	 */
	private Report firstAttempt(ReportPayload payload, boolean allPassed)
	{
		Report report = new Report();
		report.setDeviceId(payload.deviceId());
		report.setUserId(payload.userId());
		report.setKeycloakUser(jwt.<String> claim("preferred_username").orElse(jwt.getSubject()));
		report.setCheckedAt(payload.checkedAt() != null ? payload.checkedAt() : Instant.now());
		reportRepository.persist(report);

		persistResults(report, payload.results());

		if (allPassed)
		{
			report.setStatus(ReportStatus.COMPLIANT);
			report.setFinalizedAt(Instant.now());
		}
		else
		{
			report.setStatus(ReportStatus.OPEN);
			report.setDeadline(Instant.now().plus(remediationDays, ChronoUnit.DAYS));
		}

		// Only a report that is new here mails from here. One that was already
		// open has said its piece, and mails again when it is decided — by the
		// next attempt that passes, or by the deadline job. The dispatcher
		// holds either back until the failed checks have been explained.
		mailDispatcher.request(report);
		return report;
	}

	/**
	 * A device reporting again while its report is still open is having another
	 * go at the same failure, so the run replaces what the report holds rather
	 * than opening a second one beside it. Everything passing closes it; the
	 * deadline does not move either way, or a run that still fails would buy
	 * itself another week by being uploaded.
	 */
	private Report anotherAttempt(Report report, ReportPayload payload, boolean allPassed)
	{
		resultRepository.delete("report", report);
		persistResults(report, payload.results());
		report.setCheckedAt(payload.checkedAt() != null ? payload.checkedAt() : Instant.now());

		if (allPassed)
		{
			finalizer.finalizeReport(report, true);
		}
		return report;
	}

	/**
	 * Keeps the {@link AppUser} record in step with the agent's bearer token.
	 * Keycloak and Entra both put {@code email}, {@code given_name} and
	 * {@code family_name} in the access token, so the same claims the browser
	 * login syncs are available here.
	 */
	private AppUser syncReportingUser()
	{
		return userSyncService.syncUser(
			jwt.getSubject(),
			jwt.<String> claim("email").orElse(null),
			jwt.<String> claim("given_name").orElse(null),
			jwt.<String> claim("family_name").orElse(null));
	}

	/**
	 * Whether the check this answer is about is still one the estate is
	 * measured by. An answer to a retired check is recorded, not counted.
	 */
	private boolean stillInForce(ResultPayload result)
	{
		ComplianceItem item = itemRepository.findById(result.itemId());
		return item != null && !item.isRetired();
	}

	private void persistResults(Report report, List<ResultPayload> results)
	{
		for (ResultPayload rp : results)
		{
			ComplianceItem item = itemRepository.findById(rp.itemId());
			if (item == null)
			{
				continue;
			}
			ComplianceResult result = new ComplianceResult();
			result.setItem(item);
			result.setReport(report);
			result.setPassed(rp.passed());
			result.setOutput(rp.output());
			resultRepository.persist(result);
			// Failed checks are explained by ResultEnrichmentJob afterwards.
			// Calling the model here made the agent wait for one round trip per
			// failure and time out on a device with several.
		}
	}

	/**
	 * Files what the device said it was running, along with the newest macOS
	 * Apple had published at that moment.
	 * <p>
	 * The latter is stamped now rather than read back later so the report keeps
	 * its own verdict: a machine that was current in March should not turn red
	 * in October because Apple shipped something.
	 */
	private void recordOsVersion(Report report, OsVersionPayload os)
	{
		if (os == null)
		{
			return;
		}
		report.setOsName(os.name());
		report.setOsVersion(os.version());
		report.setOsBuild(os.build());
		releaseCatalog.latestPublicVersion()
			.ifPresent(latest -> report.setOsLatestVersion(latest.toString()));
	}

	/**
	 * The inventory is a snapshot, so another attempt at an open report
	 * replaces it wholesale rather than accumulating stale rows.
	 */
	private void replaceInventory(Report report, List<InstalledAppPayload> apps)
	{
		installedAppRepository.delete("report", report);
		if (apps == null)
		{
			return;
		}
		for (InstalledAppPayload payload : apps)
		{
			InstalledApp app = new InstalledApp();
			app.setReport(report);
			app.setSource(payload.source());
			app.setName(payload.name());
			app.setIdentifier(payload.identifier());
			app.setVersion(payload.version());
			app.setPath(payload.path());
			installedAppRepository.persist(app);
		}
	}

	/**
	 * Creates or updates the {@link Device} row for the reporting device and
	 * manages its periodic reporting flow:
	 * <ul>
	 * <li>First report: persists a new Device and starts the first
	 * {@link PeriodicReportingFlow} instance.</li>
	 * <li>Subsequent reports: updates {@code lastReportAt} and fires a
	 * {@link PeriodicFlowTrigger} to notify the waiting flow that the device
	 * reported on time. The flow's internal callback will then restart the flow
	 * for the next cycle.</li>
	 * </ul>
	 */
	private void upsertDevice(ReportPayload payload, AppUser reportingUser)
	{
		String keycloakUser = jwt.<String> claim("preferred_username").orElse(jwt.getSubject());
		Device device = deviceRepository.findByDeviceId(payload.deviceId()).orElse(null);

		if (device == null)
		{
			// First time we've seen this device — create it and start the
			// periodic flow
			device = new Device();
			device.setDeviceId(payload.deviceId());
			device.setUserId(payload.userId());
			device.setKeycloakUser(keycloakUser);
			device.setAppUser(reportingUser);
			device.setLastReportAt(Instant.now());
			deviceRepository.persist(device);

			var wi = periodicReportingFlow.instance(Map.of("deviceId", payload.deviceId()));
			device.setPeriodicFlowInstanceId(wi.id());
			workflowStartTrigger.fire(new WorkflowStartTrigger(wi));
		}
		else
		{
			// Device already known — notify the waiting flow and let its
			// callback restart it
			device.setLastReportAt(Instant.now());
			device.setUserId(payload.userId());
			device.setKeycloakUser(keycloakUser);
			device.setAppUser(reportingUser);
			// A fresh report starts a fresh cycle, so the nudge for the last
			// one is spent — clearing it is what makes the reminder recur.
			device.setReminderSentAt(null);
			periodicFlowTrigger.fire(
				new PeriodicFlowTrigger(device.getDeviceId(), device.getPeriodicFlowInstanceId(), true));
		}
	}
}
