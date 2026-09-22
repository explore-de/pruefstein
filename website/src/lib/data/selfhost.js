import { site } from './site.js';

/** What has to exist before `docker compose up` gets anywhere. */
export const needs = [
	{
		title: 'Docker with Compose v2',
		body: 'On a Linux host, with a DNS name pointing at it.'
	},
	{
		title: 'A reverse proxy for TLS',
		body: 'The stack comes wired for Traefik on an external proxy network. Any other proxy works if the app’s port stays reachable by nothing else.'
	},
	{
		title: 'An Entra ID app registration',
		body: 'Redirect URI /oidc-callback, public client flows allowed for the agent, an Application ID URI, and an admin app role for the people who manage checks.'
	},
	{
		title: 'An SMTP account',
		body: 'For the invitations, the reminders and the outcome mails.'
	},
	{
		title: 'An OpenAI key, if you want one',
		body: 'Leave it out and everything runs the same, minus the explanations.'
	}
];

export const compose = `git clone ${site.repo}.git
cd pruefstein/deploy

cp .env.example .env
$EDITOR .env        # host, Postgres, Entra, SMTP

docker compose up -d
docker compose logs -f web`;
