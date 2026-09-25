package com.pruefstein.osversion.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.pruefstein.osversion.domain.MacOsVersion;
import com.pruefstein.osversion.repository.MacOsReleaseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MacOsReleaseCatalogTest
{
	@InjectMock
	AppleReleaseFeedClient feedClient;

	@Inject
	MacOsReleaseCatalog catalog;

	@Inject
	MacOsReleaseRepository repository;

	private static ApplePmvFeed.Asset asset(String version, String build)
	{
		return new ApplePmvFeed.Asset(version, build, LocalDate.of(2026, 9, 15));
	}

	private static ApplePmvFeed feed(List<ApplePmvFeed.Asset> publicAssets, List<ApplePmvFeed.Asset> all)
	{
		return new ApplePmvFeed(
			Map.of(ApplePmvFeed.MAC_OS, publicAssets),
			Map.of(ApplePmvFeed.MAC_OS, all));
	}

	@BeforeEach
	@AfterEach
	void clear()
	{
		QuarkusTransaction.requiringNew().run(() -> repository.deleteAll());
	}

	@Test
	void storesWhatTheFeedHolds() throws Exception
	{
		// given
		Mockito.when(feedClient.fetch()).thenReturn(feed(
			List.of(asset("27.0", "26A428"), asset("26.7", "25G229")),
			List.of(asset("26.6.2", "25G83"))));

		// when
		int added = catalog.refresh();

		// then
		assertEquals(3, added);
		QuarkusTransaction.requiringNew().run(() -> assertEquals(3, repository.count()));
	}

	@Test
	void picksTheNewestPublicReleaseAsLatest() throws Exception
	{
		// given
		Mockito.when(feedClient.fetch()).thenReturn(feed(
			List.of(asset("26.7", "25G229"), asset("27.0", "26A428"), asset("15.8", "24H23")),
			List.of()));
		catalog.refresh();

		// when / then
		assertEquals(new MacOsVersion(27, 0, 0), catalog.latestPublicVersion().orElseThrow());
	}

	@Test
	void neverLetsASeedBuildDecideWhatLatestMeans() throws Exception
	{
		// given — a build listed only in the wider asset sets, never publicly
		Mockito.when(feedClient.fetch()).thenReturn(feed(
			List.of(asset("27.0", "26A428")),
			List.of(asset("28.0", "27A1234"))));
		catalog.refresh();

		// when / then — the public release still wins
		assertEquals(new MacOsVersion(27, 0, 0), catalog.latestPublicVersion().orElseThrow());
	}

	@Test
	void refreshingTwiceAddsNothingNew() throws Exception
	{
		// given
		Mockito.when(feedClient.fetch()).thenReturn(feed(List.of(asset("27.0", "26A428")), List.of()));
		catalog.refresh();

		// when
		int added = catalog.refresh();

		// then
		assertEquals(0, added);
		QuarkusTransaction.requiringNew().run(() -> assertEquals(1, repository.count()));
	}

	@Test
	void keepsAReleaseAppleStopsListing() throws Exception
	{
		// given a release that was in the feed once
		Mockito.when(feedClient.fetch()).thenReturn(feed(List.of(asset("15.8", "24H23")), List.of()));
		catalog.refresh();

		// when a later refresh no longer mentions it
		Mockito.when(feedClient.fetch()).thenReturn(feed(List.of(asset("27.0", "26A428")), List.of()));
		catalog.refresh();

		// then it is still on record — a device may well still be running it
		QuarkusTransaction.requiringNew().run(() -> {
			assertTrue(repository.findByVersionAndBuild("15.8", "24H23").isPresent());
			assertEquals(2, repository.count());
		});
	}

	@Test
	void hasNoLatestBeforeItHasEverBeenFilled()
	{
		// given an empty catalog

		// when / then — the report page shows a version without judging it
		assertTrue(catalog.latestPublicVersion().isEmpty());
	}

	@Test
	void skipsFeedEntriesWithNothingToIdentifyThem() throws Exception
	{
		// given
		Mockito.when(feedClient.fetch()).thenReturn(feed(
			List.of(asset("27.0", "26A428"), asset(null, "26A999"), asset("27.1", null)),
			List.of()));

		// when
		int added = catalog.refresh();

		// then only the usable one lands
		assertEquals(1, added);
	}

	@Test
	void knowsTheNewestFixOfEachTrainAsOfADay() throws Exception
	{
		// given — 15.7.9 ships after 15.7.8, and a seed is never counted
		Mockito.when(feedClient.fetch()).thenReturn(feed(
			List.of(
				new ApplePmvFeed.Asset("27.0", "26A428", LocalDate.of(2026, 9, 15)),
				new ApplePmvFeed.Asset("15.7.8", "24G820", LocalDate.of(2026, 7, 1)),
				new ApplePmvFeed.Asset("15.7.9", "24G830", LocalDate.of(2026, 9, 15))),
			List.of(new ApplePmvFeed.Asset("15.8", "24H1", LocalDate.of(2026, 9, 1)))));
		catalog.refresh();

		// when
		Map<Integer, MacOsVersion> before = catalog.latestPublicPerTrain(LocalDate.of(2026, 8, 1));
		Map<Integer, MacOsVersion> after = catalog.latestPublicPerTrain(LocalDate.of(2026, 9, 20));

		// then — a fix that had not shipped yet was not missing
		assertEquals(new MacOsVersion(15, 7, 8), before.get(15));
		assertTrue(before.get(27) == null);
		assertEquals(new MacOsVersion(15, 7, 9), after.get(15));
		assertEquals(new MacOsVersion(27, 0, 0), after.get(27));
	}
}
