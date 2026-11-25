import requests
import pytest
from conftest import api, external_state

pytestmark = pytest.mark.multiservice


def calls(path):
    return external_state()["calls"].get(path, 0)


def fault(external, path, status, body=None):
    response = requests.post(external + "/__admin/fault",
                             json={"path": path, "status": status, "body": body or {"error": "test"}}, timeout=3)
    response.raise_for_status()


def test_weather_city_coordinates_forecast_and_redis_cache(account, external, cache):
    user = account()
    before_city, before_weather = calls("/v1/search"), calls("/v1/forecast")
    locations = api("weather", "GET", "/weather/locations", user, params={"city": "Москва"}).json()
    assert len(locations) == 2 and locations[0]["admin1"] != locations[1]["admin1"]
    by_city = api("weather", "GET", "/weather", user, params={"city": "Москва"}).json()
    by_coordinates = api("weather", "GET", "/weather", user, params={"latitude": 55.75, "longitude": 37.62}).json()
    assert by_city["current"] == by_coordinates["current"]
    assert by_city["current"]["temperature"] == 21.5 and len(by_city["forecast"]) == 2
    assert by_city["timezone"] == "Europe/Moscow"
    assert calls("/v1/search") == before_city + 1
    assert calls("/v1/forecast") == before_weather + 1
    keys = list(cache.scan_iter("travelapp:v1:cache:weather:forecast:*"))
    assert len(keys) == 1 and 0 < cache.ttl(keys[0]) <= 600
    cache.delete(keys[0])
    api("weather", "GET", "/weather", user, params={"latitude": 55.75, "longitude": 37.62})
    assert calls("/v1/forecast") == before_weather + 2


@pytest.mark.parametrize("params,status", [
    ({}, 400), ({"latitude": 91, "longitude": 0}, 400), ({"latitude": 10}, 400),
    ({"city": "Москва", "latitude": 10}, 400), ({"city": "unknown-city"}, 404),
])
def test_weather_invalid_input_and_unknown_city(account, external, params, status):
    api("weather", "GET", "/weather", account(), expected=status, params=params)


def test_failed_weather_result_is_not_cached_and_recovers(account, external):
    user = account()
    fault(external, "/v1/forecast", 503)
    api("weather", "GET", "/weather", user, expected=503, params={"latitude": 10, "longitude": 20})
    fault(external, "/v1/forecast", 0)
    api("weather", "GET", "/weather", user, params={"latitude": 10, "longitude": 20})


def test_weather_falls_back_to_provider_while_redis_is_unresponsive(account, external, cache):
    user = account()
    before = calls("/v1/forecast")
    # Only this isolated Redis is affected; the pause automatically expires.
    cache.client_pause(2500)
    result = api("weather", "GET", "/weather", user, params={"latitude": 11, "longitude": 21}).json()
    assert result["current"]["temperature"] == 21.5
    assert calls("/v1/forecast") == before + 1


def test_currency_catalogue_conversion_cache_and_currency_rounding(account, external, cache):
    user = account()
    before = calls("/v2/rates")
    catalogue = api("currency", "GET", "/currencies", user).json()
    assert "BTC" not in [c["code"] for c in catalogue]
    for currency, expected in [("JPY", "1521"), ("KWD", "3.106"), ("EUR", "9.09")]:
        result = api("currency", "GET", "/currencies/convert", user,
                      params={"from": "usd", "to": currency, "amount": "10.1"}).json()
        assert result["convertedAmount"] == expected and result["date"] == "2030-05-31"
    assert calls("/v2/rates") == before + 1
    assert 0 < cache.ttl("travelapp:v1:cache:currency:rates:USD") <= 3600
    identical = api("currency", "GET", "/currencies/convert", user,
                    params={"from": "USD", "to": "USD", "amount": "0"}).json()
    assert identical["convertedAmount"] == "0.00" and identical["rate"] == "1"


@pytest.mark.parametrize("params", [
    {"from": "USD", "to": "EUR", "amount": "-1"},
    {"from": "BTC", "to": "EUR", "amount": "1"},
    {"from": "USD", "to": "EUR", "amount": "NaN"},
])
def test_currency_bad_requests(account, external, params):
    api("currency", "GET", "/currencies/convert", account(), params=params, expected=400)


def test_currency_provider_failure_and_missing_pair(account, external):
    user = account()
    fault(external, "/v2/rates", 429)
    api("currency", "GET", "/currencies/rates", user, params={"base": "USD"}, expected=503)
    fault(external, "/v2/rates", 200, [{"date": "2030-05-31", "base": "USD", "quote": "EUR", "rate": 0.9}])
    api("currency", "GET", "/currencies/convert", user, params={"from": "USD", "to": "KWD", "amount": "1"}, expected=404)
