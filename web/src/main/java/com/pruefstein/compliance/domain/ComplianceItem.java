package com.pruefstein.compliance.domain;

import java.time.Instant;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

/**
 * A check that belongs to a compliance group and produces one
 * {@link ComplianceResult} per report.
 * <p>
 * Everything shared by every kind of check lives here — a name, its group, and
 * its history. What a check actually asks the device is left to the subclass:
 * {@link ExpressionCheck} carries admin-authored osquery SQL, while
 * {@link AppBlacklistCheck} derives its SQL from the {@link BlockedApp} list.
 * Use {@code CheckResolver} to obtain the query and expression for any check
 * rather than branching on the type at the call site.
 * <p>
 * Checks are retired rather than deleted. A check that has ever been reported
 * on is part of the evidence behind those reports, so removing the row would
 * take the history with it — see {@link #retire()}.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "check_type")
public abstract class ComplianceItem extends PanacheEntity
{
	private String name;

	/**
	 * The ISO/IEC 27001:2022 Annex A control this check evidences, such as
	 * {@code A.8.8} — the group holds only the broader theme.
	 *
	 * <p>
	 * Null for a check an administrator wrote themselves: we know which theme
	 * they filed it under, and inventing a control number on their behalf would
	 * put a claim in front of an auditor that nobody made.
	 */
	@Column(length = 16)
	private String control;

	@ManyToOne
	private ComplianceGroup group;

	@OneToMany(mappedBy = "item")
	private List<ComplianceResult> results;

	/**
	 * When an admin retired this check, or {@code null} while it is still in
	 * force. A retired check is gone from the catalogue, from what the agent
	 * fetches and from every count, but its rows stay where they are.
	 */
	private Instant retiredAt;

	/**
	 * The library entry this check was created from, or {@code null} for one an
	 * admin wrote. Provenance only — the check is free to be edited away from
	 * the entry, and nothing reconciles it back.
	 */
	@Column(length = 96)
	private String libraryKey;

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public String getControl()
	{
		return control;
	}

	public void setControl(String control)
	{
		this.control = control;
	}

	public ComplianceGroup getGroup()
	{
		return group;
	}

	public void setGroup(ComplianceGroup group)
	{
		this.group = group;
	}

	public List<ComplianceResult> getResults()
	{
		return results;
	}

	public void setResults(List<ComplianceResult> results)
	{
		this.results = results;
	}

	public Instant getRetiredAt()
	{
		return retiredAt;
	}

	public String getLibraryKey()
	{
		return libraryKey;
	}

	public void setLibraryKey(String libraryKey)
	{
		this.libraryKey = libraryKey;
	}

	public boolean isRetired()
	{
		return retiredAt != null;
	}

	/**
	 * Takes this check out of force without touching what it already recorded.
	 * <p>
	 * A hard delete is not available to us: every result of this check names it
	 * through a foreign key, so removing the row would mean removing the
	 * findings that justify past reports. Retiring keeps the evidence and stops
	 * the check from being asked again. Idempotent — retiring twice keeps the
	 * first date, which is the one the reports were judged against.
	 */
	public void retire()
	{
		if (retiredAt == null)
		{
			retiredAt = Instant.now();
		}
	}

	/**
	 * Whether an admin can edit this check's query and expression directly.
	 * Generated checks are managed through their own screen instead.
	 */
	public boolean isEditable()
	{
		return true;
	}
}
