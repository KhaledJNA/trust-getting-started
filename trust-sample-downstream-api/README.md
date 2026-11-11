# trust-sample-downstream-api

Sample **opaque-token resource server** built on
`trust-security-resource-server-starter`: every token is validated by
asking Keycloak (RFC 7662 introspection), never decoded locally. Port 9083.
Called by [`trust-sample-bff`](../trust-sample-bff) and
[`trust-sample-api`](../trust-sample-api); start everything with
`docker compose up --build -d` in
[`trust-getting-started`](../README.md).

| Route | Protection | Returns |
|---|---|---|
| `GET /public/ping` | none | status |
| `GET /data` | role `SAMPLE_USER` (`realm_access.roles` from the introspection response) | the user and `issuedTo`, the client the token was issued to: `trust-sample-bff` (relayed session token) or `trust-sample-api` (exchanged token) |
| `GET /system/status` | authority `SCOPE_sample.system` | `calledBy`: the application that called with `client_credentials` |

**Keycloak 26 introspects a token only for a client in its audience**:
it answers `active: false` otherwise. Every token sent here must carry
`trust-sample-downstream-api` in `aud` -- the realm adds it through an
audience mapper on `trust-sample-bff` and on `trust-sample-api`.

The introspection client secret comes from
`../env/trust-sample-client-secrets.env`
(override with `TRUST_SAMPLE_SECRETS`); the file is mandatory.

Tests, from this folder once the libraries are installed (`./mvnw install`
at the root of `trust-getting-started`):

```bash
../mvnw test    # no Keycloak needed
```
