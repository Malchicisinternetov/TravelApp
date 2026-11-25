import time
import uuid
from datetime import datetime, timedelta, timezone

import pytest
from conftest import api, chat_id, command, eventually, link, messages, NOTIFICATION_SECRET

pytestmark = pytest.mark.multiservice


def test_bot_commands_operate_on_same_account_and_backend_data(account, rpc):
    user, member, chat = account(), account(), chat_id()
    code = api("users", "POST", "/auth/telegram/code", user).json()["code"]
    assert "Telegram подключён" in command(chat, "/link " + code)
    assert str(user["userId"]) in command(chat, "/id")
    name = "Bot trip " + uuid.uuid4().hex[:10]
    command(chat, f"/trip_create {name} | Bot description | Rome")
    trip = next(t for t in api("trips", "GET", "/trips", user).json() if t["name"] == name)
    command(chat, f"/member_add {trip['id']} | {member['userId']} | MEMBER")
    command(chat, f"/expense_add {trip['id']} | 120 EUR | Bot dinner")
    expenses = api("expenses", "GET", f"/trips/{trip['id']}/expenses", member).json()
    assert len(expenses) == 1 and len(expenses[0]["shares"]) == 2
    assert expenses[0]["createdBy"] == user["userId"]
    assert "EUR" in command(chat, f"/summary {trip['id']}")
    command(chat, f"/expense_delete {trip['id']} | {expenses[0]['id']}")
    assert api("expenses", "GET", f"/trips/{trip['id']}/expenses", user).json() == []


def test_bot_read_only_role_is_enforced_by_services(journey, rpc):
    chat = chat_id()
    link(journey["reader"], chat, rpc)
    reply = command(chat, f"/expense_add {journey['trip']['id']} | 10 USD | Denied")
    assert "Недостаточно прав" in reply
    assert api("expenses", "GET", f"/trips/{journey['trip']['id']}/expenses", journey["owner"]).json() == []


def test_group_chat_cannot_execute_personal_commands(account):
    chat = chat_id()
    reply = command(chat, "/start", chat_type="group")
    assert "личном чате" in reply


def test_start_creates_one_telegram_account_and_reuses_it(rpc):
    from conftest import BOT_SECRET
    chat = chat_id()
    assert "Готово" in command(chat, "/start")
    first = rpc.call("users", "POST", "/internal/auth/telegram", secret=BOT_SECRET,
                     body={"telegramId": chat, "name": "Test", "create": False})
    assert "Готово" in command(chat, "/start")
    second = rpc.call("users", "POST", "/internal/auth/telegram", secret=BOT_SECRET,
                      body={"telegramId": chat, "name": "Test", "create": False})
    assert first["userId"] == second["userId"]


def flight(hours, label):
    departure = (datetime.now(timezone.utc) + timedelta(hours=hours)).replace(microsecond=0)
    arrival = departure + timedelta(hours=2)
    return {"departurePoint": "Origin " + label, "destinationPoint": "Destination " + label,
            "departureTime": departure.replace(tzinfo=None).isoformat(),
            "arrivalTime": arrival.replace(tzinfo=None).isoformat(), "departureZone": "UTC", "arrivalZone": "UTC"}


def test_scheduled_notifications_only_reach_verified_opted_in_current_members_once(journey, rpc):
    owner, member, reader = [journey[k] for k in ["owner", "member", "reader"]]
    chats = {name: chat_id() for name in ["owner", "member", "outsider"]}
    for name in chats:
        link(journey[name], chats[name], rpc)
        user = journey[name]
        api("users", "PUT", f"/users/{user['userId']}/settings", user,
            json={"notificationMethod": "TELEGRAM"})
    # An unverified manually supplied Telegram ID must not become a recipient.
    api("users", "PUT", f"/users/{reader['userId']}/settings", reader,
        json={"telegramId": chat_id(), "notificationMethod": "TELEGRAM"})
    path = f"/trips/{journey['trip']['id']}"
    label = uuid.uuid4().hex[:10]
    route = {"name": label, "legs": [flight(1, label), flight(5, label + "-later")]}
    api("trips", "PUT", path, owner, json=route)
    def matching(chat, departure_label=label):
        prefix = f"Скоро ваш вылет из Origin {departure_label} в Destination {departure_label},"
        return [m for m in messages(chat) if m["text"].startswith(prefix)]

    for name in ["owner", "member"]:
        eventually(lambda: matching(chats[name]), timeout=45)
    # Observe several scheduled polling cycles to detect duplicates.
    time.sleep(4)
    assert all(len(matching(chats[name])) == 1 for name in ["owner", "member"])
    assert not matching(chats["outsider"])
    assert "Origin " + label in matching(chats["owner"])[0]["text"]
    assert not matching(chats["owner"], label + "-later")
    # A changed departure is a new reminder; removed/disabled users must not receive it.
    api("trips", "DELETE", path + f"/participants/{member['userId']}", owner, expected=204)
    api("users", "PUT", f"/users/{owner['userId']}/settings", owner, json={"notificationMethod": "NONE"})
    chats["reader"] = chat_id()
    link(reader, chats["reader"], rpc)
    api("users", "PUT", f"/users/{reader['userId']}/settings", reader,
        json={"notificationMethod": "TELEGRAM"})
    # The newly linked reader can legitimately receive the original reminder.
    # Force that ordering to reproduce the CI race before changing the departure.
    eventually(lambda: matching(chats["reader"]), timeout=45)
    changed_label = label + "-changed"
    route["legs"] = [flight(2, changed_label)]
    api("trips", "PUT", path, owner, json=route)
    # Poll the real notification recipient endpoint as well as observing scheduler output.
    recipients = rpc.call("users", "POST", "/internal/notifications/telegram-recipients",
                          body={"userIds": [owner["userId"], member["userId"], reader["userId"]]},
                          secret=NOTIFICATION_SECRET)
    assert {r["userId"] for r in recipients} == {member["userId"], reader["userId"]}
    # A positive control proves the scheduler is still processing the changed route.
    eventually(lambda: matching(chats["reader"], changed_label), timeout=45)
    time.sleep(4)
    assert all(len(matching(chats[name])) == 1 for name in ["owner", "member"])
    assert len(matching(chats["reader"])) == 1
    assert len(matching(chats["reader"], changed_label)) == 1
    assert all(not matching(chats[name], changed_label) for name in ["owner", "member", "outsider"])
