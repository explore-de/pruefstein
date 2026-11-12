package com.pruefstein.agent.client;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "pruefstein-api")
@RegisterProvider(BearerTokenFilter.class)
@Produces(MediaType.APPLICATION_JSON)
public interface PruefsteinClient
{
	@GET
	@Path("/api/checks")
	List<CheckItem> getChecks();

	@GET
	@Path("/api/agent-release")
	AgentRelease agentRelease();

	@POST
	@Path("/api/os-version")
	@Consumes(MediaType.APPLICATION_JSON)
	OsVersionVerdict assessOsVersion(OsVersionPayload os);

	@POST
	@Path("/api/reports")
	@Consumes(MediaType.APPLICATION_JSON)
	ReportResponse pushReport(ReportPayload report);
}
