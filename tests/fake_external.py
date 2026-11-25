"""Deterministic HTTP replacements for external vendors; used only by compose.test.yaml."""
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

condition = threading.Condition()
state = {"updates": [], "messages": [], "calls": {}, "faults": {}, "next_id": 1}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def respond(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.dispatch({})

    def do_POST(self):
        # Java's JDK HTTP client streams JSON with Transfer-Encoding: chunked.
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b";", 1)[0], 16)
                if size == 0:
                    while self.rfile.readline().strip():
                        pass
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            data = b"".join(chunks)
        else:
            data = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        body = json.loads(data or b"{}")
        self.dispatch(body)

    def dispatch(self, body):
        url = urlparse(self.path)
        path, query = url.path, parse_qs(url.query)
        with condition:
            if path == "/health":
                return self.respond(200, {"ok": True})
            if path == "/__admin/state":
                return self.respond(200, state)
            if path == "/__admin/fault":
                if body.get("status"):
                    state["faults"][body["path"]] = body
                else:
                    state["faults"].pop(body["path"], None)
                return self.respond(200, {"ok": True})
            if path == "/__admin/update":
                update_id = state["next_id"]
                state["next_id"] += 1
                sender = body["chat_id"]
                state["updates"].append({"update_id": update_id, "message": {
                    "from": {"id": sender, "first_name": "Test Traveller", "is_bot": body.get("is_bot", False)},
                    "chat": {"id": sender, "type": body.get("chat_type", "private")}, "text": body["text"]}})
                condition.notify_all()
                return self.respond(200, {"update_id": update_id})
            state["calls"][path] = state["calls"].get(path, 0) + 1
            fault = state["faults"].get(path)
            if fault:
                return self.respond(fault["status"], fault.get("body", {"error": "test upstream failure"}))
            if path.endswith("/getUpdates"):
                offset = body.get("offset", 0)
                updates = [u for u in state["updates"] if u["update_id"] >= offset]
                if not updates:
                    condition.wait(timeout=0.5)
                    updates = [u for u in state["updates"] if u["update_id"] >= offset]
                return self.respond(200, {"ok": True, "result": updates})
            if path.endswith("/sendMessage"):
                if "chat_id" not in body or "text" not in body:
                    return self.respond(400, {"ok": False, "description": "Invalid sendMessage payload"})
                state["messages"].append({**body, "at": time.time()})
                condition.notify_all()
                return self.respond(200, {"ok": True, "result": {"message_id": len(state["messages"])}})
        if path == "/v1/search":
            name = query.get("name", [""])[0]
            locations = [] if "unknown" in name.lower() else [
                {"name": "Москва", "country": "Россия", "admin1": "Москва", "latitude": 55.75, "longitude": 37.62},
                {"name": "Москва", "country": "Test", "admin1": "Other region", "latitude": 45.0, "longitude": 30.0}]
            return self.respond(200, {"results": locations})
        if path == "/v1/forecast":
            return self.respond(200, {
                "timezone": "Europe/Moscow",
                "current": {"time": "2030-06-01T10:00", "temperature_2m": 21.5, "apparent_temperature": 20.0,
                            "relative_humidity_2m": 60, "wind_speed_10m": 3.5, "weather_code": 1, "is_day": 1},
                "daily": {"time": ["2030-06-01", "2030-06-02"], "weather_code": [1, 3],
                          "temperature_2m_min": [12, 13], "temperature_2m_max": [22, 23],
                          "precipitation_probability_max": [10, 20],
                          "sunrise": ["2030-06-01T04:00", "2030-06-02T04:00"],
                          "sunset": ["2030-06-01T21:00", "2030-06-02T21:00"]}})
        if path == "/v2/currencies":
            return self.respond(200, [{"iso_code": code, "name": code, "type": kind}
                                     for code, kind in [("USD", "currency"), ("EUR", "currency"), ("RUB", "currency"),
                                                        ("JPY", "currency"), ("KWD", "currency"), ("BTC", "crypto")]])
        if path == "/v2/rates":
            base = query.get("base", ["USD"])[0]
            return self.respond(200, [{"date": "2030-05-31", "base": base, "quote": code, "rate": rate}
                                     for code, rate in {"USD": 1, "EUR": 0.9, "RUB": 90, "JPY": 150.555, "KWD": 0.30755}.items()
                                     if code != base])
        return self.respond(404, {"error": "unknown stub route"})


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8090), Handler).serve_forever()
