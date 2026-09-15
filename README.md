# Prüfstein

**Device compliance management for ISO 27001 — powered by [osquery](https://osquery.io/).**

Prüfstein lets you define compliance checks as SQL queries, run them on every employee's device via a lightweight local agent, and track pass/fail status across your entire fleet from a central web dashboard.

---

## How it works

The web app (Quarkus) lets admins define Compliance Items: each item belongs to a ComplianceGroup (e.g. "A.8 Asset Management") and contains a SQL query and a JEXL pass/fail expression. The dashboard shows per-user/device compliance Reports, each containing a ComplianceResult per item.

A lightweight local agent runs on each employee's machine. It fetches all ComplianceItems via `GET /api/checks`, executes each SQL query through osquery, evaluates the result against the expected expression, and — once the person running it says so — POSTs a Report back via `POST /api/reports`. Checking is free and repeatable; reporting is the deliberate step, so a machine can be checked and fixed as often as needed before anything is filed. For a schedule (cron, launchd, systemd) `--yes` answers the question up front.

---

## Domain model

| Entity | Key fields | Purpose |
|---|---|---|
| `AppUser` | firstname, lastname, mail | An employee whose devices are checked |
| `ComplianceGroup` | name | Groups related items (maps to ISO 27001 control family) |
| `ComplianceItem` | name, query, expectedExpression, group | One osquery check with a JEXL pass/fail expression |
| `Report` | user, deviceHostname, checkedAt, passed | One agent run for one device |
| `ComplianceResult` | item, report, actualResult (JSON), passed | Outcome of one check in one report |

---

## Compliance Items

A Compliance Item is a SQL query against the [osquery schema](https://www.osquery.io/schema/) plus a **[JEXL](https://commons.apache.org/proper/commons-jexl/) expression** that is evaluated against the full JSON result array. The expression must return `true` for the check to pass.

The JEXL context exposes `results` — the parsed JSON array returned by `osqueryi --json`.

**Example — disk encryption (macOS):**
```sql
SELECT encrypted FROM mounts WHERE path = '/';
```
```jexl
results[0].encrypted == "1"
```

**Example — firewall enabled (macOS):**
```sql
SELECT global_state FROM alf;
```
```jexl
results[0].global_state == "1"
```

**Example — screen lock timeout ≤ 300 s:**
```sql
SELECT value FROM preferences WHERE domain = 'com.apple.screensaver' AND key = 'idleTime';
```
```jexl
results[0].value =~ '\d+' && Integer.parseInt(results[0].value) <= 300
```

**Example — no unknown listening ports:**
```sql
SELECT DISTINCT port FROM listening_ports WHERE pid != 0;
```
```jexl
results.size() == 0
```

---

## Local agent

The agent is a Java program installed on each employee's machine that:

1. Authenticates to the web app via **OIDC** (the employee logs in once; the agent uses the token)
2. Downloads the current list of `ComplianceItem`s
3. Runs each query via `osqueryi --json`
4. Evaluates the **JEXL expression** (`expectedExpression`) against the full JSON result
5. Sends a `Report` payload back to the web app

One user can have multiple devices — each device reports independently and appears as a separate `Report` identified by hostname.

It is intended to run as a scheduled task (launchd on macOS, systemd on Linux, Task Scheduler on Windows).

### Building and installing the CLI

Prerequisites: **JDK 25** (GraalVM if you want the native binary), **Docker** or
Podman for the web app's Dev Services, and **osquery** on the `PATH`
(`brew install --cask osquery` on macOS).

```bash
git clone git@github.com:d135-1r43/pruefstein.git
cd pruefstein
./agent/bin/install.sh
```

That is the whole setup. The installer builds the agent if nothing is built
yet, then links it as `pruefstein-agent` into whichever directory is already
on your `PATH` — so there is normally nothing to add to your shell profile. If
no suitable directory exists it uses `~/.local/bin` and prints the one line to
add. `./agent/bin/install.sh --uninstall` removes the command again.

What gets linked is `agent/bin/pruefstein-agent`, a launcher rather than a copy: it
runs whichever build is present in `agent/target`, preferring the native binary
over the JVM one. A rebuild therefore takes effect immediately, with nothing to
reinstall — including the switch to a native build:

```bash
cd agent && ./mvnw package -Dnative     # same command afterwards, ~20 ms startup
```

Against a local server, start the web app first. Dev Services bring up
PostgreSQL and a Keycloak realm seeded with `admin`/`admin` and `user`/`user`:

```bash
(cd web && ./mvnw quarkus:dev)                           # terminal 1

pruefstein-agent login --server http://localhost:8080    # terminal 2
pruefstein-agent run                                     # asks before reporting
```

### Logging in

```bash
pruefstein-agent login --server https://pruefstein.example.com
```

The agent carries no identity-provider configuration of its own. It asks the
server it reports to (`GET /internal/agent-config`) for the issuer, client id
and scopes, then discovers the device-flow endpoints from that issuer's
`/.well-known/openid-configuration`. The same binary therefore works against a
Keycloak and an Entra deployment without a rebuild, and the token it obtains is
audienced to whatever the server's `api` tenant validates.

The server URL, the tokens and the issuer are stored together in
`~/.config/pruefstein/credentials.json`; later `pruefstein-agent run` invocations need no
arguments and refresh the token unattended. `QUARKUS_REST_CLIENT_PRUEFSTEIN_API_URL`
still overrides the stored server, for CI.

Reports are attributed to the person who logged in — the server builds the
`AppUser` from the token's `sub`, `email`, `given_name` and `family_name` — so
the grant is device code, not client credentials.

Against Entra, the app registration has to allow public client flows (device
code) and expose an Application ID URI, and `%prod.pruefstein.agent.scopes`
requests `api://<client-id>/.default offline_access`. Without the API scope
Entra audiences the access token to Microsoft Graph and the `api` tenant
rejects it; without `offline_access` no refresh token is issued and every run
would prompt for an interactive login. Set `PRUEFSTEIN_AGENT_CLIENT_ID` when the
agent gets a registration separate from the web client.

---

## Web app stack

- **Quarkus 3** + Renarde (server-side MVC)
- **Qute** templates
- **Hibernate Panache** + PostgreSQL (Dev Services in dev mode)
- **Quarkus Web Bundler** (Tailwind CSS + Alpine.js, no Node.js required)

### Running locally

```bash
cd web
./mvnw quarkus:dev
```

Quarkus Dev Services starts a PostgreSQL container automatically. The app is available at `http://localhost:8080`.

### Setup walkthrough

The three commands a new colleague has to run are written once, in
`SetupManual`, and rendered twice: by the invitation mail and by the **How to
Report** page at `/Manual/index`. The login step names the server from
`pruefstein.web.base-url`, so the command in both places is one a reader can
paste as it stands.

The repository those mails and pages point at is
`pruefstein.project.repository-url` in `application.properties`. It defaults to
this repository — point it at a fork or an internal mirror if that is where
your people should be reading the agent's source before they run it.

### Production

A ready-to-use Compose stack (Postgres, Kafka, and the web image from `ghcr.io/d135-1r43/pruefstein-web`) lives in `deploy/`. Copy `deploy/.env.example` to `deploy/.env`, fill in the required values (database credentials, Entra tenant/client IDs, OpenAI key), then run:

```bash
cd deploy
docker compose up -d
```

---

## ISO 27001 mapping

Compliance Groups map to ISO 27001 Annex A control families. Suggested groups:

| Group | Controls |
|---|---|
| A.8 Asset Management | Inventory, software install policy |
| A.9 Access Control | Screen lock, password policy, MFA presence |
| A.10 Cryptography | Disk encryption |
| A.12 Operations Security | Auto-update, AV, firewall |
| A.13 Network Security | VPN, DNS-over-HTTPS |

---

## Architecture decisions

| Topic | Decision |
|---|---|
| Local agent | Java (single distributable JAR) |
| Authentication | OIDC — employee logs in once, token stored by the agent |
| Authorization | Two-tier RBAC via configured OIDC provider roles (see below) |
| Pass/fail logic | JEXL expression evaluated against full osquery JSON output |
| Result payload | Full JSON string from `osqueryi --json` stored as `actualResult` |
| Multi-device | One `AppUser` → many `Report`s, differentiated by `deviceHostname` |

### Authorization (RBAC)

Every endpoint requires an explicit security annotation (`quarkus.security.jaxrs.deny-unannotated-endpoints=true`). Two roles are recognized:

| Role | Permissions |
|---|---|
| Regular user | Read compliance groups/items; view own reports (filtered by `oidcSubject`) |
| Admin | All of the above, plus full CRUD for Users/Compliance Groups/Items, view all reports, access AI suggest |

The admin role name defaults to `admin` and is configurable via `pruefstein.security.admin-role` in `application.properties`. The OIDC claim path from which roles are read is configurable via `quarkus.oidc.roles.role-claim-path` — use `realm_access/roles` for Keycloak and `roles` for Microsoft Entra ID. In dev mode, Quarkus Dev Services starts a Keycloak container using the realm definition in `keycloak/pruefstein-realm.json`, which ships with the `admin` realm role pre-assigned to the `admin` user. In production, Microsoft Entra ID is used as the OIDC provider. Templates hide admin controls (Add/Edit/Delete buttons, Users nav link) for non-admin users.
