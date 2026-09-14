package com.pruefstein.user.service;

import java.util.Optional;

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
			.map(user -> {
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
				return user;
			})
			.orElseGet(() -> {
				AppUser user = new AppUser();
				user.setOidcSubject(subject);
				user.setMail(email);
				user.setFirstname(firstName != null ? firstName : subject);
				user.setLastname(lastName != null ? lastName : "");
				userRepository.persist(user);
				return user;
			});
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
}
