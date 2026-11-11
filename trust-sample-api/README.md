# trust-sample-api

Sample **JWT resource server** built on the Trust starters, called by
[`trust-sample-bff`](../trust-sample-bff) and calling
[`trust-sample-downstream-api`](../trust-sample-downstream-api). Port 9082.
Start everything with `docker compose up --build -d` in
[`trust-getting-started`](../README.md).

## What it demonstrates

| Route | Protection | Starter |
|---|---|---|
| `GET /public/ping` | none (`trust.security.resource-server.public-paths`) | resource-server |
| `GET /me` | realm role: `realm_access.roles` → `ROLE_SAMPLE_USER` | resource-server |
| `GET /reader` | client role: `resource_access.trust-sample-api.roles` → `ROLE_SAMPLE_READER` | resource-server |
| `GET /sensitive` | `@RequiresAcr("strong")`: `401` + `WWW-Authenticate: Bearer error="insufficient_user_authentication", acr_values="strong"` | resource-server |
| `GET /documents/{id}` | `@RequiresPermission(resource = "Document", scope = "read")`, decided by Keycloak (UMA) | keycloak-authorization |
| `PUT /documents/{id}` | `@CanWriteDocument` (a meta-annotation) + the document's tenant sent as UMA context (`DocumentTenantContextProvider`: odd ids → `tenant-a`, even → `tenant-b`) | keycloak-authorization |
| `GET /downstream/on-behalf` | calls the downstream API with a token **exchanged** for the caller's (RFC 8693): same user, issued to `trust-sample-api` | rest-client |
| `GET /downstream/system` | calls it **as this application** (`client_credentials`, scope `sample.system`) | rest-client |

The Keycloak side (realm `trust-sample`, client `trust-sample-api`):
`read` is granted by `SAMPLE_USER`; `write` needs `SAMPLE_MANAGER` **and**
a `tenant` context equal to `tenant-a`. So `sample.manager` may write
document 1 but not document 2, and `sample.user` may read but not write.

The token sent downstream is chosen per method of `DownstreamClient`, by
`@ClientRegistrationId` naming a registration of the matching grant type
-- never deduced, and never a silent fallback to the incoming token.

## Configuration

`application.yml` holds no secret: they come from
`../env/trust-sample-client-secrets.env`
(override with `TRUST_SAMPLE_SECRETS`). The token endpoint is given
directly for the outgoing registrations, so startup does not depend on
issuer discovery for them.

## Tests

Run from this folder, once the libraries are installed (`./mvnw install`
at the root of `trust-getting-started`):

```bash
../mvnw test
```

No Keycloak needed: `@WithTrustJwt("user.json")` (`trust-resource-server-test`)
runs a claim set through the real authorities mapping, and
`TestAuthorizationDecisionClient` (`trust-security-test`) replaces only
Keycloak's UMA decision -- the `@RequiresPermission` chain and the tenant
context provider are the real ones, and the tests check the context sent.
