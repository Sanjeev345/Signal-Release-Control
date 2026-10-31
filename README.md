# Signal · Podcast Excerpt Release Withdrawal

A working SOA demo for **excerpt-scoped consent withdrawal**. A guest can revoke
one tagged excerpt without withdrawing the rest of the episode. The release
service commits the decision and an outbox event, Kafka carries
`GuestExcerptWithdrawn` to the distribution service, and distribution creates
exactly one `EXCERPT_REMOVAL` task for every edit that actually carries that
excerpt.

In the seeded demo, the target excerpt is present on the YouTube, Spotify, and
TikTok edits, so a withdrawal creates **exactly three tasks**. The control
excerpt is on the YouTube, Social, TikTok, and Website edits, so its scope stays
**ACTIVE** and it creates **zero tasks**.

On top of that core the demo now covers the full consent loop:

1. **Guest excerpt requests** — a guest selects an available episode, picks a
   window, and submits a title/description. Requests stay `PENDING` until a
   producer decides; submission never creates an excerpt or a consent scope.
2. **Producer request management** — producers see the pending queue, approve
   (creating the excerpt through the existing excerpt domain service) or reject
   with an optional reason.
3. **Guest consent dashboard** — releases, excerpt-scoped consents, requests,
   withdrawals, and approved excerpts that are not yet part of a release.
4. **Distribution task management** — producers filter tasks by episode,
   platform, status, and text; move `OPEN → IN_PROGRESS → DONE`; and record
   completion notes, an evidence URL, who updated the task, and when.
5. **In-app notifications** — producers hear about new requests and consent
   withdrawals; guests hear about approvals, rejections, and task completion.
   Notifications are stored per recipient with read/unread state.

## Demo at a glance

| Item | Value |
| --- | --- |
| Dashboard | <http://localhost:5173> |
| Gateway | <http://localhost:8080> |
| Eureka | <http://localhost:8761> |
| Keycloak | <http://localhost:8180> (admin / admin) |
| Guest login | `ava` / `guest123` |
| Producer login | `priya` / `producer123` |
| Kafka topic | `soa.s14.t04.events` |
| Notification topic | `soa.s14.t04.notifications` |

Seeded records:

| Record | ID / code |
| --- | --- |
| Episode | `2a3f078d-471e-46bb-a4b7-23ad33300960` (`ep-2026-001`, "Designing for Trust") |
| Target excerpt | `f0f4e4bf-dff7-433c-91be-b8b151c47e42` (`ex-ava-020500`) |
| Control excerpt | `6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410` (`ex-ava-341000`) |
| Release | `d76025ba-7021-47f0-ab83-4749a581fd61` |
| Target scope | `4ace15d0-2e39-488e-8bc1-4084a08d557a` |
| Control scope | `9b1e2f3a-4c5d-4e6f-8a7b-1c2d3e4f5a60` |
| YouTube edit | `e0000000-0000-4000-8000-000000000001` (target + control) |
| Spotify edit | `e0000000-0000-4000-8000-000000000002` (target only) |
| Social edit | `e0000000-0000-4000-8000-000000000003` (control only) |
| TikTok edit | `e0000000-0000-4000-8000-000000000004` (target + control) |
| Website edit | `e0000000-0000-4000-8000-000000000005` (control only) |

## Architecture

