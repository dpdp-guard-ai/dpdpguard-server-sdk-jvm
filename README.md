# dpdpguard-server-sdk-jvm

DPDP Guard Server SDK (JVM/Kotlin) — typed API client and consent
enforcement for backend fleets, over DPDP Guard's public `/api/v1`
(spec §4.2).

Package: `ai.dpdpguard:server-sdk`

Part of the DPDP Guard SDK family. See the design spec:
https://github.com/dpdp-guard-ai/dpdpbot/blob/main/docs/specs/mobile-server-sdk.md

## Contract

This SDK has no build-time dependency manager for
[`@dpdpguard/contract`](https://www.npmjs.com/package/@dpdpguard/contract) —
there's no Maven mirror yet (ADR-002 D2 calls for one; only the npm package
exists). `build.gradle.kts`'s `downloadOpenApiSpec` task fetches
`openapi/v1.yaml` straight from the published npm package via
[unpkg.com](https://unpkg.com) (serves npm package contents over plain
HTTP, works from any ecosystem, no Node required) — swap that task for a
real Maven dependency once `@dpdpguard:contract` ships to Maven Central.

- The `org.openapi.generator` Gradle plugin generates a Kotlin+OkHttp+Moshi
  client (`build/generated/openapi`, gitignored, regenerated every build) —
  never hand-edited.
- `ApiErrorCode` in `src/main/kotlin/.../ApiErrors.kt` hand-mirrors the
  ADR-002 error catalog (there's no JVM-native way to load an arbitrary
  JSON file from a published npm package at runtime); `ApiErrorCodeTest`
  fetches `conformance/error-catalog.json` from unpkg at test time and
  asserts they match.
- `AuditHashTest` fetches `conformance/audit-hash-vectors.json` from unpkg
  and runs it as a required test gate (ADR-002 D5) — a failure means
  `AuditHash.kt` has drifted from `convex/lib/auditHash.ts`.

## Usage

```kotlin
val client = DpdpGuardClient(
    baseUrl = "https://<your-deployment>.convex.site",
    apiKey = System.getenv("DPDP_SERVICE_API_KEY"), // for brokerToken() only
)

client.brokerToken(externalId) // stores the token for subsequent calls
val requests = client.listDsrRequests()
client.createDsrRequest(organizationId, CreateDsrRequestRequest.Type.erasure)

// Public reads need no auth.
val org = client.getOrganization("acme")
val notices = client.getNotices(org.orgId)

// Verify an inbound webhook (convex/webhooks.ts's X-DPDP-Signature header).
val ok = WebhookVerifier.verify(webhookSecret, rawBody, signatureHeader)
```

Every non-2xx response throws a `DpdpGuardApiError` with a `code`
(`ApiErrorCode`) from the ADR-002 error catalog and the HTTP `status`.

Unlike the raw generated `DefaultApi` (which stores auth as JVM-wide
companion-object statics — unsafe for a multi-tenant server process),
`DpdpGuardClient` scopes auth to its own OkHttp interceptor closure, so
concurrent client instances never race on each other's tokens.

## Build

```bash
./gradlew build
```

**Verification status:** this repo's build/test pipeline has been
validated by generating the Kotlin client standalone (`openapi-generator-cli`
against the published contract) and inspecting the generated method
signatures/model fields the wrapper code and tests are written against —
but a full `./gradlew build` has **not** been run in CI or on a developer
machine yet. The sandboxed environment this was authored in cannot run
Gradle at all (`java.io.IOException: Unable to establish loopback
connection` — a JVM/OS-level restriction on this specific host, reproduced
even with a bare minimal Gradle project, unrelated to this project's
config). Run `./gradlew build` in a normal environment (or let CI run it)
before depending on this package; fix forward from whatever surfaces.
