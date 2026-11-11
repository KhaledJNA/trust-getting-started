# trust-getting-started

[![demo](https://github.com/KhaledJNA/trust-getting-started/actions/workflows/demo.yml/badge.svg)](https://github.com/KhaledJNA/trust-getting-started/actions/workflows/demo.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Try the [Trust security starters](https://github.com/KhaledJNA/trust-security)
in a few minutes: a local Keycloak and three sample applications that use
every starter, started with one command.

```text
browser ──cookie──► trust-sample-bff ──user's token──► trust-sample-api ──exchanged token──► trust-sample-downstream-api
          :9081        (OIDC login)                       (JWT, UMA)   ──client_credentials──►   (opaque tokens) :9083
                            └──────────────────user's token, introspected────────────────────────────►
                                                 Keycloak :18080, realm trust-sample
```

## 0. Prerequisites

| | macOS | Windows | Linux |
|---|---|---|---|
| Docker | Docker Desktop | Docker Desktop, WSL2 backend | Docker Engine 23+ with the `docker-compose-plugin` (Compose 2.20+) |
| Memory for Docker | 4 GB (Settings → Resources) | 4 GB (Settings → Resources, or `.wslconfig`) | no limit by default |
| Git | included | Git for Windows (includes Git Bash) | distribution package |
| Developer mode only | JDK 21+ | JDK 21+ **inside WSL2** | JDK 21+ |

Intel and ARM machines (Apple Silicon, ARM laptops) are both supported:
every image used exists for `amd64` and `arm64`.

**On Windows**, run the commands below in **Git Bash** (or in a WSL2
terminal). The Docker mode works from either; the developer mode needs
WSL2: clone the repositories inside WSL2 (`~/trust`, not `/mnt/c/...`),
which is also much faster for Docker. Line endings need no setting:
`.gitattributes` keeps scripts in LF whatever `core.autocrlf` says.

## 1. Get the repositories

This repository holds the samples; the libraries are in
[`trust-security`](https://github.com/KhaledJNA/trust-security), cloned
next to it:

```bash
mkdir trust && cd trust
git clone https://github.com/KhaledJNA/trust-getting-started.git
cd trust-getting-started
./scripts/clone.sh          # clones trust-security next to this repository (skipped if present)
```

Working from a fork? `clone.sh` takes `trust-security` from the same
GitHub account as this repository; to take it from elsewhere:
`TRUST_GIT_BASE_URL=https://github.com/KhaledJNA ./scripts/clone.sh`.

## 2. Start everything

**With nothing but Docker** (Docker Desktop or Docker Engine with Compose v2):

```bash
docker compose up --build -d
```

The first run takes a few minutes: it downloads Maven dependencies and
compiles every library inside Docker. Later runs reuse the cache.

**Or in developer mode**, to change the code (needs JDK 21+ as well):

```bash
./scripts/dev.sh up         # Keycloak in Docker, the samples on your JDK
```

Use one mode or the other: both use ports 9081-9083.

## 3. Try it

Open http://localhost:9081. Password `sample` for every user:

| User | Roles |
|---|---|
| `sample.user` | `SAMPLE_USER`, client role `SAMPLE_READER` |
| `sample.manager` | `SAMPLE_USER`, `SAMPLE_MANAGER` |
| `sample.denied` | none |

Each button calls the BFF and shows the HTTP status and body it got. Log
out between users.

| Who | Button | Expected |
|---|---|---|
| `sample.user` | API /me, API /reader, Read document 1 | 200 |
| `sample.user` | Write document 1 | 403: not a manager |
| `sample.user` | Direct, Via API (×2), Async relay | 200: who called, with which token |
| `sample.user` | API /sensitive | 401 → step-up: accept, enroll an OTP app, then 200 |
| `sample.manager` | Write document 1 | 200: manager and document in `tenant-a` |
| `sample.manager` | Write document 2 | 403: document in `tenant-b` |
| `sample.denied` | API /me | 403 |

The first step-up asks to enroll an authenticator app (Google or Microsoft
Authenticator, FreeOTP): scan the QR code, type the code. The page header
then shows `acr=strong`.

What each sample demonstrates and how: see the README of
[`trust-sample-bff`](trust-sample-bff),
[`trust-sample-api`](trust-sample-api) and
[`trust-sample-downstream-api`](trust-sample-downstream-api).

Keycloak's admin console: http://localhost:18080/admin (`admin` / `admin`).

## 4. Run the tests

```bash
./mvnw install                        # every library and sample, with their tests
./mvnw install -Pintegration-tests    # plus the certification suite against a real Keycloak
```

The `integration-tests` profile adds
[`trust-security-integration-tests`](https://github.com/KhaledJNA/trust-security/tree/main/trust-security-integration-tests):
it starts its own Keycloak with Testcontainers (Docker needed, no fixed
port, independent of the demo stack) and checks the starters against it.
The developer mode prerequisites apply (JDK 21+, inside WSL2 on Windows).

## 5. Stop

```bash
docker compose down         # Docker mode (add -v to also forget users, OTP and secrets)
./scripts/dev.sh down       # developer mode
```

## What is in this repository

| File | Role |
|---|---|
| `trust-sample-bff`, `trust-sample-api`, `trust-sample-downstream-api` | The three samples. They import the `trust-dependencies` BOM, as any application using Trust would |
| `docker-compose.yml` | Keycloak, its one-shot setup, the three samples |
| `realm/trust-sample-realm.json` | The demo realm, users included, imported by Keycloak on its first start |
| `docker/keycloak-setup.sh` | Writes the client secrets Keycloak generated to `env/trust-sample-client-secrets.env` |
| `docker/apps.Dockerfile` | Builds the libraries and the samples, then one runtime image for the three |
| `pom.xml` | Maven aggregator of `../trust-security` and the samples: `./mvnw install -DskipTests` builds everything, in dependency order; `-Pintegration-tests` adds the certification suite |
| `repos.txt`, `scripts/clone.sh` | The repository to clone next to this one (`trust-security`), and how |
| `.gitattributes` | Keeps scripts and Dockerfiles in LF on Windows checkouts |
| `env/` | Generated client secrets (gitignored; the folder itself is versioned so that it belongs to you, not to the container's root user) |
| `scripts/dev.sh` | Developer mode: `up`, `keycloak`, `build`, `start`, `stop`, `down`, `status`, `logs <app>` |

How it fits together:

- **No secret is committed.** Keycloak generates the client secrets when it
  imports the realm; `keycloak-setup` writes them to `env/` (gitignored),
  where the samples read them. A sample started without that file refuses
  to start and names it.
- **One issuer URL for everyone.** The browser reaches Keycloak at
  `localhost:18080`. In Docker mode the samples share the network of a
  small `gateway` container that forwards its own `localhost:18080` to
  Keycloak, so tokens carry the same issuer whoever asked for them, and
  the samples call each other on `localhost` exactly as in developer mode.
- **Demo only.** Keycloak runs `start-dev` (HTTP, embedded database kept in
  a Docker volume). The realm, its users, their password and its
  `localhost` URLs are made for this demo: never reuse them as they are.

## Troubleshooting

| Symptom | Look at |
|---|---|
| A port is already in use | Who holds it: `lsof -nP -iTCP:9081 -sTCP:LISTEN` (macOS, Linux) or `netstat -ano \| findstr :9081` (Windows), likewise for 9082, 9083, 18080. The other mode may still be running |
| Build killed, or containers restarting with exit code 137 | Docker is out of memory: give it 4 GB (see Prerequisites) |
| `$'\r': command not found` | The repository was copied, not cloned, with Windows line endings: clone it with Git |
| `permission denied` on `realm/` or `env/` (Fedora, RHEL) | SELinux: check that `docker-compose.yml` was not edited to drop the `z` on its volumes |
| A button answers 502 | The log of the API behind it: `docker compose logs trust-sample-api`, or `./scripts/dev.sh logs trust-sample-api` |
| After a 401 every button sends you to login | An API rejected the session's token, and the BFF dropped it: the API's log says why |
| Start from scratch | `docker compose down -v`, then start again |

## License

[Apache License 2.0](LICENSE).
