import json
import threading
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "0.0.0.0"
PORT = 8765
UDP_PORT = 8766
commands = []
lock = threading.Lock()

def queue_command(command):
    if command in {"play", "pause", "rewind", "forward"}:
        with lock:
            commands.append(command)
        return True
    return False

def udp_server():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((HOST, UDP_PORT))
    print(f"UDP control/discovery on 0.0.0.0:{UDP_PORT}")
    while True:
        try:
            data, addr = sock.recvfrom(2048)
            message = data.decode("utf-8", errors="ignore").strip()
            if message == "CINEPULSE_DISCOVER":
                reply = f"CINEPULSE|{socket.gethostname()}|{addr[0]}".encode()
                sock.sendto(reply, addr)
            elif message == "CINEPULSE_PING":
                sock.sendto(b"CINEPULSE_OK", addr)
            elif queue_command(message):
                sock.sendto(b"CINEPULSE_OK", addr)
            else:
                sock.sendto(b"CINEPULSE_ERROR", addr)
        except Exception as exc:
            print(f"UDP error: {exc}")

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
            queue_command(command)
            self._headers()
            self.wfile.write(json.dumps({"ok":True,"command":command}).encode())
        except Exception as exc:
            self._headers(400)
            self.wfile.write(json.dumps({"ok":False,"error":str(exc)}).encode())

    def log_message(self, *_):
        pass

if __name__ == "__main__":
    threading.Thread(target=udp_server, daemon=True).start()
    print("CinePulse Controller")
    print("Listening on http://0.0.0.0:8765")
    print("Keep this window open while CinePulse is controlling Chrome.")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
