package com.pruefstein.shared.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownTest
{
	@Test
	void rendersTheShapesTheFixInstructionsUse()
	{
		// given
		String markdown = """
			**Firefox** is not permitted.

			1. Open **Finder** and go to `/Applications`.
			2. Run `brew uninstall --cask firefox`.
			""";

		// when
		String html = Markdown.toHtml(markdown);

		// then
		assertTrue(html.contains("<strong>Firefox</strong>"), html);
		assertTrue(html.contains("<ol>"), html);
		assertTrue(html.contains("<li>Open <strong>Finder</strong> and go to <code>/Applications</code>.</li>"), html);
		assertTrue(html.contains("<code>brew uninstall --cask firefox</code>"), html);
	}

	@Test
	void escapesHtml()
	{
		// given text the model may have copied from device output
		String html = Markdown.toHtml("<script>alert(1)</script>");

		// then
		assertFalse(html.contains("<script>"), html);
		assertTrue(html.contains("&lt;script&gt;"), html);
	}

	@Test
	void dropsUnsafeLinkTargets()
	{
		// when
		String html = Markdown.toHtml("[click](javascript:void0)");

		// then — the link text stays, its target does not
		assertFalse(html.contains("javascript"), html);
		assertTrue(html.contains("href=\"\""), html);
	}

	@Test
	void keepsTheLineBreaksOfExplanationsStoredAsPlainText()
	{
		assertEquals("<p>First line.<br>\nSecond line.</p>\n", Markdown.toHtml("First line.\nSecond line."));
	}

	@Test
	void rendersNothingForNoText()
	{
		assertEquals("", Markdown.toHtml(null));
		assertEquals("", Markdown.toHtml("  "));
	}
}
