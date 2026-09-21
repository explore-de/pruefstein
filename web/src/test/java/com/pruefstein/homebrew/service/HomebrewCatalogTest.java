package com.pruefstein.homebrew.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The entries below keep the shapes of formulae.brew.sh/api/formula.json and
 * cask.json — nested objects, and a cask's {@code name} being a list — so the
 * parse is tested against what Homebrew sends, not a simplification of it.
 */
class HomebrewCatalogTest
{
	private static final String FORMULAE = """
		[{"name":"abseil","full_name":"abseil","tap":"homebrew/core","homepage":"https://abseil.io",
		  "versions":{"stable":"20250814.1","bottle":true},"dependencies":["cmake"]},
		 {"name":"sneaky","homepage":"javascript:alert(1)"}]
		""";

	private static final String CASKS = """
		[{"token":"firefox","full_token":"firefox","name":["Mozilla Firefox"],
		  "homepage":"https://www.mozilla.org/firefox/","artifacts":[{"app":["Firefox.app"]}]}]
		""";

	private static HomebrewCatalog catalog()
	{
		HomebrewCatalog catalog = new HomebrewCatalog();
		catalog.objectMapper = new ObjectMapper();
		return catalog;
	}

	private static InputStream json(String text)
	{
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void linksToTheProjectHomepage() throws Exception
	{
		// given
		HomebrewCatalog catalog = catalog();
		catalog.load(json(FORMULAE), json(CASKS));

		// when / then
		assertEquals(Optional.of("https://abseil.io"), catalog.linkFor("brew:formula", "abseil"));
		assertEquals(Optional.of("https://www.mozilla.org/firefox/"), catalog.linkFor("brew:cask", "firefox"));
	}

	@Test
	void fallsBackToFormulaeBrewShUntilTheCatalogHasLoaded()
	{
		// given nothing loaded yet
		HomebrewCatalog catalog = catalog();

		// when / then
		assertEquals(Optional.of("https://formulae.brew.sh/formula/abseil"), catalog.linkFor("brew:formula", "abseil"));
		assertEquals(Optional.of("https://formulae.brew.sh/cask/firefox"), catalog.linkFor("brew:cask", "firefox"));
	}

	@Test
	void linksNothingHomebrewDoesNotVouchFor() throws Exception
	{
		// given
		HomebrewCatalog catalog = catalog();
		catalog.load(json(FORMULAE), json(CASKS));

		// when / then — a third-party tap's package, a non-web homepage, a
		// formula name asked for as a cask, and an app that is not from
		// Homebrew
		assertEquals(Optional.empty(), catalog.linkFor("brew:formula", "pruefstein-agent"));
		assertEquals(Optional.empty(), catalog.linkFor("brew:formula", "sneaky"));
		assertEquals(Optional.empty(), catalog.linkFor("brew:cask", "abseil"));
		assertEquals(Optional.empty(), catalog.linkFor("app", "Firefox.app"));
	}
}
