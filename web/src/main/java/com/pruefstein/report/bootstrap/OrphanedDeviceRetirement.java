package com.pruefstein.report.bootstrap;

import java.util.List;

import com.pruefstein.device.domain.Device;
import com.pruefstein.device.repository.DeviceRepository;
import com.pruefstein.report.service.DeviceRetirement;
import com.pruefstein.shared.bootstrap.SeedLedger;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retires the devices of people deleted before deleting retired them.
 *
 * <p>
 * An unowned device is not proof of that: devices registered before they
 * carried an owner, and silent since, have none either — and those are the
 * people who most need chasing. So only a device whose login no remaining user
 * answers to, by address or subject, is retired.
 *
 * <p>
 * Ledgered, so it runs once per database; drop it once every deployment has.
 * Skipped in dev mode, whose demo fleet is seeded without owners.
 */
@ApplicationScoped
public class OrphanedDeviceRetirement
{
	static final String LEDGER_KEY = "device#orphaned-device-retirement";

	private static final Logger LOG = LoggerFactory.getLogger(OrphanedDeviceRetirement.class);

	@Inject
	SeedLedger ledger;

	@Inject
	DeviceRepository deviceRepository;

	@Inject
	DeviceRetirement deviceRetirement;

	@Transactional
	void retireOnStartup(@Observes StartupEvent event)
	{
		if (LaunchMode.current() == LaunchMode.DEVELOPMENT || !ledger.claim(LEDGER_KEY))
		{
			return;
		}
		retireOrphans();
	}

	/** @return how many devices were retired */
	int retireOrphans()
	{
		List<Device> orphaned = deviceRepository.list("appUser is null and not exists ("
			+ "select u.id from AppUser u where lower(u.mail) = lower(keycloakUser) or u.oidcSubject = keycloakUser)");
		if (orphaned.isEmpty())
		{
			return 0;
		}
		int archived = deviceRetirement.retire(null, orphaned);
		LOG.info("Retired {} device(s) left by deleted users and archived {} of their report(s)",
			orphaned.size(), archived);
		return orphaned.size();
	}
}
