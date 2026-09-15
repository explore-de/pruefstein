<script>
	import Section from './Section.svelte';
	import CodeBlock from './CodeBlock.svelte';
	import Button from './Button.svelte';
	import { promises } from '$lib/data/contribute.js';
	import { site } from '$lib/data/site.js';

	const quickstart = `git clone ${site.repo}.git
cd pruefstein

# terminal 1: Dev Services bring up Postgres and Keycloak
cd web && ./mvnw quarkus:dev

# terminal 2: the agent, against the server you just started
${site.install}
pruefstein-agent login --server http://localhost:8080
pruefstein-agent run`;
</script>

<Section
	id="open-source"
	tone="dark"
	eyebrow="100% open source"
	title="All of it. Not a core, not a community edition."
	lede="Prüfstein is one repository under Apache-2.0. EXP Software GmbH builds it and publishes all of it. There is no second codebase holding the good features back for an edition somebody pays for."
>
	<div class="promises">
		{#each promises as p (p.title)}
			<div class="promise">
				<h3>{p.title}</h3>
				<p>{p.body}</p>
			</div>
		{/each}
	</div>

	<div class="start">
		<div class="start-copy">
			<h3>Run the whole thing in two terminals</h3>
			<p>
				Dev Services start Postgres and a seeded Keycloak realm for you, so there is no
				infrastructure to arrange before you can see a dashboard. You need JDK&nbsp;25, Docker or
				Podman, and osquery on your PATH.
			</p>
			<div class="start-actions">
				<Button href={site.repo} external variant="accent" size="sm">Clone the repo ↗</Button>
				<Button href={site.licenseUrl} external variant="plain" size="sm">Read the licence ↗</Button>
			</div>
		</div>
		<CodeBlock label="Quickstart" code={quickstart} tone="dark" frame="accent" />
	</div>
</Section>

<style>
	.promises {
		display: grid;
		gap: 0;
		border: var(--rule) solid var(--accent);
	}

	.promise {
		padding: 1.5rem;
		border-bottom: var(--rule) solid var(--accent);
	}

	.promise:last-child {
		border-bottom: 0;
	}

	.promise h3 {
		font-size: 1.05rem;
		color: var(--accent);
	}

	.promise p {
		margin-top: 0.55rem;
		font-size: 0.9rem;
		color: #d6d3d1;
	}

	.start {
		display: grid;
		gap: 2rem;
		margin-top: 3rem;
		padding-top: 3rem;
		border-top: var(--rule) solid #44403c;
		align-items: center;
	}

	.start-copy h3 {
		font-size: clamp(1.3rem, 2.5vw, 1.75rem);
	}

	.start-copy p {
		margin-top: 0.9rem;
		color: #d6d3d1;
		font-size: 0.95rem;
		max-width: 46ch;
	}

	.start-actions {
		display: flex;
		flex-wrap: wrap;
		gap: 0.75rem;
		margin-top: 1.5rem;
	}

	@media (min-width: 760px) {
		.promises {
			grid-template-columns: repeat(2, 1fr);
		}
		.promise:nth-child(odd) {
			border-right: var(--rule) solid var(--accent);
		}
		.promise:nth-last-child(2) {
			border-bottom: 0;
		}
	}

	@media (min-width: 980px) {
		.start {
			grid-template-columns: 0.85fr 1.15fr;
			gap: 3rem;
		}
	}
</style>
