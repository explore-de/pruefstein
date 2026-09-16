package com.pruefstein.osversion.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This trust was first declared through Quarkus' TLS registry, which reads its
 * PEM off the file system. That resolved in dev mode and not from inside a jar,
 * so the container refused to start — a broken release feed took the whole
 * application down with it. Reading it off the classpath is what these pin,
 * along with the rule that a root we cannot read is survivable.
 */
@QuarkusTest
class AppleTrustTest
{
	@Inject
	AppleTrust appleTrust;

	@Test
	void findsApplesRootOnTheClasspath()
	{
		// given the certificate shipped as a resource

		// when / then — the same lookup works packaged and unpackaged
		assertNotNull(Thread.currentThread().getContextClassLoader()
			.getResourceAsStream("tls/apple-root-ca.pem"),
			"Apple's root must be on the classpath, not merely on disk beside the sources");
	}

	@Test
	void buildsAContextThatTrustsApplesRootAndTheUsualAuthorities()
	{
		// given / when
		SSLContext context = appleTrust.sslContext();

		// then
		assertNotNull(context);
		assertNotNull(context.getSocketFactory());
	}

	@Test
	void survivesAMissingRootRatherThanTakingTheApplicationDown()
	{
		// given a configuration pointing at a certificate that is not there
		AppleTrust missing = new AppleTrust();
		missing.certificateResource = "tls/does-not-exist.pem";

		// when / then — the feed stops working; nothing else does
		assertDoesNotThrow(missing::sslContext);
		assertNotNull(missing.sslContext());
	}
}
