package com.pruefstein.osversion.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The trust anchors for Apple's release feed: the JDK's own, plus Apple's root.
 * <p>
 * {@code gdmf.apple.com} is served from Apple's own root rather than a
 * commercial CA, and no JDK ships that root — macOS trusts it through the
 * system keychain, which is why {@code curl} succeeds on a Mac while the JVM
 * beside it cannot complete the handshake.
 * <p>
 * Built here rather than declared through Quarkus' TLS registry, which reads
 * its PEM from the file system: that resolves in dev mode and not from inside a
 * jar, so the container failed to start at all. Read from the classpath, it
 * works the same in both.
 * <p>
 * The JDK's anchors are kept alongside Apple's so that a deployment pointed at
 * some other feed still verifies normally, and a root that cannot be read is
 * logged and skipped rather than thrown — the newest macOS release is not worth
 * refusing to boot over.
 */
@ApplicationScoped
public class AppleTrust
{
	private static final Logger LOG = LoggerFactory.getLogger(AppleTrust.class);

	@ConfigProperty(name = "pruefstein.macos.root-certificate", defaultValue = "tls/apple-root-ca.pem")
	String certificateResource;

	/**
	 * @return a context trusting the usual authorities and Apple's root, or the
	 *         platform default if the root cannot be read
	 */
	public SSLContext sslContext()
	{
		try
		{
			X509Certificate appleRoot = readRoot();
			if (appleRoot == null)
			{
				return SSLContext.getDefault();
			}
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { trustManagerWith(appleRoot) }, null);
			return context;
		}
		catch (Exception e)
		{
			LOG.warn("Could not build the trust for Apple's feed; falling back to the default", e);
			try
			{
				return SSLContext.getDefault();
			}
			catch (Exception fallbackFailed)
			{
				throw new IllegalStateException(fallbackFailed);
			}
		}
	}

	private X509Certificate readRoot() throws IOException, CertificateException
	{
		try (InputStream pem = Thread.currentThread().getContextClassLoader()
			.getResourceAsStream(certificateResource))
		{
			if (pem == null)
			{
				LOG.warn("Apple's root certificate is not on the classpath at {} — the release feed "
					+ "will not be reachable", certificateResource);
				return null;
			}
			return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(pem);
		}
	}

	/**
	 * One trust store holding the platform's anchors and Apple's root, so both
	 * are offered to the handshake rather than one replacing the other.
	 */
	private X509TrustManager trustManagerWith(X509Certificate appleRoot)
		throws GeneralSecurityException, IOException
	{
		List<X509Certificate> anchors = new ArrayList<>(platformAnchors());
		anchors.add(appleRoot);

		KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
		store.load(null, null);
		for (int i = 0; i < anchors.size(); i++)
		{
			store.setCertificateEntry("anchor-" + i, anchors.get(i));
		}

		TrustManagerFactory factory = TrustManagerFactory
			.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init(store);
		for (TrustManager manager : factory.getTrustManagers())
		{
			if (manager instanceof X509TrustManager x509)
			{
				return x509;
			}
		}
		throw new IllegalStateException("No X509TrustManager among the defaults");
	}

	private List<X509Certificate> platformAnchors() throws GeneralSecurityException
	{
		TrustManagerFactory factory = TrustManagerFactory
			.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init((KeyStore)null);
		for (TrustManager manager : factory.getTrustManagers())
		{
			if (manager instanceof X509TrustManager x509)
			{
				return List.of(x509.getAcceptedIssuers());
			}
		}
		return List.of();
	}
}
