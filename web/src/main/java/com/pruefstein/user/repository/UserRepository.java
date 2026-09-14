package com.pruefstein.user.repository;

import java.util.Optional;

import com.pruefstein.user.domain.AppUser;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserRepository implements PanacheRepository<AppUser>
{
	public Optional<AppUser> findBySubject(String oidcSubject)
	{
		return find("oidcSubject", oidcSubject).firstResultOptional();
	}

	/**
	 * A user who was typed into the Users screen and has not logged in since —
	 * so the row carries an address but no subject yet.
	 *
	 * <p>
	 * A row that already has a subject is deliberately not returned: it belongs
	 * to whoever holds that subject, and a second identity arriving under the
	 * same address must not be able to take it over.
	 */
	public Optional<AppUser> findAdoptableByMail(String mail)
	{
		return find("lower(mail) = ?1 and oidcSubject is null", mail.toLowerCase())
			.firstResultOptional();
	}
}
