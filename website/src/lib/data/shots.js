/**
 * Screenshots of the running app, not mockups: captured from a dev instance
 * against its seed data, which is why the numbers are small and the names are
 * alice and bob. Everything here is 2× so it stays sharp on a retina screen.
 * `w`/`h` are the intrinsic pixel sizes, so the browser can reserve the space
 * before the image arrives.
 */
export const shots = [
	{
		file: 'reports-admin',
		w: 3424,
		h: 834,
		wide: true,
		tag: 'Reports · admin',
		title: 'Every device, newest run first',
		body: 'An admin sees the whole estate; everyone else sees only their own machines. Filter by status, sort by when a device last checked in, and read the repair deadline of anything still open.',
		alt: 'The Prüfstein reports list as an admin sees it: three devices with compliant and non-compliant status chips, status filters above them, and a deadline column.'
	},
	{
		file: 'report-detail',
		w: 2400,
		h: 1706,
		tag: 'One report',
		title: 'Why it failed, not just that it failed',
		body: 'Each check opens onto the JSON osquery actually returned and the expression that had to hold against it. Nothing is hidden behind a score: the evidence for a verdict is the verdict.',
		alt: 'A non-compliant Prüfstein report with four check results, one expanded to show the raw osquery output and the expression it was evaluated against.'
	},
	{
		file: 'report-explain',
		w: 2540,
		h: 1348,
		tag: 'How to fix · AI',
		title: 'And what to do about it',
		body: 'Where a check failed, the model that reads the output writes the way out of it — in the terms of this machine, not a generic knowledge-base article. It runs on a model you choose, and the app works with it switched off.',
		alt: 'A Prüfstein report with the How to Fix panel open, showing an AI-written explanation of why the automatic-updates check failed and the steps to resolve it.'
	},
	{
		file: 'dashboard',
		w: 2400,
		h: 884,
		tag: 'Dashboard',
		title: 'The fleet in six numbers',
		body: 'Compliant, non-compliant, missing, pending, checks defined, people. Every tile is a way in: click one and the report list opens already filtered to it.',
		alt: 'The Prüfstein dashboard: red, yellow and green count tiles above three plain tiles for pending reports, checks defined and users.'
	}
];
