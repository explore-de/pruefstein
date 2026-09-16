package com.pruefstein.osversion.repository;

import java.util.Optional;

import com.pruefstein.osversion.domain.MacOsRelease;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MacOsReleaseRepository implements PanacheRepository<MacOsRelease>
{
	public Optional<MacOsRelease> findByVersionAndBuild(String productVersion, String build)
	{
		return find("productVersion = ?1 and build = ?2", productVersion, build).firstResultOptional();
	}
}
