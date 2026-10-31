#!/usr/bin/env python3
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

GATEWAY = os.getenv("GATEWAY_URL", "http://localhost:8080")
KEYCLOAK = os.getenv("KEYCLOAK_URL", "http://localhost:8180")
REALM = os.getenv("KEYCLOAK_REALM", "podcast-demo")
CLIENT = os.getenv("KEYCLOAK_CLIENT_ID", "podcast-dashboard")
TARGET_EXCERPT = "f0f4e4bf-dff7-433c-91be-b8b151c47e42"
OTHER_EXCERPT = "6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410"
EPISODE = "2a3f078d-471e-46bb-a4b7-23ad33300960"
PRODUCER_SUBJECT = "11111111-1111-1111-1111-111111111111"
SMOKE_APPROVED_TITLE = "[smoke] Approved excerpt request"
SMOKE_REJECTED_TITLE = "[smoke] Rejected excerpt request"
SMOKE_PENDING_TITLE = "[smoke] Pending demo request"
SMOKE_REJECTION_REASON = "The window overlaps claims that need editorial review."
SMOKE_TASK_NOTES = "Smoke check: producer recorded completion notes without changing the task status."
SMOKE_EVIDENCE = "https://example.com/signal/evidence/smoke"


def request(method, url, token=None, body=None, headers=None):
    data = None
    request_headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    if headers:
        request_headers.update(headers)
    req = urllib.request.Request(url, data=data, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as res:
            raw = res.read().decode("utf-8")
            return res.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as err:
        raw = err.read().decode("utf-8")
        try:
            payload = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            payload = raw
        return err.code, payload


def token(username, password):
    form = urllib.parse.urlencode(
        {
            "grant_type": "password",
            "client_id": CLIENT,
            "username": username,
            "password": password,
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        f"{KEYCLOAK}/realms/{REALM}/protocol/openid-connect/token",
        data=form,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=15) as res:
        return json.loads(res.read().decode("utf-8"))["access_token"]


def token_with_retry(username, password):
    deadline = time.time() + 90
    last = None
    while time.time() < deadline:
        try:
            return token(username, password)
        except Exception as exc:
            last = exc
        time.sleep(2)
    raise AssertionError(f"Keycloak did not issue a token: {last}")


def wait_for_gateway():
    deadline = time.time() + 90
    while time.time() < deadline:
        try:
            status, _ = request("GET", f"{GATEWAY}/actuator/health")
            if status == 200:
                return
        except Exception:
            pass
        time.sleep(2)
    raise AssertionError("Gateway did not become healthy within 90 seconds")


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def wait_for_release(guest_token):
    deadline = time.time() + 90
    last = None
    while time.time() < deadline:
        try:
            status, payload = request(
                "GET", f"{GATEWAY}/api/releases/me", token=guest_token
            )
            if status == 200:
                return payload
            last = (status, payload)
        except Exception as exc:
            last = ("connection", str(exc))
        time.sleep(2)
    raise AssertionError(f"release route did not become ready: {last}")


def wait_for(predicate, message, timeout=60, interval=1):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            value = predicate()
            if value:
                return value
        except Exception as exc:
            last = exc
        time.sleep(interval)
    raise AssertionError(f"{message}: {last}")


def submit_request(token, title, start_ms, end_ms, description=None):
    body = {
        "episodeId": EPISODE,
        "title": title,
        "startMs": start_ms,
        "endMs": end_ms,
    }
    if description:
        body["description"] = description
    return request("POST", f"{GATEWAY}/api/excerpts/requests", token=token, body=body)


def my_requests(token):
    status, requests = request("GET", f"{GATEWAY}/api/excerpts/requests/me", token=token)
    check(status == 200, f"guest request list returned {status}: {requests}")
    return requests


def notifications(token, unread_only=False):
    suffix = "?unreadOnly=true" if unread_only else ""
    status, items = request("GET", f"{GATEWAY}/api/notifications{suffix}", token=token)
    check(status == 200, f"notification list returned {status}: {items}")
    return items


def wait_for_notification(token, event_type, related_id=None, timeout=60):
    def find():
        return next(
            (
                item
                for item in notifications(token)
                if item["eventType"] == event_type
                and (related_id is None or item.get("relatedId") == related_id)
            ),
            None,
        )

    return wait_for(find, f"{event_type} notification was not delivered")


def main():
    print("1/13 waiting for gateway health")
    wait_for_gateway()

    print("2/13 authenticating guest and producer through Keycloak")
    guest = token_with_retry("ava", "guest123")
    producer = token_with_retry("priya", "producer123")

    print("3/13 reading guest release state")
    releases = wait_for_release(guest)
    release = next(
        (item for item in releases if any(scope["excerptId"] == TARGET_EXCERPT for scope in item["scopes"])),
        None,
    )
    check(release is not None, "seed release for the target excerpt was not found")
    target = next(scope for scope in release["scopes"] if scope["excerptId"] == TARGET_EXCERPT)
    other = next(scope for scope in release["scopes"] if scope["excerptId"] == OTHER_EXCERPT)
    check(other["status"] == "ACTIVE", "the control excerpt must start ACTIVE")

    already_withdrawn = target["status"] == "WITHDRAWN"
    if already_withdrawn:
        print("   target excerpt is already withdrawn; verifying idempotent state")
    else:
        print("4/13 withdrawing exactly one excerpt")
        correlation_id = f"demo-smoke-{uuid.uuid4()}"
        status, accepted = request(
            "POST",
            f"{GATEWAY}/api/releases/{release['id']}/scopes/{TARGET_EXCERPT}/withdrawals",
            token=guest,
            body={"reason": "Guest withdrew consent for this excerpt (smoke test)."},
            headers={"X-Correlation-Id": correlation_id},
        )
        check(status == 202, f"withdrawal returned {status}: {accepted}")
        check(accepted.get("status") == "WITHDRAWN_ACCEPTED", "unexpected withdrawal status")
        event_id = accepted["eventId"]

    if already_withdrawn:
        print("4/13 replaying withdrawal to prove idempotency")
        status, accepted = request(
            "POST",
            f"{GATEWAY}/api/releases/{release['id']}/scopes/{TARGET_EXCERPT}/withdrawals",
            token=guest,
            body={"reason": "Idempotency replay (smoke test)."},
        )
        check(status == 202, f"replayed withdrawal returned {status}: {accepted}")
        event_id = accepted["eventId"]

    print("5/13 waiting for the distribution projection to create exactly three tasks")
    deadline = time.time() + 60
    tasks = []
    status = None
    while time.time() < deadline:
        status, tasks = request(
            "GET",
            f"{GATEWAY}/api/distribution/tasks?excerptId={TARGET_EXCERPT}",
            token=guest,
        )
        if status in (500, 502, 503, 504):
            # A recreated service can leave a stale Eureka entry for a few seconds.
            time.sleep(1)
            continue
        check(status == 200, f"task query returned {status}: {tasks}")
        if len(tasks) == 3:
            break
        time.sleep(1)
    check(status == 200, f"task query returned {status}: {tasks}")

    check(len(tasks) == 3, f"expected exactly 3 tasks, got {len(tasks)}: {tasks}")
    check(all(task["eventId"] == event_id for task in tasks), "tasks do not belong to the accepted event")
    check(all(task["taskType"] == "EXCERPT_REMOVAL" for task in tasks), "unexpected task type")
    providers = {task["provider"] for task in tasks}
    check(providers == {"YouTube", "Spotify", "TikTok"}, f"unexpected providers: {providers}")

    print("6/13 proving the other excerpt and the release were not withdrawn")
    status, other_tasks = request(
        "GET",
        f"{GATEWAY}/api/distribution/tasks?excerptId={OTHER_EXCERPT}",
        token=guest,
    )
    check(status == 200, f"control task query returned {status}: {other_tasks}")
    check(other_tasks == [], f"control excerpt unexpectedly has tasks: {other_tasks}")

    status, refreshed = request("GET", f"{GATEWAY}/api/releases/{release['id']}", token=guest)
    check(status == 200, f"release read returned {status}: {refreshed}")
    refreshed_target = next(
        scope for scope in refreshed["scopes"] if scope["excerptId"] == TARGET_EXCERPT
    )
    refreshed_other = next(
        scope for scope in refreshed["scopes"] if scope["excerptId"] == OTHER_EXCERPT
    )
    check(refreshed_target["status"] == "WITHDRAWN", "target scope did not become WITHDRAWN")
    check(refreshed_other["status"] == "ACTIVE", "control scope was unexpectedly withdrawn")
    check(refreshed["status"] == "ACTIVE", "release must remain ACTIVE while another scope is active")

    print("7/13 verifying producer visibility of the three removal tasks")
    status, edits = request(
        "GET",
        f"{GATEWAY}/api/distribution/edits?episodeId={EPISODE}",
        token=producer,
    )
    check(status == 200, f"edit query returned {status}: {edits}")
    event_tasks = [
        task for edit in edits for task in edit["tasks"] if task["eventId"] == event_id
    ]
    check(len(event_tasks) == 3, f"producer sees {len(event_tasks)} event tasks, expected 3")

    print("8/13 proving a denied producer-only call")
    status, denied = request(
        "POST",
        f"{GATEWAY}/api/excerpts/episodes",
        token=guest,
        body={"title": "Guest must not create an episode"},
    )
    check(status == 403, f"guest producer-only call returned {status}, expected 403")
    check(
        isinstance(denied, dict) and denied.get("code") == "FORBIDDEN",
        f"unexpected denied response body: {denied}",
    )

    print("9/13 validating and submitting an excerpt request as the guest")
    status, available = request(
        "GET", f"{GATEWAY}/api/excerpts/episodes/available", token=guest
    )
    check(status == 200, f"available episodes returned {status}: {available}")
    check(
        any(episode["id"] == EPISODE for episode in available),
        "seed episode is not listed as available",
    )

    status, invalid = submit_request(guest, "Invalid window (smoke)", 5000, 5000)
    check(status == 400, f"equal timestamps returned {status}: {invalid}")
    check(
        isinstance(invalid, dict) and invalid.get("code"),
        f"invalid window response lacked a problem code: {invalid}",
    )
    status, negative = submit_request(guest, "Negative start (smoke)", -1, 1000)
    check(status == 400, f"negative start returned {status}: {negative}")

    requests = my_requests(guest)
    approved = next((item for item in requests if item["title"] == SMOKE_APPROVED_TITLE), None)
    if approved is None:
        status, approved = submit_request(
            guest,
            SMOKE_APPROVED_TITLE,
            182000,
            188000,
            "Smoke test clip for the request and approval flow.",
        )
        check(status == 201, f"valid request returned {status}: {approved}")
        check(approved["status"] == "PENDING", "new request must start PENDING")
        check(approved["excerptId"] is None, "a pending request must not carry an excerpt")
        print(f"   submitted request {approved['id']} (PENDING, no excerpt yet)")
    else:
        check(approved["status"] == "APPROVED", f"expected the smoke request approved: {approved}")
        check(approved["excerptId"], "an approved smoke request must reference its excerpt")
        print(f"   reusing approved request {approved['id']}")

    print("10/13 reviewing and approving the request as the producer")
    status, guest_queue = request("GET", f"{GATEWAY}/api/excerpts/requests", token=guest)
    check(status == 403, f"guest producer queue returned {status}, expected 403")

    if approved["status"] == "PENDING":
        status, pending_queue = request(
            "GET", f"{GATEWAY}/api/excerpts/requests?status=PENDING", token=producer
        )
        check(status == 200, f"producer queue returned {status}: {pending_queue}")
        check(
            any(item["id"] == approved["id"] for item in pending_queue),
            "the submitted request is missing from the producer queue",
        )

        status, denied = request(
            "POST",
            f"{GATEWAY}/api/excerpts/requests/{approved['id']}/approve",
            token=guest,
        )
        check(status == 403, f"guest approval returned {status}, expected 403")

        status, approved = request(
            "POST",
            f"{GATEWAY}/api/excerpts/requests/{approved['id']}/approve",
            token=producer,
        )
        check(status == 200, f"approval returned {status}: {approved}")
        check(approved["status"] == "APPROVED", "approval did not approve the request")
        check(approved["excerptId"], "approval did not create an excerpt")
        print(f"   approved request and created excerpt {approved['excerptId']}")
    else:
        status, conflict = request(
            "POST",
            f"{GATEWAY}/api/excerpts/requests/{approved['id']}/approve",
            token=producer,
        )
        check(status == 409, f"re-approval returned {status}, expected 409: {conflict}")

    print("11/13 rejecting a second request with an optional reason")
    requests = my_requests(guest)
    rejected = next((item for item in requests if item["title"] == SMOKE_REJECTED_TITLE), None)
    if rejected is None:
        status, submitted = submit_request(
            guest,
            SMOKE_REJECTED_TITLE,
            240000,
            246000,
            "This window is intentionally rejected to exercise the decision flow.",
        )
        check(status == 201, f"rejection candidate returned {status}: {submitted}")
        status, rejected = request(
            "POST",
            f"{GATEWAY}/api/excerpts/requests/{submitted['id']}/reject",
            token=producer,
            body={"reason": SMOKE_REJECTION_REASON},
        )
        check(status == 200, f"rejection returned {status}: {rejected}")
        print(f"   rejected request {rejected['id']}")
    else:
        print(f"   reusing rejected request {rejected['id']}")
    check(rejected["status"] == "REJECTED", "request was not rejected")
    check(rejected["rejectionReason"] == SMOKE_REJECTION_REASON, "rejection reason was not stored")
    check(rejected["excerptId"] is None, "a rejected request must not reference an excerpt")

    status, conflict = request(
        "POST",
        f"{GATEWAY}/api/excerpts/requests/{rejected['id']}/reject",
        token=producer,
        body={"reason": "Second decision"},
    )
    check(status == 409, f"re-deciding returned {status}, expected 409: {conflict}")

    print("12/13 managing distribution tasks as the producer")
    status, all_tasks = request("GET", f"{GATEWAY}/api/distribution/tasks", token=producer)
    check(status == 200, f"producer task list returned {status}: {all_tasks}")
    check(len(all_tasks) >= 3, f"producer task list returned {len(all_tasks)} tasks")

    status, denied = request("GET", f"{GATEWAY}/api/distribution/tasks", token=guest)
    check(status == 403, f"guest cross-episode task list returned {status}, expected 403")

    status, youtube_tasks = request(
        "GET", f"{GATEWAY}/api/distribution/tasks?provider=YouTube", token=producer
    )
    check(status == 200, f"provider filter returned {status}: {youtube_tasks}")
    check(
        youtube_tasks and all(task["provider"] == "YouTube" for task in youtube_tasks),
        f"provider filter leaked other platforms: {youtube_tasks}",
    )

    status, open_tasks = request(
        "GET", f"{GATEWAY}/api/distribution/tasks?status=OPEN", token=producer
    )
    check(status == 200, f"status filter returned {status}: {open_tasks}")
    check(
        open_tasks and all(task["status"] == "OPEN" for task in open_tasks),
        f"status filter leaked other statuses: {open_tasks}",
    )

    task = open_tasks[0]
    status, denied = request(
        "PATCH",
        f"{GATEWAY}/api/distribution/tasks/{task['id']}",
        token=guest,
        body={"notes": "Guest must not update tasks"},
    )
    check(status == 403, f"guest task update returned {status}, expected 403")

    status, updated = request(
        "PATCH",
        f"{GATEWAY}/api/distribution/tasks/{task['id']}",
        token=producer,
        body={"notes": SMOKE_TASK_NOTES, "evidenceUrl": SMOKE_EVIDENCE},
    )
    check(status == 200, f"task metadata update returned {status}: {updated}")
    check(updated["status"] == "OPEN", "metadata-only update must keep the status unchanged")
    check(updated["notes"] == SMOKE_TASK_NOTES, "completion notes were not stored")
    check(updated["evidenceUrl"] == SMOKE_EVIDENCE, "evidence URL was not stored")
    check(updated["updatedBy"] == PRODUCER_SUBJECT, "updatedBy did not record the producer")

    status, detail = request(
        "GET", f"{GATEWAY}/api/distribution/tasks/{task['id']}", token=guest
    )
    check(status == 200, f"guest task detail returned {status}: {detail}")
    check(detail["evidenceUrl"] == SMOKE_EVIDENCE, "task detail did not include evidence")

    status, conflict = request(
        "PATCH",
        f"{GATEWAY}/api/distribution/tasks/{task['id']}",
        token=producer,
        body={"status": "DONE"},
    )
    check(status == 409, f"OPEN -> DONE returned {status}, expected 409: {conflict}")

    status, invalid = request(
        "PATCH",
        f"{GATEWAY}/api/distribution/tasks/{task['id']}",
        token=producer,
        body={"evidenceUrl": "ftp://example.com/evidence"},
    )
    check(status == 400, f"invalid evidence URL returned {status}: {invalid}")

    print("13/13 verifying user-scoped in-app notifications")
    approval_notification = wait_for_notification(
        guest, "REQUEST_APPROVED", approved["id"]
    )
    rejection_notification = wait_for_notification(
        guest, "REQUEST_REJECTED", rejected["id"]
    )
    wait_for_notification(producer, "REQUEST_SUBMITTED", approved["id"])
    wait_for_notification(producer, "CONSENT_WITHDRAWN")
    print("   guests and producers received their own request and withdrawal events")

    status, foreign = request(
        "PATCH",
        f"{GATEWAY}/api/notifications/{approval_notification['id']}/read",
        token=producer,
    )
    check(status == 404, f"cross-user notification update returned {status}, expected 404")

    status, marked = request(
        "PATCH",
        f"{GATEWAY}/api/notifications/{approval_notification['id']}/read",
        token=guest,
    )
    check(status == 200, f"mark-read returned {status}: {marked}")
    check(marked["read"] is True, "mark-read did not set the notification to read")

    status, page = request("POST", f"{GATEWAY}/api/notifications/read-all", token=producer)
    check(status == 200, f"mark-all returned {status}: {page}")
    check(isinstance(page, dict) and isinstance(page.get("updated"), int), "unexpected mark-all body")
    check(notifications(producer, unread_only=True) == [], "producer still has unread notifications")

    print("   leaving one pending request so the producer queue stays demo-ready")
    requests = my_requests(guest)
    pending_demo = next((item for item in requests if item["title"] == SMOKE_PENDING_TITLE), None)
    if pending_demo is None:
        status, pending_demo = submit_request(
            guest,
            SMOKE_PENDING_TITLE,
            300000,
            306000,
            "Pending request kept for the live producer demo.",
        )
        check(status == 201, f"pending demo request returned {status}: {pending_demo}")
    check(pending_demo["status"] == "PENDING", "demo request must remain pending")
    wait_for_notification(producer, "REQUEST_SUBMITTED", pending_demo["id"])

    print("\nPASS")
    print(f"  event:      {event_id}")
    print(f"  tasks:      {len(tasks)} EXCERPT_REMOVAL ({', '.join(sorted(providers))})")
    print("  control:    control excerpt has 0 tasks and stays ACTIVE")
    print("  release:    still ACTIVE because another scope remains active")
    print(f"  request:    {approved['id']} APPROVED -> excerpt {approved['excerptId']}")
    print(f"  rejection:  {rejected['id']} REJECTED with reason")
    print(f"  task:       {task['id']} notes and evidence stored by {updated['updatedBy']}")
    print("  notifications: user-scoped, mark-read and mark-all verified")
    print(f"  demo queue: pending request {pending_demo['id']} left for the producer demo")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"\nFAIL: {exc}", file=sys.stderr)
        sys.exit(1)
