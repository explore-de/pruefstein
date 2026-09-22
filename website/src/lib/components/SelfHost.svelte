<script>
	import Section from './Section.svelte';
	import CodeBlock from './CodeBlock.svelte';
	import Button from './Button.svelte';
	import { needs, compose } from '$lib/data/selfhost.js';
	import { site } from '$lib/data/site.js';
</script>

<Section
	id="self-host"
	tone="paper"
	eyebrow="Self-host"
	title="Your server, one compose file"
	lede="The web app ships as a native container image. deploy/ in the repository holds PostgreSQL and the app in one Docker Compose stack, so hosting it is an .env file and one command. No JDK, no build on the host."
>
	<div class="grid">
		<div class="needs">
			<h3>What you need first</h3>
			<ol>
				{#each needs as n, i (n.title)}
					<li>
						<span class="n">{i + 1}</span>
						<div>
							<strong>{n.title}</strong>
							<p>{n.body}</p>
						</div>
					</li>
				{/each}
			</ol>
		</div>

		<div class="run">
			<CodeBlock label="On the server" code={compose} tone="dark" />
			<p class="note">
				On first boot the app creates its schema and seeds the baseline checks.
				<code>docker compose pull &amp;&amp; docker compose up -d</code> updates it later; schema
				changes apply themselves.
			</p>
			<div class="actions">
				<Button href="{site.repo}#hosting-with-docker-compose" external variant="dark" size="sm">
					Full hosting guide ↗
				</Button>
				<Button href="{site.repo}/tree/main/deploy" external variant="plain" size="sm">
					See deploy/ ↗
				</Button>
			</div>
		</div>
	</div>
</Section>

<style>
	.grid {
		display: grid;
		gap: 2.5rem;
	}

	.needs h3 {
		font-size: clamp(1.2rem, 2.4vw, 1.5rem);
	}

	ol {
		list-style: none;
		margin: 1.25rem 0 0;
		padding: 0;
		border: var(--rule) solid var(--ink);
		background: var(--surface);
		box-shadow: var(--shadow);
	}

	li {
		display: flex;
		gap: 1rem;
		padding: 1rem 1.1rem;
		border-bottom: var(--rule) solid var(--ink);
	}

	li:last-child {
		border-bottom: 0;
	}

	.n {
		flex: none;
		display: grid;
		place-items: center;
		width: 1.8rem;
		height: 1.8rem;
		border: var(--rule) solid var(--ink);
		background: var(--accent);
		font-weight: 700;
		font-size: 0.85rem;
	}

	li strong {
		font-size: 0.95rem;
	}

	li p {
		margin-top: 0.3rem;
		font-size: 0.88rem;
		color: var(--muted);
	}

	.run {
		min-width: 0;
	}

	.note {
		margin-top: 1.1rem;
		font-size: 0.9rem;
		color: var(--muted);
	}

	.note code {
		font-family: var(--mono);
		font-size: 0.82rem;
		color: var(--ink);
		overflow-wrap: anywhere;
	}

	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: 0.75rem;
		margin-top: 1.4rem;
	}

	@media (min-width: 980px) {
		.grid {
			grid-template-columns: 0.9fr 1.1fr;
			gap: 3rem;
			align-items: start;
		}
		.run {
			margin-top: 3.1rem;
		}
	}
</style>
