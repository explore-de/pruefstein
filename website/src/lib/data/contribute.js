/**
 * Contribution lanes. Ordered easiest-first on purpose: the catalog entry is a
 * two-line pull request, and that is the door most people should come through.
 */
export const lanes = [
	{
		tag: 'Good first issue',
		title: 'Add a check to the catalog',
		body: 'You know an osquery table and the control it satisfies. Add a CheckDef with a permanent key, an expression and the Annex A group it belongs to. Two lines of Java and a test. No Quarkus knowledge required.',
		cta: 'Browse the catalog',
		href: 'https://github.com/explore-de/pruefstein/blob/main/web/src/main/java/com/pruefstein/compliance/bootstrap/ComplianceCatalog.java',
		accent: true
	},
	{
		tag: 'Most wanted',
		title: 'Windows and Linux checks',
		body: 'The agent already runs anywhere osquery runs. The seeded catalog does not: it is macOS to the last row. A Windows or Linux equivalent for each control is the single biggest thing missing from the project.',
		cta: 'Open an issue',
		href: 'https://github.com/explore-de/pruefstein/issues/new'
	},
	{
		tag: 'Code',
		title: 'Pick up the agent or the web app',
		body: 'Quarkus 3, Java 25, Renarde and Qute on the server; Picocli and a native image on the client. Both build with the wrapper, both have tests, and dev mode brings up Postgres and Keycloak for you.',
		cta: 'Read the README',
		href: 'https://github.com/explore-de/pruefstein#readme'
	},
	{
		tag: 'Docs',
		title: 'Write down what tripped you up',
		body: 'Deploying behind a proxy, wiring Entra ID, scheduling the agent with launchd or systemd. If you worked it out once, the note you wished you had is a contribution.',
		cta: 'Start a discussion',
		href: 'https://github.com/explore-de/pruefstein/discussions'
	},
	{
		tag: 'Field report',
		title: 'Tell us it failed your audit',
		body: 'A control your auditor rejected, an expression that is wrong on macOS 15, a check that passes when it should not. Negative results are worth more than feature requests here.',
		cta: 'File a bug',
		href: 'https://github.com/explore-de/pruefstein/issues/new'
	},
	{
		tag: 'No code',
		title: 'Map more of Annex A',
		body: 'Which controls are genuinely machine-checkable on an endpoint, and which are policy that no query will ever settle? That judgement is the hard part, and it does not need a compiler.',
		cta: 'Join the discussion',
		href: 'https://github.com/explore-de/pruefstein/discussions'
	}
];

/** Promises the project makes about being open source. */
export const promises = [
	{
		title: 'No paid tier',
		body: 'There is no enterprise edition, no seat count and no feature behind a licence key. EXP Software GmbH builds Prüfstein and gives all of it away. What you clone is what exists.'
	},
	{
		title: 'No hosted service',
		body: 'Nobody runs Prüfstein for you, so nobody else holds a list of which of your laptops are unencrypted. That is on purpose.'
	},
	{
		title: 'Your data stays yours',
		body: 'Postgres, your infrastructure, your OIDC provider. The only outbound call the app can make is to a model provider, and only if you configure a key.'
	},
	{
		title: 'Readable by design',
		body: 'A check is SQL and one expression. You can verify what the tool asserts about your fleet without trusting the tool.'
	}
];
