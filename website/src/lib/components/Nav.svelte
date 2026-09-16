<script>
	import LogoMark from './LogoMark.svelte';
	import Button from './Button.svelte';
	import { base } from '$app/paths';
	import { nav, site } from '$lib/data/site.js';
	import { scrollSpy } from '$lib/actions/reveal.js';

	let open = $state(false);
</script>

<header class="bar">
	<div class="shell row">
		<a class="brand" href="{base}/#top">
			<LogoMark size={40} />
			<span class="word">{site.name}</span>
		</a>

		<nav class="links" aria-label="Sections" use:scrollSpy>
			{#each nav as item (item.href)}
				<a href="{base}{item.href}">{item.label}</a>
			{/each}
		</nav>

		<div class="cta">
			<Button href={site.repo} external size="sm" variant="dark">GitHub ↗</Button>
		</div>

		<button
			class="toggle"
			aria-expanded={open}
			aria-controls="mobile-nav"
			onclick={() => (open = !open)}
		>
			{open ? 'Close' : 'Menu'}
		</button>
	</div>

	{#if open}
		<nav id="mobile-nav" class="sheet" aria-label="Sections">
			{#each nav as item (item.href)}
				<a href="{base}{item.href}" onclick={() => (open = false)}>{item.label}</a>
			{/each}
			<a href={site.repo} target="_blank" rel="noreferrer noopener">GitHub ↗</a>
		</nav>
	{/if}
</header>

<style>
	.bar {
		position: sticky;
		top: 0;
		z-index: 50;
		background: var(--surface);
		border-bottom: var(--rule) solid var(--ink);
	}

	.row {
		display: flex;
		align-items: center;
		gap: 1.25rem;
		height: 64px;
	}

	.brand {
		display: inline-flex;
		align-items: center;
		gap: 0.7rem;
		text-decoration: none;
		flex: none;
	}

	.word {
		font-weight: 700;
		font-size: 1.2rem;
		text-transform: uppercase;
		letter-spacing: 0.055em;
	}

	.links {
		display: none;
		margin-left: auto;
		gap: 0.25rem;
	}

	.links a {
		padding: 0.45rem 0.7rem;
		font-size: 0.85rem;
		font-weight: 500;
		text-decoration: none;
		border: var(--rule) solid transparent;
	}

	.links a:hover {
		background: var(--accent);
		border-color: var(--ink);
		font-weight: 700;
	}

	/* Mirrors the application sidebar's nav-link-active */
	.links :global(a.current) {
		background: var(--accent);
		border-color: var(--ink);
		font-weight: 700;
	}

	.brand :global(svg) {
		transition: transform 0.35s cubic-bezier(0.2, 0.8, 0.3, 1);
	}

	.brand:hover :global(svg) {
		transform: rotate(-6deg) scale(1.1);
	}

	.word {
		transition: letter-spacing 0.18s ease-out;
	}

	.brand:hover .word {
		letter-spacing: 0.085em;
	}

	@media (prefers-reduced-motion: reduce) {
		.brand :global(svg),
		.brand:hover :global(svg),
		.word {
			transition: none;
			transform: none;
		}
	}

	.cta {
		display: none;
		flex: none;
	}

	.toggle {
		margin-left: auto;
		font-family: inherit;
		font-weight: 700;
		font-size: 0.75rem;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		padding: 0.5rem 0.85rem;
		background: var(--accent);
		border: var(--rule) solid var(--ink);
		cursor: pointer;
	}

	.sheet {
		display: flex;
		flex-direction: column;
		border-top: var(--rule) solid var(--ink);
		background: var(--surface);
		/* Nine links plus the bar is taller than a phone held sideways, and the
		   bar is sticky — without a ceiling of its own the last few links would
		   sit below the fold with nothing to scroll them into view. */
		max-height: calc(100vh - 64px);
		max-height: calc(100dvh - 64px);
		overflow-y: auto;
		overscroll-behavior: contain;
	}

	.sheet a {
		padding: 0.9rem var(--gutter);
		font-weight: 500;
		text-decoration: none;
		border-bottom: 1px solid #e7e5e4;
	}

	.sheet a:last-child {
		border-bottom: 0;
	}

	@media (min-width: 900px) {
		.links,
		.cta {
			display: flex;
		}
		.toggle,
		.sheet {
			display: none;
		}
	}
</style>
