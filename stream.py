# tcp_to_mjpeg.py
import socket, struct
from http.server import BaseHTTPRequestHandler, HTTPServer

HOST = "192.168.0.12"  # use device IP if not using adb reverse
PORT = 7070         # emulator stream port
HTTP_PORT = 8080

def frame_source():
    s = socket.create_connection((HOST, PORT))
    while True:
        hdr = s.recv(16)
        if len(hdr) < 16:
            break
        magic, ver, size, w, h = struct.unpack("<4sIIHH", hdr)
        if magic != b"MDJP":
            break
        data = b""
        while len(data) < size:
            chunk = s.recv(size - len(data))
            if not chunk:
                return
            data += chunk
        yield data

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/" or self.path == "/index.html":
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            html = (
                "<!doctype html>"
                "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
                "<style>"
                "html,body{margin:0;height:100%;background:#000;}"
                "img{height:100vh;width:auto;display:block;margin:0 auto;}"
                "</style></head>"
                "<body><img src='/stream' alt='Top screen stream'></body></html>"
            )
            self.wfile.write(html.encode("utf-8"))
            return
        if self.path != "/stream":
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"Open /stream")
            return
        self.send_response(200)
        self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
        self.end_headers()
        for jpg in frame_source():
            self.wfile.write(b"--frame\r\n")
            self.wfile.write(b"Content-Type: image/jpeg\r\n")
            self.wfile.write(f"Content-Length: {len(jpg)}\r\n\r\n".encode())
            self.wfile.write(jpg)
            self.wfile.write(b"\r\n")

HTTPServer(("", HTTP_PORT), Handler).serve_forever()
