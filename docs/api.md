# API guide

The public API is served by the gateway at `http://localhost:8080`. The
authoritative machine-readable contract is
[`contracts/openapi/openapi.yaml`](../contracts/openapi/openapi.yaml).

## Authentication

Keycloak realm `podcast-demo`, public client `podcast-dashboard`.

| Demo user | Password | Realm role | Subject |
| --- | --- | --- | --- |
| `priya` | `producer123` | `podcastProducer` | `11111111-1111-1111-1111-111111111111` |
| `ava` | `guest123` | `guest` | `22222222-2222-2222-2222-222222222222` |

Get a demo token (password grant; production should use Authorization Code +
PKCE):

```bash
GUEST_TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/podcast-demo/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=podcast-dashboard' \
  -d 'username=ava' \
  -d 'password=guest123' | jq -r '.access_token')

PRODUCER_TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/podcast-demo/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=podcast-dashboard' \
  -d 'username=priya' \
  -d 'password=producer123' | jq -r '.access_token')
```

Every gateway route except actuator health requires
`Authorization: Bearer <token>`. Access tokens must carry the `podcast-api`
audience, which the realm adds through an audience mapper on the
`podcast-dashboard` client; a token issued for another audience is rejected with
`401` before routing.

## Role matrix

| Endpoint | Producer | Guest |
| --- | --- | --- |
| `GET /api/excerpts/episodes` | own episodes | 403 |
| `POST /api/excerpts/episodes` | yes | 403 |
| `GET /api/excerpts/episodes/available` | 403 | yes |
| `GET /api/excerpts/episodes/{id}` | own episode | 403 |
| `GET /api/excerpts/episodes/{id}/excerpts` | own episode | own excerpts |
| `POST /api/excerpts/episodes/{id}/excerpts` | own episode | 403 |
| `POST /api/excerpts/requests` | 403 | yes |
| `GET /api/excerpts/requests/me` | 403 | own requests |
| `GET /api/excerpts/requests` | own episodes | 403 |
| `GET /api/excerpts/requests/{id}` | own episode | own request |
| `POST /api/excerpts/requests/{id}/approve` | own episode | 403 |
| `POST /api/excerpts/requests/{id}/reject` | own episode | 403 |
| `GET /api/releases?episodeId=` | own episode | 403 |
| `POST /api/releases` | own episode | 403 |
| `GET /api/releases/me` | 403 | own releases |
| `GET /api/releases/{id}` | release for own episode | own release |
| `GET /api/releases/{id}/audit` | audit for own episode | own release |
| `POST .../withdrawals` | 403 | own release |
| `GET /api/distribution/edits?episodeId=` | own episode | 403 |
| `GET /api/distribution/tasks?excerptId=` | own episode | own excerpt |
| `GET /api/distribution/tasks?episodeId=&provider=&status=` | own episodes | 403 |
| `GET /api/distribution/tasks/{id}` | own episode | own excerpt |
| `PATCH /api/distribution/tasks/{id}` | own episode | 403 |
| `PATCH /api/distribution/tasks/{id}/status` | own episode | 403 |
| `GET /api/notifications` | own | own |
| `PATCH /api/notifications/{id}/read` | own | own |
| `POST /api/notifications/read-all` | own | own |

The gateway enforces the route-level role. The receiving service re-checks
record ownership, so a valid token with the right role is not enough to access
someone else's record.

## Producer flow

