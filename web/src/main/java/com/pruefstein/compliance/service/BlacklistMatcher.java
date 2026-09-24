package com.pruefstein.compliance.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pruefstein.compliance.domain.AppMatcher;
import com.pruefstein.compliance.domain.BlockedApp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Maps the rows a device reported back onto the rules that forbade them, so a
 * failed blacklist check can name the policy reason rather than just the app.
 * <p>
 * Matching semantics mirror the generated SQL exactly — case-insensitive, with
 * {@code %} and {@code _} as {@code LIKE} wildcards — so the server never
 * disagrees with the device about what matched.
 */
@ApplicationScoped
public class BlacklistMatcher
{
	@Inject
	ObjectMapper objectMapper;

	public List<BlockedApp> match(String outputJson, List<BlockedApp> rules)
	{
		if (outputJson == null || outputJson.isBlank())
		{
			return List.of();
		}
		List<Map<String, Object>> rows;
		try
		{
			rows = objectMapper.readValue(outputJson, new TypeReference<List<Map<String, Object>>>()
			{
			});
		}
		catch (Exception _)
		{
			return List.of();
		}

		LinkedHashSet<BlockedApp> matched = new LinkedHashSet<>();
		for (Map<String, Object> row : rows)
		{
			for (BlockedApp rule : rules)
			{
				if (matchesRow(rule, row))
				{
					matched.add(rule);
				}
			}
		}
		return List.copyOf(matched);
	}

	/**
	 * Renders the matched rules as the policy context handed to the AI tip.
	 */
	public String reasons(List<BlockedApp> matched)
	{
		StringBuilder sb = new StringBuilder();
		for (BlockedApp rule : matched)
		{
			sb.append("- ").append(rule.getLabel()).append(": ")
				.append(rule.getReason() == null || rule.getReason().isBlank()
					? "not approved for use on managed devices"
					: rule.getReason().strip())
				.append('\n');
		}
		return sb.toString();
	}

	/**
	 * The first rule that forbids this application, or {@code null} if none do.
	 * Used to flag rows in a report's installed-app inventory.
	 */
	public BlockedApp ruleFor(String source, String name, String identifier, List<BlockedApp> rules)
	{
		return rules.stream()
			.filter(rule -> matchesApp(rule, source, name, identifier))
			.findFirst()
			.orElse(null);
	}

	private boolean matchesRow(BlockedApp rule, Map<String, Object> row)
	{
		return matchesApp(rule, string(row.get("source")), string(row.get("name")), string(row.get("identifier")));
	}

	private static boolean matchesApp(BlockedApp rule, String source, String name, String identifier)
	{
		boolean fromHomebrew = source != null && source.startsWith("brew");
		for (AppMatcher matcher : rule.getMatchers())
		{
			boolean hit = switch (matcher.getType())
			{
				case HOMEBREW -> fromHomebrew && matches(matcher.getPattern(), name);
				case BUNDLE_ID -> !fromHomebrew && matches(matcher.getPattern(), identifier);
				case APP_NAME -> !fromHomebrew && matches(matcher.getPattern(), name);
			};
			if (hit)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean matches(String pattern, String value)
	{
		if (pattern == null || pattern.isBlank() || value == null)
		{
			return false;
		}
		return likeToRegex(pattern.strip().toLowerCase(Locale.ROOT))
			.matcher(value.toLowerCase(Locale.ROOT))
			.matches();
	}

	private static Pattern likeToRegex(String like)
	{
		StringBuilder regex = new StringBuilder();
		StringBuilder literal = new StringBuilder();
		for (char c : like.toCharArray())
		{
			if (c == '%' || c == '_')
			{
				if (!literal.isEmpty())
				{
					regex.append(Pattern.quote(literal.toString()));
					literal.setLength(0);
				}
				regex.append(c == '%' ? ".*" : ".");
			}
			else
			{
				literal.append(c);
			}
		}
		if (!literal.isEmpty())
		{
			regex.append(Pattern.quote(literal.toString()));
		}
		return Pattern.compile(regex.toString(), Pattern.DOTALL);
	}

	private static String string(Object value)
	{
		return value == null ? null : value.toString();
	}
}
