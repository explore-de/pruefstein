# pruefstein-agent

The local compliance agent. It logs in once as the employee, downloads the
compliance checks from a Prüfstein server, runs each of them through
[osquery](https://osquery.io/), shows what they found — and only then asks
whether to report it.

Built with Quarkus and [Picocli](https://quarkus.io/guides/picocli).

---

## Installing

From this directory:

```bash
./bin/install.sh
```

(or `./agent/bin/install.sh` from the repository root). This builds the agent
if nothing is built yet and makes it available as the
`pruefstein-agent` command. `./bin/install.sh --uninstall` removes it again.
See the [root README](../README.md#building-and-installing-the-cli) for
prerequisites and the full first-run walkthrough.

## Commands

| Command | What it does |
|---|---|
| `login` | Authenticates against the server's identity provider and caches the credentials |
| `run` | Runs every compliance check, then asks whether to report the result |
| `logout` | Deletes the cached credentials |

```bash
pruefstein-agent login --server https://pruefstein.example.com
pruefstein-agent run
pruefstein-agent logout
```

Every command accepts `--help`.

### login

`--server` (`-s`) names the Prüfstein server to report to. It is only needed
the first time: the URL is stored alongside the credentials and reused by every
later run. Naming a *different* server discards the cached token — it was
issued by the previous server's identity provider and means nothing to the new
one.

Login uses the OAuth **device code** flow: the agent prints a URL and a code,
you confirm in the browser, and the token comes back to the agent. The agent
itself carries no identity-provider configuration — it asks the server
(`GET /internal/agent-config`) which issuer and client id to use, so the same
binary works against Keycloak and Microsoft Entra ID without a rebuild.

Everything ends up in `~/.config/pruefstein/credentials.json`: server URL,
issuer, access token and refresh token.

### run

A run is two separate things: checking the machine, and reporting on it. The
checks run first and print their verdicts, and nothing has reached the server
at that point — the only thing `run` needed it for was the list of checks. Then
it asks:

```
  [PASS] FileVault enabled
  [FAIL] Firewall enabled
────────────────────────────────────────────
Done: 3/4 checks passed
Report this run? [y/N]
```

Answer no and nothing is filed: turn the firewall on, run again, and report the
run you would rather stand behind. Answer yes and the report exists — there is
no unsending it, which is why the question is asked at the one moment when the
verdicts are known and the server still knows nothing.

Anything other than `y` or `yes` is a no, a bare Enter included.

A yes that reports failures gets told what they cost:

```
View report: http://localhost:8080/Reports/show/41
2 checks are still failing. Fix them and report again by 10 Sep 2026 (7 days),
or this report is recorded as non-compliant.
```

The date comes back from the server rather than being worked out locally,
because the window belongs to the report and not to the run: reporting a
still-failing machine again replaces what the report holds without pushing its
deadline back, so the days left shrink with every attempt. Report a clean run
before then and the same report closes compliant instead.

**Unattended runs need `--yes`.** With stdin closed — cron, launchd, CI — the
prompt reaches EOF, and an EOF is not consent: nothing is reported and `run`
exits non-zero, so a schedule that is quietly reporting nothing looks broken
rather than healthy.

```bash
pruefstein-agent run --yes    # or -y
```

Needs **`osqueryi` on the `PATH`**. When it is missing, `run` asks before
doing anything else:

```
You need osqueryi to continue, install it? [y/n]
```

A `y` runs `brew install --cask osquery` in the foreground — Homebrew installs
a signed pkg, so it will ask for a sudo password. Anything else, a bare Enter
included, aborts with exit code 1 and installs nothing. An unattended `run`
never reaches the prompt: with stdin closed it aborts on the spot rather than
waiting for an answer, so cron gets a non-zero exit instead of a report in
which every check errored.

Each query gets 10 seconds before it is abandoned.

Checks run **concurrently**. Each one is its own short-lived `osqueryi`
process and nearly all of the cost is process startup — 12 invocations
measured 3.2 s one after another against 0.31 s at once — so a run finishes in
about the time its slowest check takes. Concurrent `osqueryi` instances do not
contend for anything: unlike `osqueryd` it keeps its database in memory.

```bash
PRUEFSTEIN_AGENT_CHECK_PARALLELISM=4 pruefstein-agent run   # gentler on the machine
```

`pruefstein.agent.check-parallelism` (default 100) is a cap, not a target — a
run never starts more processes than it has checks. Results keep the order the
server sent them in; only the `[PASS]`/`[FAIL]` lines arrive as each check
finishes.

Apart from `--yes`, `run` takes no arguments — it reads the stored server and
refreshes the access token on its own. If the refresh token is gone or rejected
it falls back to an interactive device login, which is worth knowing before
putting `run` in cron or launchd: an unattended run can end up waiting for a
browser confirmation that nobody gives.

The token is only needed at two points — fetching the checks and filing the
report — and each is retried on its own if the server rejects the credentials.
A token that expires while someone thinks about the question therefore costs
them the wait for a refresh, not the run.

`QUARKUS_REST_CLIENT_PRUEFSTEIN_API_URL` overrides the stored server for a
single run, which is what CI uses.

---

## Logging

Quiet by default. Every command prints what it has to say on stdout, and the
log only speaks up at `WARN` or worse — no banner, no startup lines. Raise it
for a run that has to be explained:

```bash
PRUEFSTEIN_AGENT_LOG_LEVEL=DEBUG pruefstein-agent run
```

`INFO` gets the Quarkus startup lines back; `DEBUG` adds the stack trace behind
every `[ERROR]` check line. The banner is a build-time switch and does not come
back with the level — `quarkus.banner.enabled` and
`quarkus.banner-generator.enabled` in `application.properties` turn it on again.

---

## Output

The commands write what they have to say to standard output — the verdicts, the
summary, the report URL, the prompts. None of it goes through the logger, which
is left at its Quarkus defaults and used for what a log is for: the warning when
the installed-app inventory could not be read, the debug line naming why a check
errored or a token refresh was rejected.

That split is worth keeping. Routed through the logger, program output can only
be made to look like program output by flattening the log format for everything
else, and a real warning then arrives with no level, no timestamp and no logger
name to tell it apart from ordinary output.

Verdicts are coloured: `[PASS]` green, `[FAIL]` and `[ERROR]` red. The closing
summary is bold, and green when the whole run passed; a run with failures is
left in the terminal's normal text colour, since the red already sits on the
`[FAIL]` lines that name the checks. The deadline notice after a reported
failure *is* red — it is the one line that says something the `[FAIL]` lines do
not, that a window is counting down. A faint rule separates the summary from
the per-check lines:

```
  [PASS] FileVault enabled
  [FAIL] Firewall enabled
  [ERROR] Screen lock under 5 minutes — osqueryi timed out after 10 seconds
────────────────────────────────────────────
Done: 1/4 checks passed
Report this run? [y/N]
```

Colour is decided by picocli's `Ansi.AUTO`, so it turns itself off when there
is no terminal — piped into a file, mailed by cron, or with `NO_COLOR=1` set.

A check that errors says why on its own line; the stack trace behind it is a
debug log. Quarkus logs its own startup at `INFO` — the profile, the installed
features, the scheduler notice — so a command's output arrives with those lines
around it unless the log level is lowered for the run:

```bash
QUARKUS_LOG_LEVEL=WARN pruefstein-agent run     # just the command's own output
QUARKUS_LOG_LEVEL=DEBUG pruefstein-agent login  # why a refresh failed
QUARKUS_BANNER_ENABLED=false pruefstein-agent   # without the banner
```

Nothing pins a category, so `QUARKUS_LOG_LEVEL` means what it says and no
rebuild is needed to reach the agent's own `DEBUG` statements.

---

## Running without installing

The launcher is a convenience, not a requirement. After a build you can always
invoke the artifact directly:

```bash
java -jar target/quarkus-app/quarkus-run.jar login --server http://localhost:8080
./target/pruefstein-agent-1.0.0-SNAPSHOT-runner run     # after a native build
```

In dev mode, arguments are passed through `quarkus.args`:

```bash
./mvnw quarkus:dev -Dquarkus.args='run'
```

Dev mode runs the application and restarts it on Enter — it does **not** exit
when the command finishes, and it prints its own startup logging regardless of
the level above. That is dev mode, not the agent: the packaged artifact runs
the command and exits. Use it for development, and the installed
`pruefstein-agent` command for actual runs.

## Building

`bin/install.sh` builds for you; these are for when you want a specific
packaging:

```bash
./mvnw package            # fast-jar in target/quarkus-app/ — what the installer builds
./mvnw package -Dnative   # native binary; the launcher prefers it, nothing to reinstall
./mvnw test
```

## Packaging a release

`bin/install.sh` links the command back into this working tree, which is right
while you are working on the agent and useless to anybody installing it. For
that there is:

```bash
./bin/package.sh --build    # build the native binary, then archive it
./bin/package.sh            # archive whatever is already in target/
```

It writes `dist/pruefstein-agent-<version>-<os>-<arch>.tar.gz` and a `.sha256`
beside it. The archive holds the binary under its command name plus the licence
and this README, so an unpacked copy explains itself — the shape a Homebrew
formula, or anything else that downloads a tarball, expects.

Archiving is deterministic: ownership, timestamps, member order and the gzip
header are all pinned, so re-packaging the same binary yields the same
checksum. The binary itself is not reproducible — `native-image` bakes build
paths into it — so a formula should pin the checksum of a published archive
rather than one built locally.

**A native build only ever targets the machine it runs on.** `.github/workflows/
agent-release.yml` therefore builds on one runner per architecture — Apple
silicon and Intel — checks that the archived binary starts, and attaches both
archives to the release when a `v*` tag is pushed. `./mvnw release:prepare` from
the repository root is what pushes that tag; see [Building](../README.md#building).
Run the workflow from the Actions tab without a tag to get the archives as
workflow artifacts instead, which is the cheap way to try a formula against a
real binary before naming a version.

## Related guides

- [Picocli](https://quarkus.io/guides/picocli) — the CLI framework
- [OpenID Connect Client](https://quarkus.io/guides/security-openid-connect-client) — token handling
- [Maven tooling](https://quarkus.io/guides/maven-tooling) — native builds
