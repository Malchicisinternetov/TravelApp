from decimal import Decimal
import pytest
from conftest import api

pytestmark = pytest.mark.multiservice


def money(value):
    return Decimal(str(value))


def base(journey):
    return f"/trips/{journey['trip']['id']}/expenses"


def add(journey, amount="120.00", currency="USD", actor="owner", **fields):
    return api("expenses", "POST", base(journey), journey[actor], expected=201,
               json={"description": "Tickets", "amount": amount, "currency": currency, **fields}).json()


def test_default_split_and_reciprocal_debts(journey):
    owner, member, reader = [journey[k]["userId"] for k in ["owner", "member", "reader"]]
    expense = add(journey)
    assert expense["createdBy"] == expense["paidBy"] == owner
    assert all(money(share["amount"]) == 40 for share in expense["shares"])
    add(journey, "30", actor="member", participantIds=[owner])
    summary = api("expenses", "GET", base(journey) + "/summary", journey["owner"]).json()[0]
    totals = {p["userId"]: p for p in summary["participants"]}
    assert money(totals[owner]["balance"]) == 50
    assert sum(money(p["balance"]) for p in totals.values()) == 0
    debts = {(d["fromUserId"], d["toUserId"]): money(d["amount"]) for d in summary["debts"]}
    assert debts == {(member, owner): Decimal(10), (reader, owner): Decimal(40)}


@pytest.mark.parametrize("currency,amount,expected", [
    ("USD", "10.00", ["3.34", "3.33", "3.33"]),
    ("JPY", "100", ["34", "33", "33"]),
    ("KWD", "1", ["0.334", "0.333", "0.333"]),
])
def test_rounding_preserves_every_minor_unit(journey, currency, amount, expected):
    ids = [journey[k]["userId"] for k in ["owner", "member", "reader"]]
    expense = add(journey, amount, currency, participantIds=ids)
    assert [money(s["amount"]) for s in expense["shares"]] == list(map(Decimal, expected))
    assert sum(money(s["amount"]) for s in expense["shares"]) == Decimal(amount)


def test_custom_shares_paid_by_and_personal_history(journey):
    owner, member, reader = [journey[k]["userId"] for k in ["owner", "member", "reader"]]
    expense = add(journey, "90", paidBy=member,
                  shares=[{"userId": owner, "amount": "20"}, {"userId": reader, "amount": "70"}])
    assert expense["createdBy"] == owner and expense["paidBy"] == member
    add(journey, "10", participantIds=[owner])
    mine = api("expenses", "GET", base(journey) + "/me", journey["member"]).json()
    assert [e["id"] for e in mine] == [expense["id"]]
    summary = api("expenses", "GET", base(journey) + "/summary/me", journey["reader"]).json()[0]
    assert [p["userId"] for p in summary["participants"]] == [reader]
    assert all(reader in (d["fromUserId"], d["toUserId"]) for d in summary["debts"])


def test_currencies_stay_separate_in_history_and_summary(journey):
    add(journey, "90", "RUB")
    add(journey, "30", "USD", actor="member")
    summaries = api("expenses", "GET", base(journey) + "/summary", journey["owner"]).json()
    assert [s["currency"] for s in summaries] == ["RUB", "USD"]
    for summary in summaries:
        assert sum(money(p["balance"]) for p in summary["participants"]) == 0


@pytest.mark.parametrize("fields", [
    {"amount": "0"}, {"amount": "-1"}, {"amount": "1.001", "currency": "USD"},
    {"amount": "1.1", "currency": "JPY"}, {"currency": "ZZZ"}, {"paidBy": 999999999},
    {"participantIds": []}, {"participantIds": [999999999]},
])
def test_invalid_expenses_do_not_enter_history(journey, fields):
    api("expenses", "POST", base(journey), journey["owner"], expected=400,
        json={"description": "Invalid", "amount": "10", "currency": "USD", **fields})
    assert api("expenses", "GET", base(journey), journey["owner"]).json() == []


def test_member_role_changes_are_immediately_enforced_over_kafka(journey):
    path = f"/trips/{journey['trip']['id']}/participants/{journey['member']['userId']}/role"
    add(journey, actor="member")
    api("trips", "PUT", path, journey["owner"], json={"role": "READ_ONLY"})
    api("expenses", "GET", base(journey), journey["member"])
    api("expenses", "POST", base(journey), journey["member"], expected=403,
        json={"description": "Denied", "amount": "1", "currency": "USD"})
    api("trips", "PUT", path, journey["owner"], json={"role": "MEMBER"})
    add(journey, actor="member")
    api("trips", "DELETE", path.removesuffix("/role"), journey["owner"], expected=204)
    for suffix in ["", "/me", "/summary", "/summary/me"]:
        api("expenses", "GET", base(journey) + suffix, journey["member"], expected=403)


def test_expense_delete_permissions_and_trip_isolation(journey, account):
    owner, member = journey["owner"], journey["member"]
    expense = add(journey, actor="member")
    path = base(journey) + f"/{expense['id']}"
    api("expenses", "DELETE", path, journey["reader"], expected=403)
    other_trip = api("trips", "POST", "/trips", owner, expected=201,
                     json={"name": "Other", "destinationPoint": "Paris"}).json()
    api("expenses", "DELETE", f"/trips/{other_trip['id']}/expenses/{expense['id']}", owner, expected=404)
    own_expense = add(journey)
    api("expenses", "DELETE", base(journey) + f"/{own_expense['id']}", member, expected=403)
    api("expenses", "DELETE", path, member, expected=204)
    api("expenses", "DELETE", path, owner, expected=404)
    api("expenses", "DELETE", base(journey) + f"/{own_expense['id']}", owner, expected=204)
    assert api("expenses", "GET", base(journey) + "/summary", owner).json() == []


def test_removed_and_outside_users_cannot_read_or_add_expenses(journey):
    for method in ["GET", "POST"]:
        kwargs = {} if method == "GET" else {"json": {"description": "Denied", "amount": "1", "currency": "USD"}}
        api("expenses", method, base(journey), journey["outsider"], expected=403, **kwargs)
    api("expenses", "GET", "/trips/999999999/expenses", journey["owner"], expected=404)
