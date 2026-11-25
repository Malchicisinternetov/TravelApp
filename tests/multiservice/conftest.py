import json
import os
import time
import uuid

import pytest
import redis
import requests
from kafka import KafkaConsumer, KafkaProducer, TopicPartition

pytestmark = pytest.mark.multiservice
URLS = {"users": "http://userservice:8082", "trips": "http://tripservice:8083",
        "expenses": "http://expensesservice:8084", "weather": "http://weatherservice:8085",
        "currency": "http://currencyservice:8086"}
BOT_SECRET = os.getenv("TEST_BOT_SECRET", "integration-only-bot-secret-at-least-32-bytes")
NOTIFICATION_SECRET = os.getenv("TEST_NOTIFICATION_SECRET", "integration-only-notification-secret-32-bytes")
JWT_SECRET = os.getenv("TEST_JWT_SECRET", "integration-only-jwt-secret-at-least-32-bytes")


def eventually(probe, timeout=40, interval=0.25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = probe()
        if result:
            return result
        time.sleep(interval)
    raise AssertionError("Expected state was not reached within the timeout")


def api(service, method, path, user=None, expected=200, **kwargs):
    headers = dict(kwargs.pop("headers", {}))
    if user:
        headers["Authorization"] = "Bearer " + user["accessToken"]
    response = requests.request(method, URLS[service] + path, headers=headers, timeout=35, **kwargs)
    # Deliberately avoid dumping request credentials and auth response bodies into CI logs.
    assert response.status_code == expected, f"{method} {service}{path}: expected {expected}, got {response.status_code}"
    return response


@pytest.fixture(scope="session", autouse=True)
def ready():
    for service, path in [("users", "/users/1"), ("trips", "/trips"), ("expenses", "/trips/1/expenses"),
                          ("weather", "/weather"), ("currency", "/currencies")]:
        def check():
            try:
                return requests.get(URLS[service] + path, timeout=2).status_code == 401
            except requests.RequestException:
                return False
        eventually(check, timeout=120)


@pytest.fixture
def account():
    def create(name="Traveller"):
        username = "test_" + uuid.uuid4().hex[:20]
        response = api("users", "POST", "/auth/register", expected=201,
                       json={"username": username, "password": "Compass!Trip42", "name": name})
        return {**response.json(), "username": username}
    return create


@pytest.fixture
def journey(account):
    owner, member, reader, outsider = [account(name) for name in ["Owner", "Member", "Reader", "Outsider"]]
    trip = api("trips", "POST", "/trips", owner, expected=201,
               json={"name": "Journey " + uuid.uuid4().hex, "description": "Plans", "destinationPoint": "Rome"}).json()
    for user, role in [(member, "MEMBER"), (reader, "READ_ONLY")]:
        api("trips", "POST", f"/trips/{trip['id']}/participants", owner, expected=201,
            json={"userId": user["userId"], "role": role})
    return {"trip": trip, "owner": owner, "member": member, "reader": reader, "outsider": outsider}


@pytest.fixture(scope="session")
def cache():
    client = redis.Redis(host="redis", port=6379, decode_responses=True, socket_timeout=3)
    assert client.ping()
    yield client
    client.close()


class Rpc:
    def __init__(self):
        self.consumer = KafkaConsumer(bootstrap_servers="kafka:29092", enable_auto_commit=False,
                                      group_id=None, value_deserializer=lambda b: json.loads(b.decode()),
                                      request_timeout_ms=20000, api_version_auto_timeout_ms=20000)
        partition = TopicPartition("travel.rpc.replies", 0)
        self.consumer.assign([partition])
        self.consumer.seek_to_end(partition)
        self.producer = KafkaProducer(bootstrap_servers="kafka:29092", acks="all",
                                      key_serializer=lambda value: value.encode(),
                                      value_serializer=lambda value: json.dumps(value).encode(),
                                      request_timeout_ms=20000, max_block_ms=20000)

    def request(self, method, path, user=None, body=None, secret=None, **overrides):
        return {"id": str(uuid.uuid4()), "expiresAt": int(time.time() * 1000) + 90000,
                "method": method, "path": path, "authorization": "Bearer " + user["accessToken"] if user else None,
                "serviceSecret": secret, "body": body, **overrides}

    def send(self, service, request):
        self.producer.send("travel.rpc." + service, key=request["id"], value=request).get(timeout=20)
        deadline = time.monotonic() + 35
        while time.monotonic() < deadline:
            for records in self.consumer.poll(timeout_ms=500).values():
                for record in records:
                    if record.value["id"] == request["id"]:
                        return record.value
        raise AssertionError(f"No correlated Kafka reply from {service}")

    def call(self, service, method, path, user=None, body=None, secret=None, status=200):
        reply = self.send(service, self.request(method, path, user, body, secret))
        assert reply["status"] == status, f"Kafka {method} {service}{path}: expected {status}, got {reply['status']}"
        return reply["body"]

    def close(self):
        self.producer.close(timeout=5)
        self.consumer.close()


@pytest.fixture(scope="session")
def rpc(ready):
    client = Rpc()
    yield client
    client.close()


@pytest.fixture
def external(cache):
    # Isolated stack, serial pytest execution: clear only vendor caches, never RPC markers.
    def clear():
        keys = list(cache.scan_iter("travelapp:v1:cache:*"))
        if keys:
            cache.delete(*keys)
    clear()
    yield "http://external:8090"
    faults = requests.get("http://external:8090/__admin/state", timeout=3).json()["faults"]
    for path in faults:
        requests.post("http://external:8090/__admin/fault", json={"path": path}, timeout=3).raise_for_status()
    clear()


def external_state():
    return requests.get("http://external:8090/__admin/state", timeout=3).json()


def chat_id():
    return int(uuid.uuid4().hex[:12], 16)


def messages(chat):
    return [m for m in external_state()["messages"] if m["chat_id"] == chat]


def command(chat, text, **options):
    before = len(messages(chat))
    requests.post("http://external:8090/__admin/update",
                  json={"chat_id": chat, "text": text, **options}, timeout=3).raise_for_status()
    return eventually(lambda: messages(chat)[before:] or None)[0]["text"]


def link(user, chat, rpc):
    code = api("users", "POST", "/auth/telegram/code", user).json()["code"]
    return rpc.call("users", "POST", "/internal/auth/telegram/link",
                    body={"telegramId": chat, "code": code}, secret=BOT_SECRET)
