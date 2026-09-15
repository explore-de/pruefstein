<script>
	import Section from './Section.svelte';
	import { base } from '$app/paths';
	import { shots } from '$lib/data/shots.js';
</script>

<Section
	id="screens"
	tone="paper"
	eyebrow="What you get"
	title="The admin's side of it"
	lede="Captured from a running instance against its seed data, which is why the numbers are small and the people are called alice and bob."
>
	<div class="stack">
		{#each shots as shot (shot.file)}
			<figure class="shot" class:wide={shot.wide}>
				<div class="frame">
					<picture>
						<source srcset="{base}/shots/{shot.file}.webp" type="image/webp" />
						<img
							src="{base}/shots/{shot.file}.png"
							width={shot.w}
							height={shot.h}
							alt={shot.alt}
							loading="lazy"
							decoding="async"
						/>
					</picture>
				</div>
				<figcaption>
					<p class="eyebrow tag">{shot.tag}</p>
					<h3>{shot.title}</h3>
					<p class="body">{shot.body}</p>
				</figcaption>
			</figure>
		{/each}
	</div>
</Section>

<style>
	.stack {
		display: grid;
		gap: clamp(2.75rem, 6vw, 4.5rem);
	}

	.shot {
		margin: 0;
	}

	/* The plate the app itself is built out of: 2px rule, hard offset shadow,
	   no radius. The screenshot sits inside it rather than floating. */
	.frame {
		border: var(--rule) solid var(--ink);
		box-shadow: var(--shadow);
		background: var(--surface);
		line-height: 0;
	}

	/* The wide one is a full-bleed table. Squeezed onto a phone it would be
	   a texture rather than a screenshot, so it scrolls sideways instead. */
	.wide .frame {
		overflow-x: auto;
	}

	.wide .frame img {
		min-width: 680px;
	}

	img {
		display: block;
		width: 100%;
		height: auto;
	}

	figcaption {
		margin-top: 1.15rem;
		max-width: 56ch;
	}

	.tag {
		color: var(--muted);
		margin-bottom: 0.7rem;
	}

	h3 {
		font-size: 1.2rem;
		line-height: 1.15;
	}

	.body {
		margin-top: 0.6rem;
		font-size: 0.94rem;
		line-height: 1.65;
		color: var(--muted);
	}
</style>
