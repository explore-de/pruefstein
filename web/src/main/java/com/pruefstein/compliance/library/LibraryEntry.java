package com.pruefstein.compliance.library;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * One check in the built-in library, as read from
 * {@code compliance-library/<key>.json}.
 * <p>
 * The key is the file name and is permanent: the seed ledger remembers it, and
 * every check created from this entry carries it, so renaming the file makes
 * both believe it is a different check. Add entries freely; never repurpose a
 * key.
 *
 * @param key
 *            the file name without {@code .json}; not part of the file itself,
 *            so the two can never disagree
 * @param group
 *            the name of the compliance group a new check goes into, or
 *            {@code null} for a check that lives outside the group screen
 * @param description
 *            why the check exists, typically the control it serves
 * @param query
 *            the osquery SQL; {@code null} for a check whose SQL is generated
 */
// Only Jackson constructs these, so native-image would otherwise drop the
// constructor and every entry would fail to load in production alone.
@RegisterForReflection
public record LibraryEntry(
	String key,
	Type type,
	String group,
	String name,
	String description,
	String query,
	String expression)
{
	@RegisterForReflection
	public enum Type
	{
		/** An {@code ExpressionCheck}: SQL and pass condition as written. */
		EXPRESSION,

		/** The {@code AppBlacklistCheck}, whose SQL comes from Blocked Apps. */
		APP_BLACKLIST
	}

	LibraryEntry withKey(String key)
	{
		return new LibraryEntry(key, type, group, name, description, query, expression);
	}

	@JsonIgnore
	public boolean isGenerated()
	{
		return type == Type.APP_BLACKLIST;
	}
}
