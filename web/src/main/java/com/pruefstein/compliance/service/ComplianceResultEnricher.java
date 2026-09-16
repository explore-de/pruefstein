package com.pruefstein.compliance.service;

import java.util.ArrayList;
import java.util.List;

import com.pruefstein.compliance.domain.AppBlacklistCheck;
import com.pruefstein.compliance.domain.BlockedApp;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ComplianceResult;
import com.pruefstein.compliance.repository.BlockedAppRepository;
import com.pruefstein.compliance.repository.ComplianceResultRepository;
import com.pruefstein.compliance.service.CheckResolver.ResolvedCheck;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The database half of explaining a failed check.
 *
 * <p>
 * Reading what the model needs and storing what it said are two short
 * transactions, deliberately not one: the call in between takes seconds, and a
 * transaction spanning it would hold a database connection open for the whole
 * conversation. {@link ResultEnrichmentJob} owns the middle.
 */
@ApplicationScoped
public class ComplianceResultEnricher
{
	private static final Logger LOG = LoggerFactory.getLogger(ComplianceResultEnricher.class);

	@Inject
	ComplianceResultRepository resultRepository;

	@Inject
	BlockedAppRepository blockedAppRepository;

	@Inject
	BlacklistMatcher blacklistMatcher;

	@Inject
	CheckResolver checkResolver;

	/**
	 * Everything the model needs about one failed check, read while a
	 * transaction is open so the call itself does not need one.
	 *
	 * @param reasons
	 *            why the applications found on the device are forbidden, or
	 *            {@code null} for a check that is not a blacklist check
	 */
	public record EnrichmentRequest(long resultId, String checkName, String query, String expression,
		String output, String reasons)
	{
		public boolean blacklist()
		{
			return reasons != null;
		}
	}

	/**
	 * Failed checks still waiting for an explanation, newest first.
	 *
	 * <p>
	 * Newest first, not oldest, so that a row nothing can explain cannot starve
	 * the queue. Nothing records an attempt, so a result the model keeps
	 * refusing stays selected forever; ordered the other way, {@code limit} of
	 * those at the head of the table would be the only rows ever offered and no
	 * new failure would be explained again.
	 *
	 * <p>
	 * Checks with no output are skipped rather than retried: the agent records
	 * a check that errored or timed out as failed with a {@code null} output,
	 * and there is nothing in that for a model to explain.
	 */
	@Transactional
	public List<EnrichmentRequest> findPending(int limit)
	{
		List<ComplianceResult> candidates = resultRepository
			.find("passed = false and item.retiredAt is null"
				+ " and aiShortDescription is null and output is not null",
				Sort.by("id").descending())
			.page(0, limit)
			.list();

		// One unresolvable check does not cost the rest of the batch: reading
		// them is what resolves a generated query, and that can throw.
		List<EnrichmentRequest> requests = new ArrayList<>(candidates.size());
		for (ComplianceResult candidate : candidates)
		{
			try
			{
				requests.add(toRequest(candidate));
			}
			catch (Exception e)
			{
				LOG.warn("Could not prepare an explanation for result {}.", candidate.id, e);
			}
		}
		return requests;
	}

	@Transactional
	public void save(long resultId, ComplianceResultExplanation explanation)
	{
		if (explanation == null || explanation.shortDescription() == null
			|| explanation.shortDescription().isBlank())
		{
			// Writing a blank leaves the row matching findPending's predicate,
			// so it would come back every cycle and never be finished with.
			LOG.warn("The model returned no short description for result {}; leaving it unexplained.",
				resultId);
			return;
		}

		ComplianceResult result = resultRepository.findById(resultId);
		if (result == null)
		{
			// The report was deleted while the model was thinking
			return;
		}
		result.setAiShortDescription(explanation.shortDescription());
		result.setAiLongExplanation(explanation.longExplanation());
	}

	private EnrichmentRequest toRequest(ComplianceResult result)
	{
		ComplianceItem item = result.getItem();
		if (item instanceof AppBlacklistCheck)
		{
			List<BlockedApp> matched = blacklistMatcher.match(result.getOutput(),
				blockedAppRepository.listEnabled());
			return new EnrichmentRequest(result.id, item.getName(), null, null, result.getOutput(),
				blacklistMatcher.reasons(matched));
		}
		ResolvedCheck resolved = checkResolver.resolve(item);
		return new EnrichmentRequest(result.id, item.getName(), resolved.query(), resolved.expression(),
			result.getOutput(), null);
	}
}
