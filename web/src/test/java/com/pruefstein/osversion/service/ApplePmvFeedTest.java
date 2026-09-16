package com.pruefstein.osversion.service;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsed against a real capture of gdmf.apple.com/v2/pmv, trimmed only of the
 * supported-device lists — so the shapes here are Apple's, not ours.
 */
class ApplePmvFeedTest
{
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	private static ApplePmvFeed sample() throws Exception
	{
		try (var in = ApplePmvFeedTest.class.getResourceAsStream("/apple-pmv-sample.json"))
		{
			return MAPPER.readValue(in, ApplePmvFeed.class);
		}
	}

	@Test
	void readsThePublicMacOsReleases() throws Exception
	{
		// given a capture of the live feed
		ApplePmvFeed feed = sample();

		// when
		List<String> versions = feed.publicMacOs().stream().map(ApplePmvFeed.Asset::productVersion).toList();

		// then every supported train's current release is there
		assertTrue(versions.contains("27.0"));
		assertTrue(versions.contains("26.7"));
		assertTrue(versions.contains("15.8"));
		assertTrue(versions.contains("11.7.11"));
	}

	@Test
	void readsBuildsAndPostingDates() throws Exception
	{
		// given
		ApplePmvFeed feed = sample();

		// when
		ApplePmvFeed.Asset tahoe = feed.publicMacOs().stream()
			.filter(a -> "26.7".equals(a.productVersion()))
			.findFirst()
			.orElseThrow();

		// then
		assertEquals("25G229", tahoe.build());
		assertEquals(LocalDate.of(2026, 9, 15), tahoe.postingDate());
	}

	@Test
	void reachesFurtherBackThroughTheWiderAssetSets() throws Exception
	{
		// given
		ApplePmvFeed feed = sample();

		// when
		List<String> versions = feed.allMacOs().stream().map(ApplePmvFeed.Asset::productVersion).toList();

		// then releases no longer current are still described
		assertTrue(versions.contains("26.5.1"));
		assertTrue(versions.contains("15.7.7"));
	}

	@Test
	void survivesTheFieldsItDoesNotUse() throws Exception
	{
		// given — the feed carries iOS, visionOS, expiry dates and device lists

		// when / then — none of it fails the parse
		assertFalse(sample().publicMacOs().isEmpty());
	}

	@Test
	void treatsAMissingPlatformAsNoReleases() throws Exception
	{
		// given a feed with no macOS key at all
		ApplePmvFeed feed = MAPPER.readValue("{\"PublicAssetSets\":{\"iOS\":[]}}", ApplePmvFeed.class);

		// when / then — empty, not a crash
		assertTrue(feed.publicMacOs().isEmpty());
		assertTrue(feed.allMacOs().isEmpty());
	}
}
