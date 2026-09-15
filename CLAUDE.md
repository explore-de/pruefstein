# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Layout

The active Quarkus application lives in the `web/` subdirectory. The root-level Java sources have been removed and replaced by this subdirectory structure.

```
web/          ← Quarkus Maven project (pruefstein-web)
  pom.xml
  src/main/java/
    com/pruefstein/   ← Infrastructure (health check, example REST resource)
    model/            ← Domain models (Todo)
    rest/             ← Renarde controllers
    util/             ← Qute template extensions, dev-mode startup seeding
  src/main/resources/
    templates/        ← Qute server-side templates (extend main.html)
    web/              ← Web Bundler assets (app.js, app.scss → auto-bundled)
    application.properties
  src/test/java/
    com/pruefstein/   ← @QuarkusTest unit tests, @QuarkusIntegrationTest IT tests
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

HTTP request → Renarde `Controller` subclass (in `rest/`) → Qute template (in `templates/`) → rendered HTML.

Controllers use `@CheckedTemplate` inner classes for type-safe template binding. Templates extend `main.html` via `{#include main.html}`.

### Frontend bundling

Assets under `src/main/resources/web/` are automatically bundled by Quarkus Web Bundler (zero-config, no Node.js required). SCSS is compiled, JS is bundled — use standard imports.

### Persistence

`model/Todo.java` currently uses in-memory stubs (real Hibernate/Panache persistence is commented out). PostgreSQL JDBC driver is on the classpath but `application.properties` has no datasource configured yet. Quarkus Dev Services will spin up a PostgreSQL container automatically in dev/test mode when no datasource URL is set.

### Dev-mode seeding

`util/Startup.java` is `@ApplicationScoped` and seeds sample Todo data only when running in `@io.quarkus.runtime.LaunchMode.DEVELOPMENT`.

### Health / OpenAPI

- Health endpoint: `/q/health` (SmallRye Health, `@Liveness` in `com.pruefstein.MyLivenessCheck`)
- Swagger UI: `/q/swagger-ui` (SmallRye OpenAPI, available in dev mode)