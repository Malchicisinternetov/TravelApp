import pytest
from conftest import api

pytestmark = pytest.mark.multiservice


def route():
    return {"name": "Connecting flight", "legs": [
        {"departurePoint": "Moscow", "destinationPoint": "Istanbul", "departureTime": "2030-12-01T10:00:00",
         "arrivalTime": "2030-12-01T14:00:00", "departureZone": "Europe/Moscow", "arrivalZone": "Europe/Istanbul"},
        {"departurePoint": "Istanbul", "destinationPoint": "London", "departureTime": "2030-12-01T16:00:00",
         "arrivalTime": "2030-12-01T15:00:00", "departureZone": "Europe/Istanbul", "arrivalZone": "Europe/London"}]}


def test_route_local_times_order_and_member_edit_persist(journey, rpc):
    trip, owner, member = journey["trip"], journey["owner"], journey["member"]
    path = f"/trips/{trip['id']}"
    rpc.call("trips", "PUT", path, member, route())
    stored = api("trips", "GET", path, owner).json()
    assert stored["legs"][0]["departurePoint"] == "Moscow"
    assert stored["legs"][1]["arrivalTime"] == "2030-12-01T15:00:00"
    assert stored["destinationPoint"] == "London"
    assert len(stored["participants"]) == 3


def test_invalid_route_update_does_not_replace_persisted_trip(journey):
    path, owner = f"/trips/{journey['trip']['id']}", journey["owner"]
    before = api("trips", "GET", path, owner).json()
    invalid = route()
    invalid["legs"][1]["departureTime"] = "2030-12-01T11:00:00"
    api("trips", "PUT", path, owner, json=invalid, expected=400)
    assert api("trips", "GET", path, owner).json() == before


@pytest.mark.parametrize("actor", ["member", "reader", "outsider"])
def test_only_owners_manage_participants_and_delete_trip(journey, actor):
    owner, user = journey["owner"], journey[actor]
    path = f"/trips/{journey['trip']['id']}"
    api("trips", "POST", path + "/participants", user, expected=403,
        json={"userId": owner["userId"], "role": "OWNER"})
    api("trips", "PUT", path + f"/participants/{user['userId']}/role", user, expected=403, json={"role": "OWNER"})
    api("trips", "DELETE", path, user, expected=403)


def test_live_user_directory_prevents_nonexistent_participants(journey):
    path, owner = f"/trips/{journey['trip']['id']}", journey["owner"]
    api("trips", "POST", path + "/participants", owner, expected=404,
        json={"userId": 999999999, "role": "MEMBER"})
    participants = api("trips", "GET", path + "/participants", owner).json()
    assert len(participants) == 3
    api("trips", "POST", path + "/participants", owner, expected=409,
        json={"userId": journey["member"]["userId"], "role": "MEMBER"})


def test_last_owner_protection_and_ownership_transfer(journey):
    path, owner, member = f"/trips/{journey['trip']['id']}", journey["owner"], journey["member"]
    api("trips", "DELETE", path + f"/participants/{owner['userId']}", owner, expected=409)
    api("trips", "PUT", path + f"/participants/{owner['userId']}/role", owner, expected=409, json={"role": "MEMBER"})
    api("trips", "PUT", path + f"/participants/{member['userId']}/role", owner, json={"role": "OWNER"})
    api("trips", "PUT", path + f"/participants/{owner['userId']}/role", owner, json={"role": "READ_ONLY"})
    api("trips", "DELETE", path, owner, expected=403)
    api("trips", "DELETE", path, member, expected=204)
    api("trips", "GET", path, member, expected=404)


def test_outsiders_and_removed_members_lose_trip_visibility(journey):
    path, owner, member, outsider = (f"/trips/{journey['trip']['id']}", journey["owner"],
                                     journey["member"], journey["outsider"])
    api("trips", "GET", path, outsider, expected=403)
    assert journey["trip"]["id"] not in [t["id"] for t in api("trips", "GET", "/trips", outsider).json()]
    api("trips", "DELETE", path + f"/participants/{member['userId']}", owner, expected=204)
    api("trips", "GET", path, member, expected=403)
    assert journey["trip"]["id"] not in [t["id"] for t in api("trips", "GET", "/trips", member).json()]
