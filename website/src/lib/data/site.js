/**
 * Everything the site says about itself in one place, so copy changes never
 * require touching a component.
 */

export const site = {
	name: 'Prüfstein',

	// Deployed to GitHub Pages under /<repo> for now. Moving to the apex domain
	// later is three edits: these two lines, dropping BASE_PATH from
	// .github/workflows/website.yml, and putting static/CNAME back.
	domain: 'explore-de.github.io/pruefstein',
	url: 'https://explore-de.github.io/pruefstein',
	tagline: 'ISO 27001 device compliance without spying on your team.',
	description:
		'Prüfstein checks every employee Mac against your ISO 27001 controls. Nothing runs in the ' +
		'background, nothing is filed until the person says so, and the fleet data never leaves you. ' +
		'macOS today. Optional AI writes the checks and explains the failures, using a model you ' +
		'choose. 100% open source, self-hosted, no paid tier. By EXP Software GmbH.',
	// osquery first, since a formula cannot depend on a cask. The agent line is
	// fully qualified, so it taps on the reader's behalf without a `brew tap`.
	install: 'brew install --cask osquery\nbrew install explore-de/pruefstein/pruefstein-agent',
	tap: 'https://github.com/explore-de/homebrew-pruefstein',
	repo: 'https://github.com/explore-de/pruefstein',
	issues: 'https://github.com/explore-de/pruefstein/issues',
	discussions: 'https://github.com/explore-de/pruefstein/discussions',
	newIssue: 'https://github.com/explore-de/pruefstein/issues/new',
	license: 'Apache-2.0',
	licenseUrl: 'https://github.com/explore-de/pruefstein/blob/main/LICENSE'
};

/**
 * EXP Software GmbH, the company behind Prüfstein and the Diensteanbieter
 * for this site. Figures below are the §5 TMG mandatory particulars as
 * published at https://explore.de/impressum.
 */
export const company = {
	name: 'EXP Software GmbH',
	short: 'EXP',
	street: 'Ludwig-Hirschberger-Allee 11',
	postcode: '85276',
	city: 'Pfaffenhofen an der Ilm',
	country: 'Deutschland',
	phone: '+49 8441 47247-0',
	phoneHref: '+4984414724770',
	fax: '+49 8441 47247-99',
	email: 'kontakt@explore.de',
	site: 'https://explore.de',
	siteLabel: 'explore.de',
	managingDirector: 'Marko Hirsch',
	court: 'Ingolstadt',
	registerNumber: 'HRB 11396',
	vatId: 'DE 363 269 547',
	impressumUrl: 'https://explore.de/impressum',
	privacyUrl: 'https://explore.de/datenschutz'
};

/** Hrefs are relative to the deployment root; components prefix `base`. */
export const nav = [
	{ href: '/#how', label: 'How it works' },
	{ href: '/#screens', label: 'Screenshots' },
	{ href: '/#trust', label: 'Trust' },
	{ href: '/#anatomy', label: 'Anatomy of a check' },
	{ href: '/#features', label: 'Features' },
	{ href: '/#ai', label: 'AI' },
	{ href: '/#open-source', label: 'Open source' },
	{ href: '/#contribute', label: 'Contribute' }
];
