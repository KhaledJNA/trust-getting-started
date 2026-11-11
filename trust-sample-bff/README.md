# trust-sample-bff

Sample **Backend-for-Frontend** built on the Trust starters: the browser
only holds an HTTP session cookie, tokens never leave this application.
Part of a trio with [`trust-sample-api`](../trust-sample-api) and
[`trust-sample-downstream-api`](../trust-sample-downstream-api).

```text
browser ──cookie──► trust-sample-bff ──user's token──► trust-sample-api ──exchanged token──► trust-sample-downstream-api
  (9081 page)          (9081)            (JWT)            (9082)      ──client_credentials──►      (9083, opaque)
                          └──────────────user's token, introspected──────────────────────────────►
```

## Run it

Everything (libraries, Keycloak, realm, secrets, the three apps) starts
with one command in [`trust-getting-started`](../README.md):

```bash
docker compose up --build -d     # or ./scripts/dev.sh up, with a JDK
```

then open http://localhost:9081 and log in as `sample.user`, `sample.manager`
or `sample.denied` (password `sample`). Each button calls one route below
and shows the HTTP status and body it got.

## What it demonstrates

| Page button | Route | Shows |
|---|---|---|
| Log in | `/oauth2/authorization/trust-sample` | Authorization Code + PKCE S256, state, nonce (`trust-security-oauth2-client-starter`) |
| API /me, /reader | `/api/me`, `/api/reader` | The session's token relayed to the API (`trust-rest-client-starter`, no annotation needed), realm and client roles decided there |
| API /sensitive | `/api/sensitive` | The API's RFC 9470 challenge (`401`, `acr_values="strong"`) passed back; the page then starts a step-up through `/oauth2/step-up` |
| BFF /local/strong | `/api/local/strong` | `@RequiresAcr("strong")` decided by the BFF itself, on the session's ID token |
| Read / write document | `/api/documents/{id}` | Keycloak UMA decisions taken by the API, with the document's tenant as context |
| Direct | `/api/downstream/direct` | The session's token sent to an opaque-token API, validated by introspection |
| Via API | `/api/downstream/on-behalf`, `/system` | Token exchange (RFC 8693) and `client_credentials`, performed by the API |
| Async relay | `/api/async/me` | The same relay from an `@Async` thread: security context and request carried over by `trust-security-web` |
| Log out | `POST /logout` | CSRF-protected RP-initiated logout, redirect to Keycloak's end-session endpoint; back-channel logout is registered too |

`/`, the page assets and `/api/session` are public; everything else needs
a session (`trust.security.public-paths`). The page's CSP is strict
(`default-src 'self'`): no inline script or style.

## Choices worth copying

- **Its own `OAuth2AuthorizedClientManager`** (`SampleBffApplication`):
  Trust's default only knows `client_credentials` and token exchange; a BFF
  also needs `refresh_token`, since the realm's access tokens live 5
  minutes and the session 30.
- **Errors from the APIs** (`DownstreamErrorHandler`): a `403` or an ACR
  challenge concerns the user's own rights and is passed back as is;
  anything else is this BFF's upstream failing, answered `502` with the
  details in the log only.
- **A `401` from an API drops the stored token** (Spring Security's
  failure handler, wired by `trust-rest-client-starter`): the next call
  sends the user back to login. An API that rejects a token it should
  accept therefore looks like a logout -- check that API's log first.
- **No secret in `application.yml`**: client secrets come from
  `../env/trust-sample-client-secrets.env`
  (written when Keycloak imports the realm; override with
  `TRUST_SAMPLE_SECRETS`).
  The file is mandatory: without it the application refuses to start and
  names it.

## Tests

Run from this folder, once the libraries are installed (`./mvnw install`
at the root of `trust-getting-started`):

```bash
../mvnw test
```

No Keycloak needed: `@WithTrustOidcUser` (`trust-oauth2-client-test`)
builds the session from a claim set (`src/test/resources/user.json`)
through the real authorities mapping, the APIs are mocked, and an
in-memory `ClientRegistrationRepository` avoids issuer discovery.
