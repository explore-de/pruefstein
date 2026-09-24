package com.pruefstein.osversion.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.pruefstein.osversion.domain.MacOsRelease;
import com.pruefstein.osversion.domain.MacOsVersion;
import com.pruefstein.osversion.repository.MacOsReleaseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What macOS releases exist, according to Apple.
 * <p>
 * The feed is copied into the database rather than read on demand: a report
 * page must not wait on Apple, must still know the newest release while the
 * feed is unreachable, and the rows accumulate into a record of what shipped
 * when. Refreshing is additive — a release Apple stops listing is kept, because
 * a device may still be running it.
 */
@ApplicationScoped
public class MacOsReleaseCatalog
{
	private static final Logger LOG = LoggerFactory.getLogger(MacOsReleaseCatalog.class);

	@Inject
	AppleReleaseFeedClient feedClient;

	@Inject
	MacOsReleaseRepository repository;

	/**
	 * Pulls the feed and stores what it holds.
	 *
	 * @return how many releases this call had not seen before
	 */
	@Transactional
	public int refresh() throws Exception
	{
		ApplePmvFeed feed = feedClient.fetch();
		Instant now = Instant.now();
		int added = 0;
		// Public first, so a version listed in both is recorded as public.
		added += store(feed.publicMacOs(), true, now);
		added += store(feed.allMacOs(), false, now);
		return added;
	}

	/**
	 * The newest macOS Apple has published, or empty while the catalogue has
	 * never been filled. Seed builds are left out — see
	 * {@link MacOsRelease#isPublicRelease()}.
	 */
	public Optional<MacOsVersion> latestPublicVersion()
	{
		return repository.find("publicRelease = true").list().stream()
			.map(MacOsRelease::version)
			.filter(java.util.Objects::nonNull)
			.max(Comparator.naturalOrder());
	}

	/** Every release the catalogue knows, newest first. */
	public List<MacOsRelease> known()
	{
		return repository.listAll().stream()
			.sorted(Comparator.comparing(MacOsRelease::version, Comparator.nullsLast(Comparator.reverseOrder())))
			.toList();
	}

	private int store(List<ApplePmvFeed.Asset> assets, boolean publicRelease, Instant now)
	{
		int added = 0;
		for (ApplePmvFeed.Asset asset : assets)
		{
			if (asset.productVersion() == null || asset.build() == null)
			{
				LOG.debug("Skipping a feed entry with no version or build");
				continue;
			}
			Optional<MacOsRelease> existing = repository
				.findByVersionAndBuild(asset.productVersion(), asset.build());
			if (existing.isPresent())
			{
				MacOsRelease release = existing.get();
				release.setSeenAt(now);
				// Never demoted: a build listed publicly once stays public.
				if (publicRelease)
				{
					release.setPublicRelease(true);
				}
			}
			else
			{
				MacOsRelease release = new MacOsRelease();
				release.setProductVersion(asset.productVersion());
				release.setBuild(asset.build());
				release.setPostingDate(asset.postingDate());
				release.setPublicRelease(publicRelease);
				release.setSeenAt(now);
				repository.persist(release);
				added++;
			}
		}
		return added;
	}
}
