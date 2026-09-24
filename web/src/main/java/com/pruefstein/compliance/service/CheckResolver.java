package com.pruefstein.compliance.service;

import com.pruefstein.compliance.domain.AppBlacklistCheck;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.BlockedAppRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Turns any {@link ComplianceItem} into the query and expression a device
 * should actually run.
 * <p>
 * This is the only place that knows a check can be generated rather than
 * authored, so callers — the agent API, the group screen, the local test runner
 * — treat every check the same way.
 */
@ApplicationScoped
public class CheckResolver
{
	@Inject
	BlockedAppRepository blockedAppRepository;

	@Inject
	BlacklistQueryGenerator blacklistQueryGenerator;

	/**
	 * @param query
	 *            the osquery SQL to run on the device
	 * @param expression
	 *            the JEXL expression that must hold for the check to pass
	 */
	public record ResolvedCheck(String query, String expression)
	{
	}

	public ResolvedCheck resolve(ComplianceItem item)
	{
		return switch (item)
		{
			case ExpressionCheck check -> new ResolvedCheck(check.getQuery(), check.getExpectedExpression());
			case AppBlacklistCheck _ -> new ResolvedCheck(
				blacklistQueryGenerator.generate(blockedAppRepository.listEnabled()),
				BlacklistQueryGenerator.EXPRESSION);
			default -> throw new IllegalStateException(
				"No resolver for check type " + item.getClass().getSimpleName());
		};
	}
}
