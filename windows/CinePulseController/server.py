import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "0.0.0.0"
PORT = 8765
commands = []
lock = threading.Lock()

class Handler(BaseHTTPRequestHandler):
    def _headers(self, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()

    def do_OPTIONS(self):
        self._headers(204)

    def do_GET(self):
        if self.path == "/":
            self._headers()
            self.wfile.write(json.dumps({
                "app":"CinePulse Controller","status":"running","port":PORT
            }).encode())
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
            if command not in {"play","pause","rewind","forward"}:
                raise ValueError("unsupported command")
            with lock:
                commands.append(command)
            self._headers()
            self.wfile.write(json.dumps({"ok":True,"command":command}).encode())
        except Exception as exc:
            self._headers(400)
            self.wfile.write(json.dumps({"ok":False,"error":str(exc)}).encode())

    def log_message(self, *_):
        pass

if __name__ == "__main__":
    print("CinePulse Controller")
    print("Listening on http://0.0.0.0:8765")
    print("Keep this window open while CinePulse is controlling Chrome.")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