```bash
# 1. Create an episode
EPISODE_ID=$(curl -s -X POST http://localhost:8080/api/excerpts/episodes \
  -H "Authorization: Bearer $PRODUCER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Designing for Trust","publishedAt":"2026-10-20T09:00:00Z"}' \
  | jq -r '.id')

# 2. Tag an excerpt for the guest
EXCERPT_ID=$(curl -s -X POST \
  "http://localhost:8080/api/excerpts/episodes/$EPISODE_ID/excerpts" \
  -H "Authorization: Bearer $PRODUCER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "guestId":"22222222-2222-2222-2222-222222222222",
    "startMs":1205000,
    "endMs":1242000,
    "transcript":"Trust is designed in the small moments."
  }' | jq -r '.id')

# 3. Grant a release for that excerpt
RELEASE_ID=$(curl -s -X POST http://localhost:8080/api/releases \
  -H "Authorization: Bearer $PRODUCER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"guestId\":\"22222222-2222-2222-2222-222222222222\",
    \"episodeId\":\"$EPISODE_ID\",
    \"excerptIds\":[\"$EXCERPT_ID\"],
    \"scopeType\":\"PODCAST_DISTRIBUTION\"
  }" | jq -r '.id')

# 4. Inspect the release and distribution state
curl -s "http://localhost:8080/api/releases?episodeId=$EPISODE_ID" \
  -H "Authorization: Bearer $PRODUCER_TOKEN" | jq

curl -s "http://localhost:8080/api/distribution/edits?episodeId=$EPISODE_ID" \
  -H "Authorization: Bearer $PRODUCER_TOKEN" | jq
```

Review and decide guest excerpt requests:

```bash
# Pending queue for the producer's episodes
curl -s http://localhost:8080/api/excerpts/requests \
  -H "Authorization: Bearer $PRODUCER_TOKEN" | jq

# Approve: creates the excerpt through the excerpt domain service
curl -s -X POST \
  "http://localhost:8080/api/excerpts/requests/$REQUEST_ID/approve" \
  -H "Authorization: Bearer $PRODUCER_TOKEN" | jq

# Reject: reason is optional, max 2000 characters
curl -s -X POST \
  "http://localhost:8080/api/excerpts/requests/$REQUEST_ID/reject" \
  -H "Authorization: Bearer $PRODUCER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"reason":"The window includes unpublished material."}' | jq
```

Approval does not grant a release. Grant the release with `POST /api/releases`
as in step 3 before the excerpt becomes guest-visible consent.

After a withdrawal fans out, the producer advances each task through
`OPEN -> IN_PROGRESS -> DONE`:

```bash
curl -s -X PATCH \
  "http://localhost:8080/api/distribution/tasks/$TASK_ID/status" \
  -H "Authorization: Bearer $PRODUCER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"IN_PROGRESS"}' | jq

curl -s -X PATCH \
  "http://localhost:8080/api/distribution/tasks/$TASK_ID/status" \
  -H "Authorization: Bearer $PRODUCER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"DONE"}' | jq
```

Record the completion evidence in the same transition (or afterwards):

```bash
curl -s -X PATCH \
  "http://localhost:8080/api/distribution/tasks/$TASK_ID" \
  -H "Authorization: Bearer $PRODUCER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "status":"DONE",
    "notes":"Removed the clip from the platform dashboard at 14:10.",
    "evidenceUrl":"https://example.com/signal/evidence/removal-001"
  }' | jq
```

`PATCH /api/distribution/tasks/{id}` accepts partial metadata without a status,
so notes and evidence can be corrected later. `updatedBy` is taken from the JWT
subject. Skipping a step or repeating a terminal decision returns `409`. Task
completion is recorded in this service only; no external provider call is
performed in the demo.

## Guest flow

```bash
# Current releases and excerpt scopes
curl -s http://localhost:8080/api/releases/me \
  -H "Authorization: Bearer $GUEST_TOKEN" | jq

# Withdraw exactly one excerpt
curl -i -X POST \
  http://localhost:8080/api/releases/d76025ba-7021-47f0-ab83-4749a581fd61/scopes/f0f4e4bf-dff7-433c-91be-b8b151c47e42/withdrawals \
  -H "Authorization: Bearer $GUEST_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-withdrawal-001' \
  -d '{"reason":"Withdrawing consent for this excerpt."}'

# Poll the distribution projection until the tasks appear
curl -s \
  'http://localhost:8080/api/distribution/tasks?excerptId=f0f4e4bf-dff7-433c-91be-b8b151c47e42' \
  -H "Authorization: Bearer $GUEST_TOKEN" | jq
```

Expected `202` body:

```json
{
  "eventId": "0f6f2c14-6b7f-4f60-a938-8c9143f052c3",
  "releaseId": "d76025ba-7021-47f0-ab83-4749a581fd61",
  "excerptId": "f0f4e4bf-dff7-433c-91be-b8b151c47e42",
  "status": "WITHDRAWN_ACCEPTED"
}
```

