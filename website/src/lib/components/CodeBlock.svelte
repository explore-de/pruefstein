<script>
	/**
	 * A labelled code plate. `tone="dark"` is the terminal treatment; the
	 * default is a light plate for SQL and expressions.
	 *
	 * `wrap` keeps hand-formatted indentation but folds an over-long line
	 * instead of hiding it behind a scrollbar. Better for a short expression
	 * in a narrow column than for a wide terminal transcript.
	 */
	let {
		label = '',
		code = '',
		tone = 'light',
		frame = 'ink',
		wrap = false,
		scroll = false,
		copy = true
	} = $props();

	let copied = $state(false);
	let failed = $state(false);
	let resetTimer;

	/**
	 * The async clipboard API is the right one, but it can reject, or, when the
	 * document is not focused, simply never settle. Race it, then fall back to
	 * the old selection trick, so the button always ends up saying something
	 * true rather than sitting on "Copy" forever.
	 */
	async function writeClipboard(text) {
		if (navigator.clipboard?.writeText) {
			try {
				await Promise.race([
					navigator.clipboard.writeText(text),
					new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 700))
				]);
				return true;
			} catch {
				// fall through to the fallback below
			}
		}

		try {
			const ta = document.createElement('textarea');
			ta.value = text;
			ta.setAttribute('readonly', '');
			ta.style.cssText = 'position:fixed;top:-1000px;left:0;opacity:0';
			document.body.appendChild(ta);
			ta.select();
			const ok = document.execCommand('copy');
			ta.remove();
			return ok;
		} catch {
			return false;
		}
	}

	async function copyCode() {
		clearTimeout(resetTimer);
		const ok = await writeClipboard(code);
		copied = ok;
		failed = !ok;
		resetTimer = setTimeout(() => {
			copied = false;
			failed = false;
		}, 1600);
	}
</script>

<figure class="block {tone} frame-{frame}" class:scroll class:wrap>
	{#if label || copy}
		<figcaption class="label">
			<span class="eyebrow text">{label}</span>
			{#if copy}
				<button
					class="copy"
					class:done={copied}
					class:bad={failed}
					onclick={copyCode}
					aria-label={copied ? 'Copied' : 'Copy to clipboard'}
				>
					{copied ? 'Copied ✓' : failed ? 'Blocked' : 'Copy'}
				</button>
			{/if}
		</figcaption>
	{/if}
	<pre><code>{code}</code></pre>
</figure>

<style>
	.block {
		margin: 0;
		border: var(--rule) solid var(--ink);
		background: var(--surface);
		display: flex;
		flex-direction: column;
		min-width: 0;
	}

	.dark {
		background: var(--ink);
		color: #fafaf9;
	}

	/* On a black band a black border is no border at all */
	.frame-accent {
		border-color: var(--accent);
	}
	.frame-accent .label {
		border-bottom-color: var(--accent);
	}

	.label {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		padding: 0.4rem 0.4rem 0.4rem 0.9rem;
		min-height: 2.1rem;
		border-bottom: var(--rule) solid var(--ink);
		background: var(--accent);
		color: #422006;
		flex: none;
	}

	.text {
		color: inherit;
	}

	.dark .label {
		background: #1c1917;
		color: var(--accent);
		border-bottom-color: #44403c;
	}

	/* --- Copy ------------------------------------------------------------- */

	.copy {
		margin-left: auto;
		font-family: var(--sans);
		font-size: 9px;
		font-weight: 700;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		padding: 0.3rem 0.5rem;
		border: 2px solid transparent;
		background: transparent;
		color: inherit;
		cursor: pointer;
		white-space: nowrap;
		opacity: 0;
		transition:
			opacity 0.1s linear,
			background-color 0.1s linear,
			color 0.1s linear,
			border-color 0.1s linear;
	}

	/* Stays out of the way until wanted, but never hidden from the keyboard */
	.block:hover .copy,
	.copy:focus-visible,
	.copy.done,
	.copy.bad {
		opacity: 1;
	}

	.copy:hover,
	.copy:focus-visible {
		border-color: currentColor;
	}

	.copy:active {
		transform: translate(1px, 1px);
	}

	.copy.done {
		background: var(--ok);
		border-color: var(--ink);
		color: var(--ok-ink);
		animation: pop 0.22s ease-out 1;
	}

	.copy.bad {
		background: var(--bad);
		border-color: var(--ink);
		color: var(--bad-ink);
	}

	@keyframes pop {
		from {
			transform: scale(0.86);
		}
		to {
			transform: scale(1);
		}
	}

	@media (hover: none) {
		.copy {
			opacity: 1;
		}
	}

	pre {
		margin: 0;
		padding: 0.95rem 1rem;
		overflow-x: auto;
		font-size: 0.82rem;
		line-height: 1.65;
		tab-size: 2;
	}

	.scroll pre {
		max-height: 15rem;
		overflow-y: auto;
	}

	code {
		white-space: pre;
	}

	.wrap code {
		white-space: pre-wrap;
		overflow-wrap: anywhere;
	}

	/* Below the two-column breakpoint no plate has a column wide enough for a
	   full command line, and a box that scrolls sideways inside a page that
	   scrolls down mostly just hides its own second half. So every plate folds
	   on a phone, `wrap` or not; the Copy button still hands over the real text
	   with its line breaks intact. */
	@media (max-width: 699px) {
		pre {
			padding: 0.9rem;
		}

		code {
			white-space: pre-wrap;
			overflow-wrap: anywhere;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.copy.done {
			animation: none;
		}
	}
</style>
