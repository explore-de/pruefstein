package com.pruefstein.shared.util;

import io.quarkus.qute.RawString;
import io.quarkus.qute.TemplateExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Renders the Markdown the AI writes its fix instructions in.
 * <p>
 * That text comes from a model fed with osquery output, which is to say from
 * the device, so it is treated as untrusted: HTML in it is escaped rather than
 * passed through, and links keep only safe schemes. Single line breaks are kept
 * as breaks, because explanations stored before the switch to Markdown are
 * plain text laid out with them.
 */
public final class Markdown
{
	private static final Parser PARSER = Parser.builder().build();

	private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
		.escapeHtml(true)
		.sanitizeUrls(true)
		.softbreak("<br>\n")
		.build();

	private Markdown()
	{
	}

	public static String toHtml(String markdown)
	{
		if (markdown == null || markdown.isBlank())
		{
			return "";
		}
		return RENDERER.render(PARSER.parse(markdown));
	}

	/** {@code {text.markdown}} in a template. */
	@TemplateExtension(matchName = "markdown")
	static RawString markdown(String markdown)
	{
		return new RawString(toHtml(markdown));
	}
}