The endpoint is idempotent. Calling it again for the same scope returns `202`
with the original event ID. It does not create another outbox event or another
task.

## Guest request flow

```bash
# Episodes the guest can request an excerpt from
curl -s http://localhost:8080/api/excerpts/episodes/available \
  -H "Authorization: Bearer $GUEST_TOKEN" | jq

# Submit a request; it stays PENDING until a producer decides
curl -s -X POST http://localhost:8080/api/excerpts/requests \
  -H "Authorization: Bearer $GUEST_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "episodeId":"2a3f078d-471e-46bb-a4b7-23ad33300960",
    "title":"The trust question",
    "description":"A short clip about verification.",
    "startMs":302000,
    "endMs":308000
  }' | jq

# Only the caller's requests
curl -s http://localhost:8080/api/excerpts/requests/me \
  -H "Authorization: Bearer $GUEST_TOKEN" | jq

# Notifications and read state
curl -s http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $GUEST_TOKEN" | jq
curl -s -X PATCH \
  "http://localhost:8080/api/notifications/$NOTIFICATION_ID/read" \
  -H "Authorization: Bearer $GUEST_TOKEN" | jq
curl -s -X POST http://localhost:8080/api/notifications/read-all \
  -H "Authorization: Bearer $GUEST_TOKEN" | jq
```

Backend validation: `startMs >= 0`, `endMs > startMs`, required title
(max 255), description optional (max 2000), and the episode must exist and be
published. Submission does not create an excerpt or a consent scope. A guest can
read only their own request; another guest's request returns `404`. The producer
queue returns only requests for episodes the producer owns.

## Withdrawal invariants

- `reason` is required, trimmed, and limited to 2000 characters.
- The release must belong to the JWT subject.
- The excerpt must be a scope of that exact release.
- Only that scope changes; sibling scopes remain `ACTIVE`.
- The release becomes `WITHDRAWN` only when no scope remains `ACTIVE`.
- The event is committed to the outbox in the same transaction as the scope
  change.
- Task creation is asynchronous, so `202` does not mean tasks already exist.

## Distribution semantics

Tasks are created from the actual edit graph, not from a hard-coded provider
list:

```text
for each DISTRIBUTED edit containing the withdrawn excerpt:
    create one EXCERPT_REMOVAL task
```

With the seed data, withdrawing `ex-ava-020500` creates three tasks:

| Provider | Code | Why |
| --- | --- | --- |
| YouTube | `edit-youtube-001` | carries the target excerpt |
| Spotify | `edit-spotify-001` | carries the target excerpt |
| TikTok | `edit-tiktok-001` | carries the target excerpt |

The Social and Website edits do not carry the target excerpt, so they get no
removal task. The control excerpt `ex-ava-341000` is not withdrawn, so its
scope remains `ACTIVE` and it has zero tasks.

Tasks are created with type `EXCERPT_REMOVAL`, status `OPEN`, and priority
`HIGH`. Producers manage them through `OPEN → IN_PROGRESS → DONE`. `DONE` means
the producer recorded completion of the tracked task; it does not mean an
external platform API was called.

## Error format

Errors use `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "You do not have access to this resource",
  "code": "FORBIDDEN",
  "traceId": "demo-withdrawal-001",
  "timestamp": "2026-10-29T12:00:00Z"
}
```

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | Invalid body, duplicate excerpt IDs, or bad transition |
| 400 | `VALIDATION_FAILED` | Bean-validation failure, with field errors |
| 400 | `MALFORMED_JSON` | Request body could not be parsed |
| 401 | `UNAUTHORIZED` | Missing, expired, or invalid token |
| 403 | `FORBIDDEN` | Wrong role or record belongs to another user |
| 404 | `RESOURCE_NOT_FOUND` | Unknown release, scope, episode, excerpt, request, task, or notification |
| 409 | `CONFLICT` | Request already decided, or invalid task status transition |
| 500 | `INTERNAL_ERROR` | Unexpected server failure |

Service-level validation details may be included in `errors` when a request
body fails validation.
