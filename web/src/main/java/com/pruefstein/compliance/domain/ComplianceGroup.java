package com.pruefstein.compliance.domain;

import java.time.Instant;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

/**
 * A family of related checks — in practice an ISO 27001 control family.
 * <p>
 * Like the checks inside it, a group is retired rather than deleted: past
 * reports name the group of every check they record, so the row has to outlive
 * the catalogue entry.
 */
@Entity
public class ComplianceGroup extends PanacheEntity
{
	private String name;

	@OneToMany(mappedBy = "group")
	private List<ComplianceItem> items;

	/**
	 * When an admin retired this group, or {@code null} while it is still in
	 * force. Retiring a group retires the checks in it — see {@link #retire()}.
	 */
	private Instant retiredAt;

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public List<ComplianceItem> getItems()
	{
		return items;
	}

	public void setItems(List<ComplianceItem> items)
	{
		this.items = items;
	}

	public Instant getRetiredAt()
	{
		return retiredAt;
	}

	public boolean isRetired()
	{
		return retiredAt != null;
	}

	/**
	 * Takes this group and every check in it out of force.
	 * <p>
	 * A check has no life outside its group — the group screen is the only way
	 * to reach one — so retiring the group without its checks would leave them
	 * running with nowhere to manage them. Deleting instead is not open to us
	 * for the same reason it is not open for a check: the reports that name
	 * this group would lose the name. Idempotent.
	 */
	public void retire()
	{
		if (retiredAt == null)
		{
			retiredAt = Instant.now();
		}
		if (items != null)
		{
			items.forEach(ComplianceItem::retire);
		}
	}
}
