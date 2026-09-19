#!/usr/bin/env python3
"""Real process shutdown: v2 controller, legacy WS and TLS, with open sockets."""
import base64
import json
import os
from pathlib import Path
import signal
import socket
import ssl
import subprocess
import tempfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BINARY = Path(os.environ.get('ANDROIDOSCOPY_BINARY', ROOT / 'server/target/debug/androidoscopy'))

def port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]

def websocket(port, path, tls=False, token=None):
    s = socket.create_connection(('127.0.0.1', port), timeout=3)
    if tls:
        s = ssl._create_unverified_context().wrap_socket(s, server_hostname='localhost')
    key = base64.b64encode(os.urandom(16)).decode()
    auth = f'Authorization: Bearer {token}\r\n' if token else ''
    s.sendall((f'GET {path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\n'
               f'Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n'
               f'Sec-WebSocket-Version: 13\r\n{auth}\r\n').encode())
    data = b''
    while b'\r\n\r\n' not in data:
        data += s.recv(4096)
    assert data.startswith(b'HTTP/1.1 101'), data
    return s

for mode in ('run', 'legacy-ws', 'legacy-tls'):
    for sig in (signal.SIGINT, signal.SIGTERM):
        with tempfile.TemporaryDirectory(prefix='androidoscopy-lifecycle-') as tmp:
            tmp = Path(tmp)
            http, app = port(), port()
            config = tmp / 'config.toml'
            config.write_text(f'''[server]
http_port = {http}
websocket_port = {app}
bind_address = "127.0.0.1"
udp_discovery_enabled = true
[server.tls]
enabled = {str(mode == 'legacy-tls').lower()}
cert_path = "{tmp}/cert.pem"
key_path = "{tmp}/key.pem"
''')
            env = dict(os.environ, ANDROIDOSCOPY_CONFIG=str(config), ANDROIDOSCOPY_STATE_DIR=str(tmp/'state'))
            with (tmp/'log').open('w+') as log:
                proc = subprocess.Popen([str(BINARY), 'run' if mode == 'run' else 'legacy'], env=env, stdout=log, stderr=log)
                sockets = []
                try:
                    deadline = time.monotonic() + 15
                    while True:
                        try:
                            with socket.create_connection(('127.0.0.1', http), timeout=.1):
                                break
                        except OSError:
                            assert proc.poll() is None, (tmp/'log').read_text()
                            assert time.monotonic() < deadline, 'startup timeout'
                            time.sleep(.05)
                    if mode == 'run':
                        token = (tmp/'state/control-token').read_text()
                        sockets.append(websocket(http, '/api/v2/events', token=token))
                        # Hold an outbound TLS handshake open: shutdown must cancel it.
                        pending = socket.socket()
                        pending.bind(('127.0.0.1', 0)); pending.listen()
                        sockets.append(pending)
                        request = urllib.request.Request(f'http://127.0.0.1:{http}/api/v2/connect',
                            json.dumps({'address': f'127.0.0.1:{pending.getsockname()[1]}'}).encode(),
                            {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'})
                        with urllib.request.urlopen(request) as response:
                            assert response.status == 200
                        pending.settimeout(3)
                        outbound, _ = pending.accept()
                        sockets.append(outbound)
                    else:
                        # Wait until the second listener/TLS certificate is ready.
                        while True:
                            try:
                                sockets.append(websocket(app, '/ws/app', mode == 'legacy-tls'))
                                break
                            except (OSError, ssl.SSLError):
                                assert time.monotonic() < deadline
                                time.sleep(.05)
                        sockets.append(websocket(http, '/ws/dashboard'))
                    start = time.monotonic()
                    proc.send_signal(sig)
                    assert proc.wait(timeout=8) == 0, (tmp/'log').read_text()
                    assert 'Graceful shutdown complete' in (tmp/'log').read_text(), (tmp/'log').read_text()
                    # Open sockets must be closed by the server, not by this test.
                    for s in sockets:
                        if s is locals().get('pending'):
                            continue
                        s.settimeout(1)
                        try:
                            while s.recv(4096):
                                pass
                        except (ConnectionResetError, ssl.SSLError):
                            pass
                    for p in ([http] if mode == 'run' else [http, app]):
                        with socket.socket() as s:
                            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                            s.bind(('127.0.0.1', p))
                    print(f'PASS {mode} {sig.name} ({time.monotonic()-start:.2f}s)')
                finally:
                    for s in sockets:
                        s.close()
                    if proc.poll() is None:
                        proc.kill(); proc.wait()
