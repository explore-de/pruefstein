# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Layout

The root `pom.xml` is a reactor over `web/` (the Quarkus server) and `agent/`
(the CLI that runs osquery on a Mac and reports to it).

```
web/src/main/java/com/pruefstein/
  <feature>/        ← agent, compliance, dashboard, device, homebrew, onboarding,
                      osversion, report, user — each split into api/ (Renarde
                      controllers, REST), domain/, repository/, service/
  mcp/              ← MCP tools, see below
  notification/     ← report mails
  shared/bootstrap/ ← SeedLedger, dev-mode demo data (Startup)
  shared/util/      ← Qute template extensions, Markdown
  dev/              ← dev-mode-only endpoints
web/src/main/resources/
  templates/        ← Qute templates, extend main.html
  web/              ← Web Bundler assets (app.js, app.scss)
  compliance-library/ ← baseline checks, one JSON file per library key
  application.properties
web/src/main/docker/Dockerfile.native-micro ← the image CI publishes
deploy/             ← production docker compose stack
```

## Commands

The root `pom.xml` is a reactor over two modules, `web` and `agent`, and holds
the one version both of them carry. Run Maven from the root for anything that
spans both, or from a module for anything that does not — a module resolves the
parent through `../pom.xml`, so neither needs the other installed.

```bash
# Everything, both modules
./mvnw test
./mvnw package

# One module (equivalently: cd web && ./mvnw …)
./mvnw test -pl web
./mvnw package -pl agent

# Dev mode with live reload — a single module, so run it there
cd web && ./mvnw quarkus:dev

# A single test class
./mvnw test -pl web -Dtest=ReportsTest

# Integration tests, which package first and are skipped by default
./mvnw verify -DskipITs=false

# Native build (requires GraalVM on JAVA_HOME)
./mvnw package -pl agent -Dnative

# Native build plus AgentBinaryIT against the binary itself
./mvnw verify -pl agent -Dnative
```

Releases go through `maven-release-plugin` from the root: `./mvnw
release:prepare` sets one version across the reactor, tags it `vX.Y.Z` and
pushes, and that tag is what makes `agent-release.yml` build the native
binaries and create the GitHub release. Nothing is deployed to a Maven
repository, so `perform` is configured to `verify` rather than `deploy`.

Formatting is enforced at `validate`: `formatter-maven-plugin` on `web`,
`impsort-maven-plugin` on both. `./mvnw formatter:format impsort:sort` fixes
what they would fail on. The agent's sources are not formatter-clean yet, which
is why only `web` runs the formatter.

## Architecture

**Stack**: Quarkus 3.32.4 · Java 25 · Renarde (server-side MVC) · Qute templates · Hibernate Panache Next · PostgreSQL · Quarkus Web Bundler

### Request flow

HTTP request → Renarde `Controller` subclass (in a feature's `api/`) → Qute template (in `templates/`) → rendered HTML.

Controllers use `@CheckedTemplate` inner classes for type-safe template binding. Templates extend `main.html` via `{#include main.html}`.

### Frontend bundling

Assets under `src/main/resources/web/` are automatically bundled by Quarkus Web Bundler (zero-config, no Node.js required). SCSS is compiled, JS is bundled — use standard imports.

### Persistence

Hibernate ORM with Panache repositories on PostgreSQL. There are no schema
migrations: prod runs `database.generation=update`, and Dev Services starts a
PostgreSQL container in dev and test.

Baseline checks come from `compliance-library/` and are seeded once per
database by `CatalogSeeder`; the `SeedLedger` remembers every key it claimed,
so an administrator's edits and deletions stick. Changing an already-seeded
check in deployed databases takes a ledgered migration in
`compliance/bootstrap/` (see `CatalogQueryMigration`); drop such a migration
once every deployment has run it.

### Dev-mode seeding

`shared/bootstrap/Startup.java` seeds blocked-app examples and demo reports only in
`LaunchMode.DEVELOPMENT`.

### Health / OpenAPI

- Health endpoint: `/q/health` (SmallRye Health)
- Swagger UI: `/q/swagger-ui` (SmallRye OpenAPI, available in dev mode)

### MCP

`/mcp` (Streamable HTTP, `quarkus-mcp-server-http`) exposes the tools in
`com.pruefstein.mcp`. It authenticates through the `api` OIDC tenant with a
bearer token, like the agent. Clients log in themselves via MCP OAuth: the 401
points at `/.well-known/oauth-protected-resource/mcp`, and they sign in as the
agent's public client on `http://localhost:33418/callback`
(`pruefstein.mcp.*`). `/McpGuide/index` walks users through it.

Each tool class carries the same `@RolesAllowed` as the screen it mirrors, and
the logic lives in shared services (`ReportAccess`, `ReportDetails`,
`UserAdministration`), so a permission change there applies to the web and MCP
alike.