```text
                         ┌──────────────────────┐
                         │  React dashboard     │
                         │  producer / guest    │
                         └──────────┬───────────┘
                                    │ OIDC token
                    ┌───────────────▼────────────────┐
                    │ Keycloak 26 · podcast-demo     │
                    │ roles: podcastProducer, guest  │
                    └───────────────┬────────────────┘
                                    │ JWT
                    ┌───────────────▼────────────────┐
                    │ Spring Cloud Gateway :8080     │
                    │ route roles + correlation id   │
                    └───┬───────────┬───────────┬────┘
                        │           │           │
          ┌─────────────▼──┐  ┌─────▼─────────┐ │
          │ excerpt-service│  │ release-svc   │ │
          │ :8081          │  │ :8082         │ │
          │ episodes/tags  │  │ releases/     │ │
          │                │  │ scopes/outbox │ │
          └───────┬────────┘  └──────┬────────┘ │
                  │                  │          │
                  │        ┌─────────▼───────┐  │
                  │        │ Kafka           │  │
                  │        │ withdrawal v1   │  │
                  │        └─────────┬───────┘  │
                  │                  │          │
          ┌───────▼──────────────────▼──────────▼────┐
          │ distribution-task-service :8083           │
          │ idempotent consumer + task projection     │
          └──────────────────┬────────────────────────┘
                             │
             ┌───────────────┴────────────────┐
             │                                │
      ┌──────▼──────┐                  ┌──────▼──────┐
      │ excerpt_db  │                  │ release_db  │
      └─────────────┘                  └─────────────┘
                                      ┌──────▼──────────┐
                                      │ distribution_db │
                                      └─────────────────┘
```

Every service owns its schema. A separate PostgreSQL database is created for
each service at container start; there are no cross-database foreign keys. The
release and distribution services keep small local read-model projections
(`produced_episode`, `excerpt_reference`, `episode_owner`, `excerpt_guest`) for
record-level authorization. Those projections are seeded for the demo and are
the documented speed trade-off; in production they would be maintained from
domain events.

Detailed flows and trade-offs live in [docs/architecture.md](docs/architecture.md).
The HTTP contract is in [contracts/openapi/openapi.yaml](contracts/openapi/openapi.yaml).

## Quick start

Prerequisite: Docker with Compose v2.

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

First start builds the backend image and the dashboard, and pulls PostgreSQL,
Kafka, and Keycloak. Give it a minute, then run the end-to-end check:

```bash
./scripts/demo-smoke.sh
```

Expected result:

```text
PASS
  event:      <uuid>
  tasks:      3 EXCERPT_REMOVAL (Spotify, TikTok, YouTube)
  control:    control excerpt has 0 tasks and stays ACTIVE
  release:    still ACTIVE because another scope remains active
  request:    <uuid> APPROVED -> excerpt <uuid>
  rejection:  <uuid> REJECTED with reason
  task:       <uuid> notes and evidence stored by <producer-id>
  notifications: user-scoped, mark-read and mark-all verified
  demo queue: pending request <uuid> left for the producer demo
```

The smoke run is repeatable: it reuses the existing approved and rejected
requests, re-checks `409` on a second decision, and leaves one pending request
so the producer queue is demo-ready. It does not drop databases or reset the
PostgreSQL volume.

Open <http://localhost:5173>. Use **Ava Rao** for the guest flow or **Priya
Shah** for the producer view. The guest card for `ex-ava-020500` has a
**Withdraw excerpt** action. Enter a reason, confirm, and the removal queue
appears with the YouTube, Spotify, and TikTok tasks. The control excerpt
remains active.

To reset the demo data:

```bash
docker compose down -v
docker compose up --build -d
```

## Local development

The Maven build targets Java 21 and runs on JDK 21 or newer. Lombok is pinned to
1.18.48 so annotation processing works on current JDKs (older Boot-managed
Lombok releases crash on JDK 27 javac). No environment setup is needed; if you
prefer a specific toolchain, point `JAVA_HOME` at it as usual.

Infrastructure only:

```bash
docker compose up -d postgres kafka kafka-init keycloak
```

Run the services in separate terminals from the repository root:

```bash
./mvnw -pl discovery-server spring-boot:run
./mvnw -pl podcast-excerpt-service spring-boot:run
./mvnw -pl guest-release-service spring-boot:run
./mvnw -pl distribution-task-service spring-boot:run
./mvnw -pl api-gateway spring-boot:run
```

The service defaults expect PostgreSQL on `localhost:5433`, Kafka on
`localhost:9092`, Keycloak on `localhost:8180`, and Eureka on `localhost:8761`.
The compose file exposes all of those ports (PostgreSQL is mapped to 5433 to
avoid clashing with a host-installed server). The Keycloak import only runs on a
fresh Keycloak container, so use `docker compose up -d --force-recreate keycloak`
after editing the realm.

