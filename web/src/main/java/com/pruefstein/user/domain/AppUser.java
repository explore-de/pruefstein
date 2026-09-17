package com.pruefstein.user.domain;

import java.util.List;

import com.pruefstein.report.domain.Report;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

@Entity
public class AppUser extends PanacheEntity
{
	@Column(unique = true)
	private String oidcSubject;

	private String firstname;
	private String lastname;
	private String mail;

	@OneToMany(mappedBy = "appUser")
	private List<Report> reports;

	public String getOidcSubject()
	{
		return oidcSubject;
	}

	public void setOidcSubject(String oidcSubject)
	{
		this.oidcSubject = oidcSubject;
	}

	public String getFirstname()
	{
		return firstname;
	}

	public void setFirstname(String firstname)
	{
		this.firstname = firstname;
	}

	public String getLastname()
	{
		return lastname;
	}

	public void setLastname(String lastname)
	{
		this.lastname = lastname;
	}

	public String getMail()
	{
		return mail;
	}

	public void setMail(String mail)
	{
		this.mail = mail;
	}

	/**
	 * First and last name as a person reads them, or {@code null} when neither
	 * is known — callers fall back to whatever else identifies the user.
	 */
	public String getFullName()
	{
		String name = ((firstname != null ? firstname : "") + " " + (lastname != null ? lastname : "")).strip();
		return name.isEmpty() ? null : name;
	}

	public List<Report> getReports()
	{
		return reports;
	}

	public void setReports(List<Report> reports)
	{
		this.reports = reports;
	}
}
