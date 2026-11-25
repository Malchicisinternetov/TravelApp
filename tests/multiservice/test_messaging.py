import uuid
import pytest
from conftest import api, BOT_SECRET, NOTIFICATION_SECRET

pytestmark = pytest.mark.multiservice


def test_full_kafka_chain_creates_trip_members_expense_and_summary(account, rpc):
    owner, member = account(), account()
    trip = rpc.call("trips", "POST", "/trips", owner, {"name": "Kafka chain", "destinationPoint": "Rome"})
    path = f"/trips/{trip['id']}"
    # trips -> users verifies existence; expenses -> trips verifies membership.
    rpc.call("trips", "POST", path + "/participants", owner, {"userId": member["userId"], "role": "MEMBER"})
    expense = rpc.call("expenses", "POST", path + "/expenses", member,
                       {"description": "Dinner", "amount": "60", "currency": "EUR"})
    assert expense["paidBy"] == member["userId"] and len(expense["shares"]) == 2
    summary = rpc.call("expenses", "GET", path + "/expenses/summary", owner)[0]
    assert summary["currency"] == "EUR"
    assert summary["debts"][0]["fromUserId"] == owner["userId"]
    assert summary["debts"][0]["toUserId"] == member["userId"]


def test_redelivered_mutation_returns_same_result_and_changed_payload_conflicts(account, rpc, cache):
    owner = account()
    name = "Dedup " + uuid.uuid4().hex
    request = rpc.request("POST", "/trips", owner, {"name": name, "destinationPoint": "Paris"})
    first, second = rpc.send("trips", request), rpc.send("trips", request)
    assert first["status"] == second["status"] == 200
    assert first["body"]["id"] == second["body"]["id"]
    keys = list(cache.scan_iter("travelapp:v1:rpc:*:" + request["id"]))
    assert len(keys) == 1 and cache.ttl(keys[0]) > 0
    conflict = rpc.send("trips", {**request, "body": {"name": "Changed", "destinationPoint": "Rome"}})
    assert conflict["status"] == 409
    trips = api("trips", "GET", "/trips", owner).json()
    assert len([t for t in trips if t["name"] == name]) == 1


def test_cached_mutation_still_checks_authentication(account, rpc):
    owner = account()
    request = rpc.request("POST", "/trips", owner, {"name": "Private result", "destinationPoint": "Rome"})
    assert rpc.send("trips", request)["status"] == 200
    forged = rpc.send("trips", {**request, "authorization": "Bearer invalid"})
    assert forged["status"] == 401 and forged["body"] is None


def test_pending_result_is_not_reexecuted_after_a_lost_reply(account, rpc, cache):
    owner = account()
    name = "Pending " + uuid.uuid4().hex
    request = rpc.request("POST", "/trips", owner, {"name": name, "destinationPoint": "Rome"})
    assert rpc.send("trips", request)["status"] == 200
    keys = list(cache.scan_iter("travelapp:v1:rpc:*:" + request["id"]))
    assert len(keys) == 1
    key = keys[0]
    fingerprint = cache.get(key).split("\n", 1)[0]
    # Simulate a committed mutation whose reply could not be persisted.
    cache.set(key, fingerprint + "\nPENDING", ex=600)
    assert rpc.send("trips", request)["status"] == 503
    assert len([t for t in api("trips", "GET", "/trips", owner).json() if t["name"] == name]) == 1


@pytest.mark.parametrize("expires_at", [1, 4102444800000])
def test_expired_and_excessively_future_requests_are_not_executed(account, rpc, expires_at):
    owner = account()
    name = uuid.uuid4().hex
    reply = rpc.send("trips", rpc.request("POST", "/trips", owner,
                     {"name": name, "destinationPoint": "Paris"}, expiresAt=expires_at))
    assert reply["status"] == 408
    assert all(t["name"] != name for t in api("trips", "GET", "/trips", owner).json())


def test_broken_messages_do_not_block_following_valid_requests(account, rpc):
    # These have no valid request envelope and therefore intentionally have no reply.
    for malformed in ["not-json", {"id": "invalid-id", "expiresAt": 1}]:
        rpc.producer.send("travel.rpc.trips", key="invalid", value=malformed).get(timeout=10)
    assert isinstance(rpc.call("trips", "GET", "/trips", account()), list)


@pytest.mark.parametrize("method,path,body,status", [
    ("POST", "/trips", {}, 400),
    ("GET", "/trips/-1", None, 400),
    ("GET", "/unsupported", None, 404),
])
def test_kafka_contract_validates_payload_and_route(account, rpc, method, path, body, status):
    rpc.call("trips", method, path, account(), body, status=status)


def test_internal_credentials_are_not_interchangeable(rpc):
    window = {"from": "2030-01-01T00:00:00Z", "to": "2030-01-01T03:00:00Z"}
    rpc.call("trips", "GET", "/internal/trips/departures", body=window, secret=BOT_SECRET, status=401)
    rpc.call("users", "POST", "/internal/auth/telegram", secret=NOTIFICATION_SECRET,
             body={"telegramId": 123, "name": "Denied", "create": True}, status=401)


def test_unavailable_redis_writes_fail_closed_then_recover(account, rpc, cache):
    owner = account()
    name = "Fail closed " + uuid.uuid4().hex
    # Redis stays reachable but rejects writes. Restore configuration even on assertion failure.
    cache.config_set("min-replicas-to-write", 1)
    try:
        reply = rpc.send("trips", rpc.request("POST", "/trips", owner,
                         {"name": name, "destinationPoint": "Rome"}))
        assert reply["status"] == 503
        assert all(t["name"] != name for t in api("trips", "GET", "/trips", owner).json())
    finally:
        cache.config_set("min-replicas-to-write", 0)
    created = rpc.call("trips", "POST", "/trips", owner, {"name": name, "destinationPoint": "Rome"})
    assert created["id"] > 0