Frontend:

```bash
cd web-dashboard
npm install
npm run dev
```

`VITE_API_URL`, `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, and
`VITE_KEYCLOAK_CLIENT_ID` can be supplied in `.env`; the defaults match the
compose setup.

## Tests

```bash
./mvnw clean verify
```

Current suite: 76 tests, including contract-schema validation, domain
constraints, security authorization (including audience rejection), REST
contract tests, excerpt-request validation and authorization, notification
scoping and listener behavior, task transition validation, and Kafka +
PostgreSQL Testcontainers integration tests that assert exactly three tasks,
replay safety, and dead-letter routing for malformed events.

Frontend checks:

```bash
cd web-dashboard
npm run typecheck
npm run build
```

## Endpoints

All routes go through the gateway and require a Keycloak bearer token.

| Method | Path | Role |
| --- | --- | --- |
| `GET` | `/api/excerpts/episodes` | producer |
| `POST` | `/api/excerpts/episodes` | producer |
| `GET` | `/api/excerpts/episodes/available` | guest |
| `GET` | `/api/excerpts/episodes/{episodeId}` | producer |
| `GET` | `/api/excerpts/episodes/{episodeId}/excerpts` | producer or owning guest |
| `POST` | `/api/excerpts/episodes/{episodeId}/excerpts` | producer |
| `POST` | `/api/excerpts/requests` | guest |
| `GET` | `/api/excerpts/requests/me` | guest |
| `GET` | `/api/excerpts/requests` | producer (own episodes) |
| `GET` | `/api/excerpts/requests/{requestId}` | owning guest or producer |
| `POST` | `/api/excerpts/requests/{requestId}/approve` | producer (own episodes) |
| `POST` | `/api/excerpts/requests/{requestId}/reject` | producer (own episodes) |
| `GET` | `/api/releases?episodeId=` | producer |
| `POST` | `/api/releases` | producer |
| `GET` | `/api/releases/me` | guest |
| `GET` | `/api/releases/{releaseId}` | owning guest or producer |
| `GET` | `/api/releases/{releaseId}/audit` | owning guest or producer |
| `POST` | `/api/releases/{releaseId}/scopes/{excerptId}/withdrawals` | guest |
| `GET` | `/api/distribution/edits?episodeId=` | producer |
| `GET` | `/api/distribution/tasks?excerptId=` | owning guest or producer |
| `GET` | `/api/distribution/tasks?episodeId=&provider=&status=` | producer |
| `GET` | `/api/distribution/tasks/{taskId}` | owning guest or producer |
| `PATCH` | `/api/distribution/tasks/{taskId}` | producer (notes, evidence, status) |
| `PATCH` | `/api/distribution/tasks/{taskId}/status` | producer (status only) |
| `GET` | `/api/notifications` | guest or producer (own only) |
| `PATCH` | `/api/notifications/{notificationId}/read` | recipient only |
| `POST` | `/api/notifications/read-all` | guest or producer (own only) |

Withdrawal request:

```http
POST /api/releases/d76025ba-7021-47f0-ab83-4749a581fd61/scopes/f0f4e4bf-dff7-433c-91be-b8b151c47e42/withdrawals
Authorization: Bearer <guest-token>
X-Correlation-Id: demo-123
Content-Type: application/json

