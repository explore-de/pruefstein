package com.pruefstein.osversion.domain;

import java.time.Instant;
import java.time.LocalDate;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One macOS release as Apple's public asset feed described it.
 * <p>
 * Kept in the database rather than held in memory so the report page can still
 * say what the newest release is when Apple's feed is unreachable, and so the
 * catalogue grows into a history of what shipped when.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "productVersion", "build" }))
public class MacOsRelease extends PanacheEntity
{
	private String productVersion;

	private String build;

	private LocalDate postingDate;

	/** When this row was last confirmed to be in the feed. */
	private Instant seenAt;

	/**
	 * Whether Apple listed this under its public asset sets. Only public
	 * releases decide what "latest" means — a seed build must never make the
	 * whole estate look out of date.
	 */
	private boolean publicRelease;

	public String getProductVersion()
	{
		return productVersion;
	}

	public void setProductVersion(String productVersion)
	{
		this.productVersion = productVersion;
	}

	public String getBuild()
	{
		return build;
	}

	public void setBuild(String build)
	{
		this.build = build;
	}

	public LocalDate getPostingDate()
	{
		return postingDate;
	}

	public void setPostingDate(LocalDate postingDate)
	{
		this.postingDate = postingDate;
	}

	public Instant getSeenAt()
	{
		return seenAt;
	}

	public void setSeenAt(Instant seenAt)
	{
		this.seenAt = seenAt;
	}

	public boolean isPublicRelease()
	{
		return publicRelease;
	}

	public void setPublicRelease(boolean publicRelease)
	{
		this.publicRelease = publicRelease;
	}

	public MacOsVersion version()
	{
		return MacOsVersion.parse(productVersion).orElse(null);
	}
}
