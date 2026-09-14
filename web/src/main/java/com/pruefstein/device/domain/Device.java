package com.pruefstein.device.domain;

import java.time.Instant;

import com.pruefstein.user.domain.AppUser;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

/**
 * Tracks the last-seen timestamp and active periodic reporting flow instance
 * for each known device. One row per unique {@code deviceId}.
 */
@Entity
public class Device extends PanacheEntity
{
	@Column(unique = true, nullable = false)
	private String deviceId;

	private String userId;

	private String keycloakUser;

	private Instant lastReportAt;

	/**
	 * Who to write to when this device is due again. {@code keycloakUser} is a
	 * username rather than an address, so the mail jobs would otherwise have
	 * nothing to send to.
	 */
	@ManyToOne
	private AppUser appUser;

	/**
	 * Set once the "time to report again" mail went out for the current cycle,
	 * and cleared whenever a report arrives. One nudge per cycle, however many
	 * times the hourly job looks.
	 */
	private Instant reminderSentAt;

	@Column(length = 64)
	private String periodicFlowInstanceId;

	public String getDeviceId()
	{
		return deviceId;
	}

	public void setDeviceId(String deviceId)
	{
		this.deviceId = deviceId;
	}

	public AppUser getAppUser()
	{
		return appUser;
	}

	public void setAppUser(AppUser appUser)
	{
		this.appUser = appUser;
	}

	public Instant getReminderSentAt()
	{
		return reminderSentAt;
	}

	public void setReminderSentAt(Instant reminderSentAt)
	{
		this.reminderSentAt = reminderSentAt;
	}

	public String getUserId()
	{
		return userId;
	}

	public void setUserId(String userId)
	{
		this.userId = userId;
	}

	public String getKeycloakUser()
	{
		return keycloakUser;
	}

	public void setKeycloakUser(String keycloakUser)
	{
		this.keycloakUser = keycloakUser;
	}

	public Instant getLastReportAt()
	{
		return lastReportAt;
	}

	public void setLastReportAt(Instant lastReportAt)
	{
		this.lastReportAt = lastReportAt;
	}

	public String getPeriodicFlowInstanceId()
	{
		return periodicFlowInstanceId;
	}

	public void setPeriodicFlowInstanceId(String periodicFlowInstanceId)
	{
		this.periodicFlowInstanceId = periodicFlowInstanceId;
	}
}
