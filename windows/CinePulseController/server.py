import json
import os
import threading
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "0.0.0.0"
PORT = 8765
RELAY_URL = os.environ.get("CINEPULSE_RELAY_URL", "").rstrip("/")
PAIRING_CODE = os.environ.get("CINEPULSE_PAIRING_CODE", "").strip()
RELAY_TOKEN = None
commands = []
lock = threading.Lock()
VALID = {"play", "pause", "rewind", "forward"}
USER_AGENT = "CinePulseController/1.0 (Windows; HTTPS Relay)"

def queue_command(command):
    if command in VALID:
        with lock:
            commands.append(command)
        return True
    return False

def _request_json(url, method="GET", body=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {
        "Accept": "application/json",
        "User-Agent": USER_AGENT,
    }
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    return urllib.request.urlopen(req, timeout=8)

def register_controller():
    global RELAY_TOKEN
    if not RELAY_URL.startswith("https://") or not PAIRING_CODE:
        print("Cloud relay not configured. Set the relay URL and pairing code.")
        return False
    try:
        with _request_json(
            RELAY_URL + "/v1/register",
            "POST",
            {"code": PAIRING_CODE, "role": "controller"}
        ) as response:
            result = json.loads(response.read().decode())
        RELAY_TOKEN = result.get("token")
        if RELAY_TOKEN:
            print("Cloud relay connected. Only playback commands are relayed.")
            return True
        print("Relay registration returned no controller token.")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")
        print(f"Relay registration failed: HTTP {exc.code} {detail}")
    except Exception as exc:
        print(f"Relay registration failed: {exc}")
    return False

def _poll_once():
    # POST is the current protocol. GET is kept as a compatibility fallback
    # for the already-deployed older Worker, so a ZIP-only update can still work.
    try:
        with _request_json(
            RELAY_URL + "/v1/poll",
            "POST",
            {"code": PAIRING_CODE, "token": RELAY_TOKEN}
        ) as response:
            return json.loads(response.read().decode())
    except urllib.error.HTTPError as exc:
        if exc.code not in (403, 404, 405):
            detail = exc.read().decode(errors="replace")
            raise RuntimeError(f"HTTP {exc.code} {detail}")
        # Older deployed Worker: GET /v1/poll?code=...&token=...
        query = urllib.parse.urlencode({"code": PAIRING_CODE, "token": RELAY_TOKEN})
        with _request_json(RELAY_URL + "/v1/poll?" + query, "GET") as response:
            return json.loads(response.read().decode())

def relay_poll_loop():
    last_error = None
    while True:
        if RELAY_TOKEN:
            try:
                result = _poll_once()
                last_error = None
                for command in result.get("commands", []):
                    queue_command(command)
            except urllib.error.HTTPError as exc:
                detail = exc.read().decode(errors="replace")
                message = f"HTTP {exc.code} {detail}".strip()
                if message != last_error:
                    print(f"Relay poll failed: {message}")
                    last_error = message
                time.sleep(2)
            except Exception as exc:
                message = str(exc)
                if message != last_error:
                    print(f"Relay poll failed: {message}")
                    last_error = message
                time.sleep(2)
        else:
            time.sleep(1)

class Handler(BaseHTTPRequestHandler):
    def _headers(self, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "http://localhost")
        self.end_headers()

    def do_GET(self):
        if self.path == "/":
            self._headers()
            self.wfile.write(json.dumps({"app":"CinePulse Controller","status":"running","transport":"cloud relay"}).encode())
            return
        if self.path == "/ping":
            self._headers()
            self.wfile.write(json.dumps({"ok":True,"app":"CinePulse Controller","relay":bool(RELAY_TOKEN)}).encode())
            return
        if self.path == "/commands":
            with lock:
                batch = list(commands)
                commands.clear()
            self._headers()
            self.wfile.write(json.dumps({"commands":batch}).encode())
            return
        self._headers(404)
        self.wfile.write(b'{"error":"not found"}')

    def do_POST(self):
        if self.path != "/command":
            self._headers(404)
            self.wfile.write(b'{"error":"not found"}')
            return
        length = int(self.headers.get("Content-Length", "0"))
        try:
            payload = json.loads(self.rfile.read(length).decode() or "{}")
            command = payload.get("command")
            if command not in VALID:
                raise ValueError("unsupported command")
            queue_command(command)
            self._headers()
            self.wfile.write(json.dumps({"ok":True,"command":command}).encode())
        except Exception as exc:
            self._headers(400)
            self.wfile.write(json.dumps({"ok":False,"error":str(exc)}).encode())

    def log_message(self, *_):
        pass

if __name__ == "__main__":
    register_controller()
    threading.Thread(target=relay_poll_loop, daemon=True).start()
    print("CinePulse Controller")
    print("Local Chrome bridge: http://127.0.0.1:8765")
    print("Cloud relay: outbound HTTPS polling")
    print("Keep this window open while CinePulse is controlling Chrome.")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
