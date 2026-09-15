<script>
	import { onMount } from 'svelte';
	import Button from './Button.svelte';
	import CodeBlock from './CodeBlock.svelte';
	import Terminal from './Terminal.svelte';
	import Stone from './Stone.svelte';
	import { site } from '$lib/data/site.js';

	/**
	 * How far out the demo's deadline sits. A date written into the source went
	 * stale the day it passed, and the site is not rebuilt often enough for the
	 * build date on its own to keep it honest — so it is counted from whenever
	 * the page is actually read.
	 *
	 * Prerendering bakes in the build day's answer, which is the best a visitor
	 * without JavaScript can be given; the client corrects it on mount.
	 */
	const REMEDIATION_DAYS = 14;

	/**
	 * `d MMM yyyy`, to match the agent's own formatter. Spelled out rather than
	 * left to Intl, whose en-GB shortens September to "Sept" — a difference
	 * nobody would notice until the one month it shows up.
	 */
	const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

	function deadline() {
		const d = new Date();
		d.setDate(d.getDate() + REMEDIATION_DAYS);
		return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
	}

	let due = $state(deadline());

	onMount(() => {
		due = deadline();
	});

	const run = $derived([
		{ k: 'cmd', t: 'pruefstein-agent run' },
		{ k: 'out', t: 'Running compliance checks on device 4C4C4544 (user: markus-mbp)' },
		{ k: 'pass', t: 'FileVault enabled' },
		{ k: 'pass', t: 'Screen lock requires a password' },
		{ k: 'fail', t: 'Firewall enabled' },
		{ k: 'pass', t: 'Gatekeeper enabled' },
		{ k: 'pass', t: 'System Integrity Protection enabled' },
		{ k: 'fail', t: 'Remote login (SSH) disabled' },
		{ k: 'pass', t: 'No blacklisted applications installed' },
		{ k: 'rule' },
		{ k: 'sum', t: 'Done: 17/19 checks passed' },
		{ k: 'notice', t: `Reporting as non-compliant on ${due} unless fixed.` },
		{ k: 'ask', t: 'Report this run? [y/N] ' }
	]);
</script>

<section class="hero" id="top">
	<Stone n={3} side="right" width="30vw" bottom="-6%" opacity={0.85} eager />

	<div class="shell grid">
		<div class="copy">
			<p class="eyebrow kicker">ISO 27001 · macOS · 100% open source</p>

			<h1>
				<span class="line">Compliant laptops.</span>
				<span class="line">Without spying</span>
				<span class="line"><span class="mark">on your team.</span></span>
			</h1>

			<p class="lede">
				Prüfstein checks every employee Mac against your ISO 27001 controls. Nothing runs in the
				background, nothing is filed until the person says so, and the fleet data never leaves you.
			</p>

			<div class="actions">
				<Button href="#how" variant="accent">See how it works</Button>
				<Button href={site.repo} external variant="dark">Get it on GitHub ↗</Button>
			</div>

			<!-- The one line somebody has to copy to try it. Fully qualified, so
			     Homebrew taps on their behalf and this really is one command. -->
			<div class="install">
				<CodeBlock label="Install the agent" code={site.install} tone="dark" />
			</div>

			<dl class="facts">
				<div><dt>Devices</dt><dd>macOS today</dd></div>
				<div><dt>Licence</dt><dd>Apache-2.0</dd></div>
				<div><dt>Runs on</dt><dd>Your infrastructure</dd></div>
			</dl>
		</div>

		<div class="demo">
			<Terminal lines={run} />
			<p class="caption">
				A real run. Checking costs nothing and can be repeated all day. Nothing is on record until
				you answer that last question.
			</p>
		</div>
	</div>
</section>

<style>
	.hero {
		position: relative;
		overflow: hidden;
		background: var(--paper);
		/* The faintest grid, so the plates have something to sit on */
		background-image:
			linear-gradient(to right, rgba(0, 0, 0, 0.045) 1px, transparent 1px),
			linear-gradient(to bottom, rgba(0, 0, 0, 0.045) 1px, transparent 1px);
		background-size: 56px 56px;
	}

	.grid {
		position: relative;
		z-index: 1;
		display: grid;
		gap: clamp(2.5rem, 5vw, 4rem);
		padding-block: clamp(3.25rem, 8vw, 6rem);
		align-items: center;
	}

	.kicker {
		color: var(--muted);
		margin-bottom: 1.4rem;
	}

	h1 {
		font-size: clamp(2.1rem, 5vw, 3.5rem);
		line-height: 1.06;
		letter-spacing: -0.035em;
	}

	.line {
		display: block;
	}

	/* Highlight hugs the words, not the line box */
	.mark {
		background: var(--accent);
		box-shadow:
			0.22em 0 0 var(--accent),
			-0.12em 0 0 var(--accent);
		-webkit-box-decoration-break: clone;
		box-decoration-break: clone;
	}

	.lede {
		margin-top: 1.6rem;
		font-size: clamp(1.02rem, 1.7vw, 1.2rem);
		color: var(--muted);
		max-width: 46ch;
	}

	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: 0.9rem;
		margin-top: 2.1rem;
	}

	.install {
		margin-top: 1.75rem;
		max-width: 46ch;
	}

	.facts {
		display: flex;
		flex-wrap: wrap;
		gap: 0 2.5rem;
		margin: 2.6rem 0 0;
		padding-top: 1.4rem;
		border-top: var(--rule) solid var(--ink);
	}

	.facts dt {
		font-size: 10px;
		font-weight: 700;
		letter-spacing: 0.15em;
		text-transform: uppercase;
		color: var(--faint);
	}

	.facts dd {
		margin: 0.15rem 0 0;
		font-weight: 700;
		font-size: 0.95rem;
	}

	.demo {
		min-width: 0;
	}

	.caption {
		margin-top: 1.1rem;
		font-size: 0.85rem;
		color: var(--muted);
		max-width: 46ch;
	}

	@media (min-width: 980px) {
		.grid {
			grid-template-columns: 1.1fr 1fr;
			gap: 4rem;
		}
	}
</style>
