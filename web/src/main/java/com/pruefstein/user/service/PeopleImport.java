package com.pruefstein.user.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import com.pruefstein.user.service.PeopleExtractionAiService.ExtractedPerson;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Turns whatever file an admin has lying around — an HR export, a Markdown
 * table, a pasted list — into people to invite. The AI reads the names; this
 * class makes sure it can only ever propose addresses that are really in the
 * file, and that it does not lose any that are.
 */
@ApplicationScoped
public class PeopleImport
{
	private static final Logger LOG = Logger.getLogger(PeopleImport.class);

	static final Pattern MAIL = Pattern.compile("[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	/**
	 * Small enough that one model call answers well inside its timeout and
	 * output limit, large enough that a company of a few hundred is a handful
	 * of calls.
	 */
	static final int CHUNK_CHARS = 6_000;

	@Inject
	PeopleExtractionAiService ai;

	@Inject
	UserRepository userRepository;

	/**
	 * One person as the review screen shows them.
	 *
	 * @param existing
	 *            somebody with this address is already on record, so inviting
	 *            them again would only create a duplicate
	 */
	public record Candidate(String firstname, String lastname, String mail, boolean existing)
	{
		public String getFirstname()
		{
			return firstname;
		}

		public String getLastname()
		{
			return lastname;
		}

		public String getMail()
		{
			return mail;
		}

		public boolean isExisting()
		{
			return existing;
		}
	}

	/** Everyone found in the file, new people first. */
	public List<Candidate> read(byte[] file)
	{
		String text = decode(file);
		Map<String, ExtractedPerson> found = new LinkedHashMap<>();
		for (String chunk : chunks(text))
		{
			extract(chunk).forEach(person -> found.putIfAbsent(key(person.mail()), person));
			// Whatever the model skipped or dropped still gets a row: removing
			// a stray address on the review screen is cheaper than never
			// noticing a colleague was missed.
			addresses(chunk).stream()
				.filter(mail -> !found.containsKey(key(mail)))
				.forEach(mail -> found.put(key(mail), fromAddress(mail)));
		}
		Set<String> existing = userRepository.listAll().stream()
			.map(AppUser::getMail)
			.filter(mail -> mail != null)
			.map(PeopleImport::key)
			.collect(Collectors.toSet());
		return found.values().stream()
			.map(p -> new Candidate(p.firstname(), p.lastname(), p.mail(), existing.contains(key(p.mail()))))
			.sorted((a, b) -> Boolean.compare(a.existing(), b.existing()))
			.toList();
	}

	private List<ExtractedPerson> extract(String chunk)
	{
		Set<String> inText = addresses(chunk).stream().map(PeopleImport::key).collect(Collectors.toSet());
		try
		{
			return ai.extract(chunk).peopleOrEmpty().stream()
				.filter(p -> p != null && p.mail() != null)
				.map(p -> new ExtractedPerson(strip(p.firstname()), strip(p.lastname()), p.mail().strip()))
				// An address that is not in the file is one the model made up.
				.filter(p -> inText.contains(key(p.mail())))
				.toList();
		}
		catch (Exception e)
		{
			LOG.warnf(e, "AI extraction failed, falling back to the addresses alone");
			return List.of();
		}
	}

	/**
	 * Splits on line boundaries and repeats the first line in every later chunk
	 * when it looks like a header, so the model still knows which column is
	 * which halfway down a CSV.
	 */
	static List<String> chunks(String text)
	{
		List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
		if (lines.isEmpty())
		{
			return List.of();
		}
		String header = MAIL.matcher(lines.getFirst()).find() ? null : lines.getFirst();
		List<String> chunks = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String line : lines)
		{
			if (!current.isEmpty() && current.length() + line.length() > CHUNK_CHARS)
			{
				chunks.add(current.toString());
				current.setLength(0);
				if (header != null)
				{
					current.append(header).append('\n');
				}
			}
			current.append(line).append('\n');
		}
		chunks.add(current.toString());
		return chunks;
	}

	/** Whether the whole value is one email address, as the invite needs it. */
	public static boolean isAddress(String value)
	{
		return value != null && MAIL.matcher(value).matches();
	}

	static List<String> addresses(String text)
	{
		List<String> mails = new ArrayList<>();
		Matcher matcher = MAIL.matcher(text);
		while (matcher.find())
		{
			mails.add(matcher.group());
		}
		return mails;
	}

	/** jane.doe@example.com → Jane Doe; anything less obvious stays blank. */
	static ExtractedPerson fromAddress(String mail)
	{
		String[] parts = mail.substring(0, mail.indexOf('@')).split("[._-]");
		if (parts.length == 2 && parts[0].length() > 1 && parts[1].length() > 1)
		{
			return new ExtractedPerson(capitalize(parts[0]), capitalize(parts[1]), mail);
		}
		return new ExtractedPerson("", "", mail);
	}

	/**
	 * UTF-8 when the bytes are valid UTF-8, otherwise Windows-1252 — which is
	 * what Excel writes when it saves a CSV on most European machines.
	 */
	static String decode(byte[] file)
	{
		try
		{
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(file))
				.toString()
				.replace("﻿", "");
		}
		catch (CharacterCodingException e)
		{
			return new String(file, Charset.forName("windows-1252"));
		}
	}

	private static String key(String mail)
	{
		return mail.strip().toLowerCase(Locale.ROOT);
	}

	private static String strip(String value)
	{
		return value == null ? "" : value.strip();
	}

	private static String capitalize(String value)
	{
		return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
	}
}