{"reason":"Withdrawing consent for this excerpt."}
```

`202 Accepted` response:

```json
{
  "eventId": "0f6f2c14-6b7f-4f60-a938-8c9143f052c3",
  "releaseId": "d76025ba-7021-47f0-ab83-4749a581fd61",
  "excerptId": "f0f4e4bf-dff7-433c-91be-b8b151c47e42",
  "status": "WITHDRAWN_ACCEPTED"
}
```

Errors use RFC 9457 problem details with a stable `code`, a `traceId` derived
from `X-Correlation-Id`, and a timestamp.

## Excerpt requests

A guest picks an available episode and submits:

```json
{
  "episodeId": "2a3f078d-471e-46bb-a4b7-23ad33300960",
  "title": "The trust question",
  "description": "A 40-second clip about verification.",
  "startMs": 302000,
  "endMs": 308000
}
```

Validation happens on the backend: the episode must exist and be published,
`startMs >= 0`, `endMs > startMs`, and the window must fit the episode. The
request starts `PENDING`; status changes only when a producer decides it with
`POST /api/excerpts/requests/{requestId}/approve` or `/reject` (optional
`reason`, max 2000 chars). Approving calls the same excerpt domain service used
by the producer UI, so no creation logic is duplicated. A second decision
returns `409`. Guests see only `/requests/me`; the producer list is filtered to
their owned episodes, and a request from another producer is a `404`.

Approval creates the excerpt, but it does **not** automatically grant a release
scope. The producer still grants the release through the existing flow. Until
then the guest dashboard shows the approved excerpt as **Awaiting release**.

## Distribution tasks

Tasks are created by the withdrawal consumer with status `OPEN`. Producers can
move them only along `OPEN → IN_PROGRESS → DONE`; invalid transitions return
`409`. `PATCH /api/distribution/tasks/{taskId}` accepts `status`, `notes`
(max 4000), and `evidenceUrl` (http/https, max 2048), and records
`updatedBy`/`updatedAt`. Notes and evidence can be edited after completion.

`DONE` means the producer recorded completion of the tracked task. It does not
mean an external platform API was called, and the UI and README never claim that
content was removed from Spotify, TikTok, or YouTube.

## Notifications

Notifications are stored per recipient in `excerpt_db` (table `notification`)
and exposed only through `/api/notifications`. Producers receive
`REQUEST_SUBMITTED` (written directly when a request is submitted) and
`CONSENT_WITHDRAWN` (derived from the existing `GuestExcerptWithdrawn` topic by
a listener in the excerpt service). Guests receive `REQUEST_APPROVED` and
`REQUEST_REJECTED` directly, and `TASK_COMPLETED` from
`soa.s14.t04.notifications`, published best-effort by the distribution service
and consumed idempotently (`source_event_id` dedupe). No notification outbox or
new microservice is involved.

Mark one read with `PATCH /api/notifications/{id}/read`, or all with
`POST /api/notifications/read-all`. A notification belonging to another user is
a `404`, not a `403`, so IDs cannot be probed.

## Event contract

Topic: `soa.s14.t04.events`. Key: `excerptId` (preserves per-excerpt ordering).
Schema: [contracts/kafka/guest-excerpt-withdrawn-v1.schema.json](contracts/kafka/guest-excerpt-withdrawn-v1.schema.json).

```json
{
  "eventId": "0f6f2c14-6b7f-4f60-a938-8c9143f052c3",
  "eventType": "GuestExcerptWithdrawn",
  "eventVersion": 1,
  "occurredAt": "2026-10-29T12:00:00Z",
  "correlationId": "demo-123",
  "releaseId": "d76025ba-7021-47f0-ab83-4749a581fd61",
  "releaseScopeId": "4ace15d0-2e39-488e-8bc1-4084a08d557a",
  "episodeId": "2a3f078d-471e-46bb-a4b7-23ad33300960",
  "excerptId": "f0f4e4bf-dff7-433c-91be-b8b151c47e42",
  "guestId": "22222222-2222-2222-2222-222222222222",
  "withdrawnBy": "22222222-2222-2222-2222-222222222222",
  "reason": "Withdrawing consent for this excerpt."
}
```

Headers: `eventType`, `eventVersion`, `correlationId`, `producer`.

## Design decisions

**One scope per excerpt.** `release_scope` has a unique
`(release_id, excerpt_id)` constraint. Withdrawing a scope updates only that
row. The parent release is marked `WITHDRAWN` only when no active scope remains.

**Transactional outbox.** The scope update and the outbox insert happen in one
database transaction. A scheduled publisher sends unpublished rows and records
`published_at`. A failed Kafka send increments `attempt_count` and retries on
the next poll instead of losing the withdrawal.

**At-least-once delivery, idempotent consumption.** Kafka may deliver an event
more than once. The release service serializes withdrawals on the release row,
and the distribution service claims each event atomically with
`insert into processed_event ... on conflict do nothing` before touching any
task. Combined with the outbox unique constraint and the unique
`(event_id, edit_id)` task constraint, replays return the original event id
(HTTP 202) and create zero new tasks, even for concurrent duplicate deliveries.

**Poison events are retried, then dead-lettered.** The consumer retries a
malformed or invalid event with exponential backoff and routes it to
`soa.s14.t04.events.DLT` after the final attempt. Event type, version, ids, and
reason are validated before any database write.

**Token audience is enforced.** All four Spring apps validate signature, issuer,
and the `podcast-api` audience. The Keycloak realm adds that audience through an
audience mapper on the `podcast-dashboard` client, so tokens minted for another
API get a `401`.

**Fan-out is derived, not hard-coded.** The consumer queries
`edit_excerpt` for edits containing the excerpt and filters to
`DISTRIBUTED` edits. It never assumes a provider count. The demo happens to
produce three tasks because of the seed graph; the Social and Website edits do
not carry the target excerpt.

**Roles at the edge and at the record.** The gateway rejects producer-only
routes for guests, but each service also checks ownership. A guest cannot
withdraw another guest's scope even with a valid token; a producer cannot read
another producer's episode.

**Correlation everywhere.** The gateway creates or forwards
`X-Correlation-Id`, passes it to downstream services, includes it in the Kafka
headers and event payload, and puts it in problem responses and logs.

**Failures.** Consumer retries use `@RetryableTopic` with exponential backoff
and a `.DLT` topic. Bad payloads are rejected before any write. The DLT
listener logs the key, partition, offset, and payload for triage.

## Repository layout

```text
common-contracts/            event DTO, headers, JSON-schema contract tests
discovery-server/            Eureka server
api-gateway/                 Spring Cloud Gateway, JWT roles, CORS, correlation
podcast-excerpt-service/     episodes + tagged excerpts, ownership checks
guest-release-service/       releases, scopes, withdrawal, outbox, Kafka producer
distribution-task-service/   Kafka consumer, idempotency, task projection
web-dashboard/               React + Vite producer/guest dashboard
contracts/kafka/             versioned event and 202 response schemas
contracts/openapi/           gateway HTTP contract
infra/keycloak/              realm import: users, roles, dashboard client
infra/postgres/              creates one database per service
infra/nginx/                 SPA serving config
scripts/                     end-to-end smoke check
docker-compose.yml           full local stack
Dockerfile                   multi-module backend image
```

## Known limitations

- Read-model projections are seeded rather than event-sourced. This keeps the
  demo focused on the withdrawal flow and is called out in the architecture doc.
- The dashboard uses the Keycloak password grant for a one-click demo. A
  production SPA should use Authorization Code + PKCE; the public client is
  already configured for it.
- Approval creates the excerpt but does not grant a release scope. The producer
  grants the release through the existing flow; the guest dashboard labels the
  gap **Awaiting release** rather than pretending consent exists.
- Excerpts have no title/description columns. Approval composes the request
  title and description into the excerpt transcript so the requested context is
  not lost.
- Guest `TASK_COMPLETED` notifications are best-effort supplementary events.
  The producer task record (notes, evidence, `updatedBy`, timestamps) is the
  authoritative completion record.
- No external platform API is called. `DONE` records that the producer completed
  the tracked task; the demo never claims content was removed from a platform.
- The smoke test creates records titled `[smoke]` and updates notes on one seeded
  task. It reuses those records on later runs and does not reset the database.
- The DLT listener logs dead letters; it does not provide a replay console.
- Keycloak runs in `start-dev` with demo credentials. Do not reuse this realm or
  the demo passwords outside the local environment.
