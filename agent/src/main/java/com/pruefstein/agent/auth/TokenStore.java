package com.pruefstein.agent.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

@ApplicationScoped
public class TokenStore
{
	/**
	 * Also read by {@code CredentialsConfigSource} before CDI exists, which is
	 * why the path is a static both can reach.
	 * <p>
	 * A method rather than a constant on purpose: a native-image build
	 * initialises static fields at build time, and a constant froze the home
	 * directory of the CI runner that built the binary — every login on a real
	 * machine then tried to write to {@code /Users/runner}.
	 */
	public static Path credentialsFile()
	{
		return Path.of(System.getProperty("user.home"), ".config", "pruefstein", "credentials.json");
	}

	private static final Logger LOG = getLogger(TokenStore.class);

	@Inject
	ObjectMapper objectMapper;

	public Optional<Credentials> load()
	{
		Path file = credentialsFile();
		if (!Files.exists(file))
		{
			return Optional.empty();
		}
		try
		{
			return Optional.of(objectMapper.readValue(file.toFile(), Credentials.class));
		}
		catch (IOException e)
		{
			LOG.warn("Error while loading credentials", e);
			return Optional.empty();
		}
	}

	public void save(Credentials credentials)
	{
		Path file = credentialsFile();
		try
		{
			Files.createDirectories(file.getParent());
			objectMapper.writeValue(file.toFile(), credentials);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Could not save credentials to " + file, e);
		}
	}

	public void clear()
	{
		Path file = credentialsFile();
		try
		{
			Files.deleteIfExists(file);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Could not clear credentials at " + file, e);
		}
	}
}
