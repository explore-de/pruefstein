package com.pruefstein.agent.auth;

import java.io.IOException;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AuthResolver
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthResolver.class);

	@Inject
	TokenStore tokenStore;

	@Inject
	DeviceAuthService deviceAuthService;

	@Inject
	ServerConfigClient serverConfigClient;

	@Inject
	TokenHolder tokenHolder;

	/**
	 * Only a fallback for a first login with no {@code --server}: once
	 * credentials exist, the stored server URL feeds this same property through
	 * {@code CredentialsConfigSource}.
	 */
	@ConfigProperty(name = "quarkus.rest-client.pruefstein-api.url")
	String configuredServerUrl;

	/**
	 * Ensures a valid access token is loaded into TokenHolder, using the cached
	 * one, refreshing it, or falling back to an interactive device login.
	 */
	public void ensureAuthenticated() throws IOException, InterruptedException
	{
		ensureAuthenticated(null);
	}

	/**
	 * @param serverOverride the server named on the command line, or
	 *        {@code null} to stay with the one already logged in to. Naming a
	 *        server discards any cached token: it was minted by whichever IdP
	 *        the previous server uses and means nothing to this one.
	 */
	public void ensureAuthenticated(String serverOverride) throws IOException, InterruptedException
	{
		Optional<Credentials> stored = tokenStore.load();

		if (serverOverride == null && stored.isPresent())
		{
			Credentials credentials = stored.get();
			if (!credentials.isExpired())
			{
				tokenHolder.setAccessToken(credentials.accessToken());
				return;
			}

			if (tryRefresh(credentials))
			{
				return;
			}
		}

		login(resolveServerUrl(serverOverride, stored));
	}

	/**
	 * Authenticates again after the server rejected the token we had.
	 * <p>
	 * {@link Credentials#isExpired()} is a stopwatch, and a stopwatch cannot
	 * see the things that actually invalidate a token: an identity provider
	 * that restarted or re-imported its realm signs with new keys, a revoked
	 * session is gone, a rotated client no longer matches. All of those leave a
	 * token that looks fine locally and comes back 401.
	 * <p>
	 * The refresh token is tried first, since a rejected access token is often
	 * only stale, and an interactive login is worth avoiding when a refresh
	 * would do. A refresh that fails, or that returns another token the server
	 * will not take, falls through to the device flow.
	 */
	public void reauthenticate() throws IOException, InterruptedException
	{
		Optional<Credentials> stored = tokenStore.load();
		if (stored.isPresent() && tryRefresh(stored.get()))
		{
			return;
		}
		login(resolveServerUrl(null, stored));
	}

	private boolean tryRefresh(Credentials credentials)
	{
		if (credentials.refreshToken() == null)
		{
			return false;
		}
		try
		{
			Credentials refreshed = deviceAuthService.refresh(credentials);
			tokenStore.save(refreshed);
			tokenHolder.setAccessToken(refreshed.accessToken());
			return true;
		}
		catch (Exception e)
		{
			LOG.debug("Token refresh failed, re-authenticating. ", e);
			return false;
		}
	}

	private void login(String serverUrl) throws IOException, InterruptedException
	{
		AgentServerConfig config = serverConfigClient.fetch(serverUrl);
		Credentials fresh = deviceAuthService.login(serverUrl, config);
		tokenStore.save(fresh);
		tokenHolder.setAccessToken(fresh.accessToken());
	}

	/**
	 * Normalised here rather than at the option: this is the one place every
	 * server URL passes through on its way into the credentials file, whether
	 * it came from {@code --server}, from an earlier login or from
	 * configuration.
	 */
	private String resolveServerUrl(String serverOverride, Optional<Credentials> stored)
	{
		if (serverOverride != null && !serverOverride.isBlank())
		{
			return ServerUrl.normalize(serverOverride);
		}
		return ServerUrl.normalize(stored.map(Credentials::serverUrl)
			.filter(url -> url != null && !url.isBlank())
			.orElse(configuredServerUrl));
	}
}
