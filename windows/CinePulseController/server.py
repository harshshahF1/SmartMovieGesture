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

def queue_command(command):
    if command in VALID:
        with lock:
            commands.append(command)
        return True
    return False

def register_controller():
    global RELAY_TOKEN
    if not RELAY_URL.startswith("https://") or not PAIRING_CODE:
        print("Cloud relay not configured. Set the relay URL and pairing code.")
        return False
    try:
        body = json.dumps({"code": PAIRING_CODE, "role": "controller"}).encode()
        req = urllib.request.Request(RELAY_URL + "/v1/register", data=body, method="POST", headers={"Content-Type":"application/json","Accept":"application/json"})
        with urllib.request.urlopen(req, timeout=8) as response:
            result = json.loads(response.read().decode())
        RELAY_TOKEN = result.get("token")
        if RELAY_TOKEN:
            print("Cloud relay connected. Only playback commands are relayed.")
            return True
    except Exception as exc:
        print(f"Relay registration failed: {exc}")
    return False

def relay_poll_loop():
    while True:
        if RELAY_TOKEN:
            try:
                path = "/v1/poll?code=" + urllib.parse.quote(PAIRING_CODE) + "&token=" + urllib.parse.quote(RELAY_TOKEN)
                req = urllib.request.Request(RELAY_URL + path, headers={"Accept":"application/json"})
                with urllib.request.urlopen(req, timeout=8) as response:
                    result = json.loads(response.read().decode())
                for command in result.get("commands", []):
                    queue_command(command)
            except Exception as exc:
                print(f"Relay poll failed: {exc}")
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
