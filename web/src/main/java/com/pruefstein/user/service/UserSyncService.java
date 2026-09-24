package com.pruefstein.user.service;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserSyncService
{
	@Inject
	UserRepository userRepository;

	/**
	 * Creates or refreshes the local record for an OIDC identity and returns
	 * it, so callers that need the user — a mail address, for instance — do not
	 * have to look it up again.
	 */
	@Transactional
	public AppUser syncUser(String subject, String email, String firstName, String lastName)
	{
		return userRepository.findBySubject(subject)
			.or(() -> adoptByMail(subject, email))
			.map(user -> refresh(user, subject, email, firstName, lastName))
			.orElseGet(() -> {
				AppUser user = new AppUser();
				user.setOidcSubject(subject);
				user.setMail(email);
				user.setFirstname(firstName != null ? firstName : firstNameFromMail(email));
				user.setLastname(lastName != null ? lastName : lastNameFromMail(email));
				userRepository.persist(user);
				return user;
			});
	}

	/**
	 * Takes over whatever the token says, and leaves alone what it does not.
	 */
	private static AppUser refresh(AppUser user, String subject, String email, String firstName, String lastName)
	{
		if (email != null)
		{
			user.setMail(email);
		}
		if (firstName != null)
		{
			user.setFirstname(firstName);
		}
		if (lastName != null)
		{
			user.setLastname(lastName);
		}
		if (subject.equals(user.getFirstname()))
		{
			// A row created before the fallback in syncUser, when a token
			// without name claims left the subject as the name.
			user.setFirstname(firstNameFromMail(email));
			if (user.getLastname() == null || user.getLastname().isEmpty())
			{
				user.setLastname(lastNameFromMail(email));
			}
		}
		return user;
	}

	/**
	 * Claims the row an admin typed in ahead of the person's first login and
	 * stamps the subject on it. Without this the hand-added user and the one
	 * who reports are two records for one human, and the Users screen shows a
	 * colleague who has been reporting for weeks as never having reported.
	 *
	 * <p>
	 * The address is the only thing the two records share — the admin typed it,
	 * and the identity provider vouches for it — so it is what they are matched
	 * on. Matching is one-shot: the subject goes on, and every later sync finds
	 * the row by subject instead.
	 */
	private Optional<AppUser> adoptByMail(String subject, String email)
	{
		if (email == null || email.isBlank())
		{
			return Optional.empty();
		}
		return userRepository.findAdoptableByMail(email)
			.map(user -> {
				user.setOidcSubject(subject);
				return user;
			});
	}

	/**
	 * Entra leaves {@code given_name} and {@code family_name} out of its tokens
	 * unless the app registration asks for them, so the name is read off an
	 * address like {@code alex.king@…} instead: "Alex" and "King". Never the
	 * subject, which is an opaque id nobody recognises on the Users screen.
	 */
	static String firstNameFromMail(String email)
	{
		String[] parts = mailNameParts(email);
		return parts.length > 0 ? parts[0] : "";
	}

	static String lastNameFromMail(String email)
	{
		String[] parts = mailNameParts(email);
		return parts.length > 1 ? String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)) : "";
	}

	private static String[] mailNameParts(String email)
	{
		if (email == null || email.isBlank())
		{
			return new String[0];
		}
		String local = email.strip().split("@", 2)[0];
		return Arrays.stream(local.split("[._]+"))
			.filter(part -> !part.isEmpty())
			.map(UserSyncService::capitalize)
			.toArray(String[]::new);
	}

	private static String capitalize(String part)
	{
		// Each hyphenated half too, so klaus-martin becomes Klaus-Martin.
		return Arrays.stream(part.split("-", -1))
			.map(half -> half.isEmpty() ? half : Character.toUpperCase(half.charAt(0)) + half.substring(1))
			.collect(Collectors.joining("-"));
	}
}
