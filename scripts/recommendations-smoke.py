#!/usr/bin/env python3
"""End-to-end HTTP regression against a running gateway (Python standard library)."""
import datetime
import json
import os
import time
import urllib.error
import urllib.request
import uuid

BASE_URL = os.environ.get("EWM_BASE_URL", "http://localhost:8080").rstrip("/")


class HttpFailure(AssertionError):
    def __init__(self, message, status):
        super().__init__(message)
        self.status = status


def request(method, path, body=None, user=None, status=200):
    headers = {"Content-Type": "application/json"}
    if user is not None:
        headers["X-EWM-USER-ID"] = str(user)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        payload = response.read().decode()
        if response.status != status:
            raise HttpFailure(f"{method} {path}: expected {status}, got {response.status}: {payload}", response.status)
        return json.loads(payload) if payload else None


def eventually(check, description):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except HttpFailure as error:
            if error.status not in (502, 503):
                raise
        time.sleep(0.5)
    raise AssertionError("Timed out: " + description)


def main():
    eventually(lambda: isinstance(request("GET", "/admin/users"), list), "user-service route in Eureka")
    eventually(lambda: isinstance(request("GET", "/categories"), list), "event-service route in Eureka")
    suffix = uuid.uuid4().hex
    users = [request("POST", "/admin/users", {"name": role, "email": f"{role}-{suffix}@example.org"}, status=201)["id"]
             for role in ("owner", "alice", "bob", "carol", "cold")]
    owner, alice, bob, carol, cold = users
    category = request("POST", "/admin/categories", {"name": "smoke-" + suffix}, status=201)["id"]
    date = (datetime.datetime.now() + datetime.timedelta(days=3)).strftime("%Y-%m-%d %H:%M:%S")
    events = []
    for title in ("A", "B", "C"):
        event = request("POST", f"/users/{owner}/events", {
            "annotation": "Smoke test annotation " + suffix,
            "description": "Smoke test description " + suffix,
            "title": "Smoke event " + title, "category": category, "eventDate": date,
            "location": {"lat": 55.0, "lon": 37.0}, "paid": False,
            "participantLimit": 0, "requestModeration": False,
        }, status=201)
        event_id = event["id"]
        request("PATCH", f"/admin/events/{event_id}", {"stateAction": "PUBLISH_EVENT"})
        events.append(event_id)
    a, b, c = events
    for user, event in ((alice, a), (alice, a), (bob, a), (bob, b), (carol, a), (carol, c)):
        card = eventually(lambda: request("GET", f"/events/{event}", user=user), "Collector discovery")
        assert "rating" in card and "views" not in card
    request("GET", f"/events/{a}", status=400)
    request("GET", f"/events/{a}", user="invalid", status=400)
    assert request("GET", "/events/recommendations", user=cold) == []
    request("PUT", f"/events/{a}/like", user=alice, status=400)

    # Read ratings without generating any additional viewing actions.
    def ratings():
        return {event["id"]: event["rating"] for event in request("GET", f"/admin/events?users={owner}&size=100")}

    eventually(lambda: abs(ratings().get(a, 0) - 1.2) < 1e-6, "repeated view stays at 0.4 per user")
    eventually(lambda: {item["id"] for item in request("GET", "/events/recommendations", user=alice)} == {b, c},
               "recommendations exclude A and contain B/C")
    # GET /events must not record a view, regardless of the header.
    request("GET", f"/events?categories={category}", user=cold)
    assert request("GET", "/events/recommendations", user=cold) == []
    registration = request("POST", f"/users/{alice}/requests?eventId={a}", status=201)
    assert registration["status"] == "CONFIRMED"
    eventually(lambda: abs(ratings().get(a, 0) - 1.6) < 1e-6, "registration replaces view with 0.8")
    request("PUT", f"/events/{a}/like", user=alice, status=204)
    request("PUT", f"/events/{a}/like", user=alice, status=204)
    request("GET", f"/events/{a}", user=alice)
    eventually(lambda: abs(ratings().get(a, 0) - 1.8) < 1e-6, "repeated like and later view keep maximal weight")
    final = ratings()
    assert abs(final[b] - 0.4) < 1e-6 and abs(final[c] - 0.4) < 1e-6
    compilation = request("POST", "/admin/compilations", {"title": "Smoke compilation", "pinned": False, "events": [a]}, status=201)
    assert abs(compilation["events"][0]["rating"] - 1.8) < 1e-6
    print("PASS: viewing, no list telemetry, registration, like eligibility, double rating, recommendations, headers, compilation rating")
    print(json.dumps({"users": users, "events": events, "category": category, "ratings": final}, ensure_ascii=False))


if __name__ == "__main__":
    main()
