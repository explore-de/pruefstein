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
 * Retires the hostname records of Macs that reported under their hardware UUID
 * before ingest learned to do it, so they stop being filed MISSING.
 *
 * <p>
 * Ledgered, so it runs once per database; drop it once every deployment has.
 */
@ApplicationScoped
public class HostnameStandInRetirement
{
	static final String LEDGER_KEY = "device#hostname-stand-in-retirement";

	private static final Logger LOG = LoggerFactory.getLogger(HostnameStandInRetirement.class);

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
		retireStandIns();
	}

	/** @return how many devices were retired */
	int retireStandIns()
	{
		List<Device> standIns = deviceRepository.findHostnameStandIns();
		int archived = 0;
		for (Device standIn : standIns)
		{
			archived += deviceRetirement.retireStandIn(standIn);
		}
		if (!standIns.isEmpty())
		{
			LOG.info("Retired {} hostname stand-in device(s) and archived {} of their report(s)",
				standIns.size(), archived);
		}
		return standIns.size();
	}
}
