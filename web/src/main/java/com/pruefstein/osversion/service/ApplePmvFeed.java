package com.pruefstein.osversion.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The slice of Apple's public asset metadata feed (gdmf.apple.com/v2/pmv) that
 * this application reads.
 * <p>
 * The feed also carries iOS and visionOS, and every entry a long list of
 * supported devices. All of it is ignored — unknown properties are dropped
 * rather than failing the parse, so Apple adding a field never takes the
 * catalogue offline.
 *
 * @param publicAssetSets
 *            what is available to everyone, keyed by platform
 * @param assetSets
 *            the wider list, which reaches further back
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplePmvFeed(
	@JsonProperty("PublicAssetSets") Map<String, List<Asset>> publicAssetSets,
	@JsonProperty("AssetSets") Map<String, List<Asset>> assetSets)
{
	/** Apple's key for macOS in both maps. */
	public static final String MAC_OS = "macOS";

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Asset(
		@JsonProperty("ProductVersion") String productVersion,
		@JsonProperty("Build") String build,
		@JsonProperty("PostingDate") LocalDate postingDate)
	{
	}

	public List<Asset> publicMacOs()
	{
		return macOs(publicAssetSets);
	}

	public List<Asset> allMacOs()
	{
		return macOs(assetSets);
	}

	private static List<Asset> macOs(Map<String, List<Asset>> sets)
	{
		if (sets == null)
		{
			return List.of();
		}
		return sets.getOrDefault(MAC_OS, List.of());
	}
}
