package com.pruefstein.compliance.domain;

import com.pruefstein.report.domain.Report;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
public class ComplianceResult extends PanacheEntity
{
	@ManyToOne(optional = false)
	private ComplianceItem item;

	@ManyToOne(optional = false)
	private Report report;

	private boolean passed;

	@Column(columnDefinition = "TEXT")
	private String output;

	@Column(columnDefinition = "TEXT")
	private String aiShortDescription;

	@Column(columnDefinition = "TEXT")
	private String aiLongExplanation;

	public ComplianceItem getItem()
	{
		return item;
	}

	public void setItem(ComplianceItem item)
	{
		this.item = item;
	}

	public Report getReport()
	{
		return report;
	}

	public void setReport(Report report)
	{
		this.report = report;
	}

	/**
	 * What the device answered when the report was filed. This is the record
	 * and never changes — read {@link #isFailing()} to know whether it still
	 * counts against anyone.
	 */
	public boolean isPassed()
	{
		return passed;
	}

	/** Whether the check behind this result has since been retired. */
	public boolean isCheckRetired()
	{
		return item != null && item.isRetired();
	}

	/**
	 * Whether this result is still held against the device.
	 * <p>
	 * A failure against a check that has since been retired is not: the estate
	 * decided the check was not worth measuring, and it would be unfair — and
	 * unfixable — to keep a device marked down for a rule nobody enforces any
	 * more. The recorded answer stays {@code false} either way, so the report
	 * can still show what the device actually said and why it no longer counts.
	 */
	public boolean isFailing()
	{
		return !passed && !isCheckRetired();
	}

	public void setPassed(boolean passed)
	{
		this.passed = passed;
	}

	public String getOutput()
	{
		return output;
	}

	public void setOutput(String output)
	{
		this.output = output;
	}

	public String getAiShortDescription()
	{
		return aiShortDescription;
	}

	public void setAiShortDescription(String aiShortDescription)
	{
		this.aiShortDescription = aiShortDescription;
	}

	public String getAiLongExplanation()
	{
		return aiLongExplanation;
	}

	public void setAiLongExplanation(String aiLongExplanation)
	{
		this.aiLongExplanation = aiLongExplanation;
	}
}
