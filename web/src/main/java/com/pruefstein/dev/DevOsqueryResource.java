package com.pruefstein.dev;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.pruefstein.compliance.service.ComplianceEvaluator;
import com.pruefstein.compliance.service.ComplianceResultAiService;
import com.pruefstein.compliance.service.ComplianceResultExplanation;
import com.pruefstein.shared.util.Markdown;
import io.quarkus.runtime.LaunchMode;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dev/osquery")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("${pruefstein.security.admin-role:admin}")
public class DevOsqueryResource
{
	private static final Logger LOG = LoggerFactory.getLogger(DevOsqueryResource.class);

	@Inject
	ComplianceEvaluator evaluator;

	@Inject
	ComplianceResultAiService aiService;

	@POST
	@Path("/run")
	@Consumes(MediaType.APPLICATION_JSON)
	public OsqueryResult run(JsonNode body)
	{
		if (LaunchMode.current() != LaunchMode.DEVELOPMENT)
		{
			throw new NotFoundException();
		}

		String query = body != null && body.has("query") ? body.get("query").asText() : null;
		String expression = body != null && body.has("expression") ? body.get("expression").asText() : null;
		String name = body != null && body.has("name") ? body.get("name").asText() : null;

		if (query == null || query.isBlank())
		{
			return OsqueryResult.error("query is required");
		}

		OsqueryResult execResult = executeOsquery(query);
		if (execResult.error() != null)
		{
			return execResult;
		}

		OsqueryResult result = evaluateExpression(execResult.output(), expression);

		// Unlike an agent report, this playground result is never persisted, so
		// the tip is regenerated on every run.
		if (Boolean.FALSE.equals(result.passed()))
		{
			result = withTip(result, name, query, expression);
		}
		return result;
	}

	private OsqueryResult withTip(OsqueryResult result, String name, String query, String expression)
	{
		try
		{
			ComplianceResultExplanation exp = aiService.explain(
				name != null && !name.isBlank() ? name : "(unnamed check)",
				query, expression, result.output());
			return result.withTip(exp.shortDescription(), exp.longExplanation());
		}
		catch (Exception e)
		{
			LOG.warn("AI tip skipped for local test run.", e);
			return result;
		}
	}

	private OsqueryResult executeOsquery(String query)
	{
		try
		{
			ProcessBuilder pb = new ProcessBuilder("osqueryi", "--json", query);
			pb.redirectErrorStream(true);
			Process process = pb.start();

			if (!process.waitFor(10, TimeUnit.SECONDS))
			{
				process.destroyForcibly();
				return OsqueryResult.error("osqueryi timed out after 10 seconds");
			}

			String output = new String(process.getInputStream().readAllBytes()).strip();
			int exitCode = process.exitValue();

			if (exitCode != 0)
			{
				return OsqueryResult.error(output.isEmpty() ? "osqueryi exited with code " + exitCode : output);
			}
			return OsqueryResult.withOutput(output, null);
		}
		catch (IOException e)
		{
			String msg = e.getMessage();
			if (msg != null && msg.contains("No such file"))
			{
				return OsqueryResult.error("osqueryi not found — is osquery installed and on PATH?");
			}
			return OsqueryResult.error(msg != null ? msg : "Unknown IO error");
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return OsqueryResult.error("Interrupted");
		}
	}

	private OsqueryResult evaluateExpression(String output, String expression)
	{
		if (expression == null || expression.isBlank())
		{
			return OsqueryResult.withOutput(output, null);
		}
		try
		{
			boolean passed = evaluator.evaluate(output, expression);
			return OsqueryResult.withOutput(output, passed);
		}
		catch (Exception e)
		{
			return OsqueryResult.withOutput(output, null).withExpressionError(e.getMessage());
		}
	}

	public record OsqueryResult(String output, Boolean passed, String error, String expressionError,
		String tipShortDescription, String tipLongExplanation, String tipLongExplanationHtml)
	{
		static OsqueryResult withOutput(String output, Boolean passed)
		{
			return new OsqueryResult(output, passed, null, null, null, null, null);
		}

		OsqueryResult withExpressionError(String msg)
		{
			return new OsqueryResult(this.output, null, null, msg, null, null, null);
		}

		OsqueryResult withTip(String shortDescription, String longExplanation)
		{
			return new OsqueryResult(this.output, this.passed, this.error, this.expressionError,
				shortDescription, longExplanation, Markdown.toHtml(longExplanation));
		}

		static OsqueryResult error(String error)
		{
			return new OsqueryResult(null, null, error, null, null, null, null);
		}
	}
}
