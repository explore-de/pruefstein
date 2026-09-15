<script>
	import Hero from '$lib/components/Hero.svelte';
	import HowItWorks from '$lib/components/HowItWorks.svelte';
	import Screenshots from '$lib/components/Screenshots.svelte';
	import Trust from '$lib/components/Trust.svelte';
	import CheckAnatomy from '$lib/components/CheckAnatomy.svelte';
	import Features from '$lib/components/Features.svelte';
	import Catalog from '$lib/components/Catalog.svelte';
	import AiAssist from '$lib/components/AiAssist.svelte';
	import OpenSource from '$lib/components/OpenSource.svelte';
	import Contribute from '$lib/components/Contribute.svelte';
	import { site } from '$lib/data/site.js';

	const title = `${site.name} · ${site.tagline}`;

	const jsonLd = {
		'@context': 'https://schema.org',
		'@type': 'SoftwareApplication',
		name: site.name,
		applicationCategory: 'SecurityApplication',
		operatingSystem: 'macOS',
		description: site.description,
		url: site.url,
		codeRepository: site.repo,
		license: 'https://www.apache.org/licenses/LICENSE-2.0',
		isAccessibleForFree: true,
		offers: { '@type': 'Offer', price: '0', priceCurrency: 'EUR' }
	};
</script>

<svelte:head>
	<title>{title}</title>
	<meta name="description" content={site.description} />
	<link rel="canonical" href={site.url + '/'} />

	<meta property="og:type" content="website" />
	<meta property="og:site_name" content={site.name} />
	<meta property="og:title" content={title} />
	<meta property="og:description" content={site.description} />
	<meta property="og:url" content={site.url + '/'} />
	<meta property="og:image" content={site.url + '/og.png'} />
	<meta property="og:image:width" content="1200" />
	<meta property="og:image:height" content="630" />

	<meta name="twitter:card" content="summary_large_image" />
	<meta name="twitter:title" content={title} />
	<meta name="twitter:description" content={site.description} />
	<meta name="twitter:image" content={site.url + '/og.png'} />

	{@html `<script type="application/ld+json">${JSON.stringify(jsonLd)}<\/script>`}
</svelte:head>

<Hero />
<HowItWorks />
<Screenshots />
<Trust />
<CheckAnatomy />
<Features />
<Catalog />
<AiAssist />
<OpenSource />
<Contribute />
