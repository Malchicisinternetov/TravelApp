import base64
import hashlib
import hmac
import json
import time

import pytest
from conftest import api, JWT_SECRET, BOT_SECRET, link, chat_id

pytestmark = pytest.mark.multiservice


def signed_token(subject, **changes):
    def part(value):
        return base64.urlsafe_b64encode(json.dumps(value).encode()).rstrip(b"=")
    claims = {"sub": str(subject), "iss": "travelapp", "aud": ["travelapp-api"],
              "iat": int(time.time()), "exp": int(time.time()) + 300, **changes}
    data = part({"alg": "HS256"}) + b"." + part(claims)
    signature = base64.urlsafe_b64encode(hmac.new(JWT_SECRET.encode(), data, hashlib.sha256).digest()).rstrip(b"=")
    return (data + b"." + signature).decode()


def test_registration_login_settings_and_public_directory(account, rpc):
    user, other = account("Alice"), account("Bob")
    logged_in = api("users", "POST", "/auth/login",
                    json={"username": user["username"], "password": "Compass!Trip42"}).json()
    assert logged_in["userId"] == user["userId"]
    profile = rpc.call("users", "PUT", f"/users/{user['userId']}", user,
                       {"name": "Alice Updated", "currency": "USD", "timezone": "Europe/Moscow", "amount": "123.45"})
    assert profile["currency"] == "USD" and profile["name"] == "Alice Updated"
    assert not {"username", "passwordHash", "password"} & profile.keys()
    settings_path = f"/users/{user['userId']}/settings"
    api("users", "PUT", settings_path, user, json={"theme": "dark", "language": "ru", "notificationMethod": "TELEGRAM"})
    assert rpc.call("users", "GET", settings_path, user)["language"] == "ru"
    directory = api("users", "POST", "/users/directory", other,
                    json={"userIds": [user["userId"], 999999999]}).json()
    assert directory == [{"userId": user["userId"], "name": "Alice Updated"}]


@pytest.mark.parametrize("password", ["12345678", "123456789012", "OnlyLetters!", "Password123!"])
def test_weak_registration_is_rejected(password):
    api("users", "POST", "/auth/register", expected=400,
        json={"username": "weak", "name": "Test", "password": password})


def test_duplicate_registration_and_wrong_password(account):
    user = account()
    api("users", "POST", "/auth/register", expected=409,
        json={"username": user["username"], "password": "Compass!Trip42", "name": "Duplicate"})
    api("users", "POST", "/auth/login", expected=401,
        json={"username": user["username"], "password": "Wrong!Password42"})


def test_web_cookie_refresh_and_logout(account):
    user = account()
    headers = {"X-TravelApp-Client": "web"}
    response = api("users", "POST", "/auth/login", headers=headers,
                   json={"username": user["username"], "password": "Compass!Trip42"})
    cookie = response.headers["Set-Cookie"].split(";", 1)[0]
    assert "HttpOnly" in response.headers["Set-Cookie"]
    assert "SameSite=Strict" in response.headers["Set-Cookie"]
    api("users", "POST", "/auth/refresh", headers={"Cookie": cookie}, expected=403)
    refreshed = api("users", "POST", "/auth/refresh", headers={**headers, "Cookie": cookie}).json()
    api("trips", "GET", "/trips", refreshed)
    api("users", "POST", "/auth/logout", headers={**headers, "Cookie": cookie}, expected=204)
    api("users", "POST", "/auth/refresh", headers={**headers, "Cookie": cookie}, expected=401)


@pytest.mark.parametrize("claims", [
    {"exp": 1}, {"iss": "wrong"}, {"aud": ["wrong"]}, {"sub": "not-a-number"}, {"nbf": 4102444800},
])
def test_jwt_validation_is_consistent_across_services(account, claims):
    user = account()
    invalid = {"accessToken": signed_token(user["userId"], **claims)}
    for service, path in [("users", f"/users/{user['userId']}"), ("trips", "/trips"),
                          ("expenses", "/trips/1/expenses"), ("weather", "/weather"), ("currency", "/currencies")]:
        api(service, "GET", path, invalid, expected=401)


def test_private_profiles_cannot_be_read_over_rest_or_kafka(account, rpc):
    alice, bob = account("Alice"), account("Bob")
    for path in [f"/users/{alice['userId']}", f"/users/{alice['userId']}/settings"]:
        api("users", "GET", path, bob, expected=403)
        rpc.call("users", "GET", path, bob, status=403)


def test_link_code_is_single_use_and_unlink_revokes_bot_login(account, rpc):
    user, chat = account(), chat_id()
    code = api("users", "POST", "/auth/telegram/code", user).json()["code"]
    body = {"telegramId": chat, "code": code}
    rpc.call("users", "POST", "/internal/auth/telegram/link", body=body, secret="wrong", status=401)
    linked = rpc.call("users", "POST", "/internal/auth/telegram/link", body=body, secret=BOT_SECRET)
    assert linked["userId"] == user["userId"]
    rpc.call("users", "POST", "/internal/auth/telegram/link", body=body, secret=BOT_SECRET, status=400)
    settings = api("users", "GET", f"/users/{user['userId']}/settings", user).json()
    assert settings["telegramVerified"] and settings["telegramId"] == chat
    api("users", "DELETE", "/auth/telegram", user, expected=204)
    rpc.call("users", "POST", "/internal/auth/telegram",
             body={"telegramId": chat, "name": "Test", "create": False}, secret=BOT_SECRET, status=404)


def test_link_cannot_steal_existing_telegram_identity(account, rpc):
    alice, bob, chat = account(), account(), chat_id()
    link(alice, chat, rpc)
    code = api("users", "POST", "/auth/telegram/code", bob).json()["code"]
    rpc.call("users", "POST", "/internal/auth/telegram/link", body={"telegramId": chat, "code": code},
             secret=BOT_SECRET, status=409)
    logged_in = rpc.call("users", "POST", "/internal/auth/telegram",
                         body={"telegramId": chat, "name": "Test", "create": False}, secret=BOT_SECRET)
    assert logged_in["userId"] == alice["userId"]
