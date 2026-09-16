package com.pruefstein.agent.client;

import java.time.Instant;
import java.util.List;

public record ReportPayload(String deviceId, String userId, Instant checkedAt, List<ResultPayload> results,
	List<InstalledAppPayload> installedApps, OsVersionPayload osVersion)
{
}
