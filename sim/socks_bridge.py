#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Local HTTP->SOCKS5 bridge (mirrors the app's LocalSocks5HttpBridge):
lets Chromium (no SOCKS-auth support) exit through an authenticated SOCKS5."""
import socket
import threading

import socks


class Bridge:
    def __init__(self, upstream):
        self.up = upstream
        self.srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.srv.bind(("127.0.0.1", 0))
        self.srv.listen(64)
        self.port = self.srv.getsockname()[1]
        self.alive = True
        threading.Thread(target=self._serve, daemon=True).start()

    def _serve(self):
        while self.alive:
            try:
                c, _ = self.srv.accept()
                threading.Thread(target=self._handle, args=(c,), daemon=True).start()
            except Exception:
                break

    def _read_headers(self, c):
        data = b""
        c.settimeout(15)
        while b"\r\n\r\n" not in data:
            chunk = c.recv(4096)
            if not chunk:
                return None, None, None
            data += chunk
            if len(data) > 65536:
                return None, None, None
        head, rest = data.split(b"\r\n\r\n", 1)
        lines = head.decode("latin-1").split("\r\n")
        return lines[0], lines[1:], rest

    def _handle(self, c):
        remote = None
        try:
            requestline, headers, rest = self._read_headers(c)
            if not requestline:
                c.close()
                return
            parts = requestline.split(" ")
            if len(parts) < 2:
                c.close()
                return
            method, target = parts[0].upper(), parts[1]
            if method == "CONNECT":
                host, _, port = target.partition(":")
                port = int(port or 443)
            else:
                # absolute-URI plain HTTP
                if target.lower().startswith("http://"):
                    u = target[7:]
                    hostport, _, path = u.partition("/")
                    host, _, port = hostport.partition(":")
                    port = int(port or 80)
                    requestline = "%s /%s %s" % (method, path, parts[2] if len(parts) > 2 else "HTTP/1.1")
                    headers = [h for h in headers
                               if not h.lower().startswith(("proxy-", "connection:", "keep-alive:"))]
                else:
                    c.close()
                    return
            remote = socks.socksocket()
            remote.set_proxy(socks.SOCKS5, self.up["host"], self.up["port"], True,
                             self.up["user"], self.up["pass"])
            remote.settimeout(20)
            remote.connect((host, port))
            if method == "CONNECT":
                c.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
            else:
                out = [requestline] + headers + ["Connection: close", ""]
                remote.sendall(("\r\n".join(out) + "\r\n").encode("latin-1") + rest)
            c.settimeout(60)
            self._pipe(c, remote)
        except Exception:
            pass
        finally:
            try:
                c.close()
            except Exception:
                pass
            try:
                if remote:
                    remote.close()
            except Exception:
                pass

    def _pipe(self, a, b):
        def fwd(src, dst):
            try:
                while True:
                    d = src.recv(16384)
                    if not d:
                        break
                    dst.sendall(d)
            except Exception:
                pass
            try:
                dst.shutdown(socket.SHUT_WR)
            except Exception:
                pass
        t1 = threading.Thread(target=fwd, args=(a, b), daemon=True)
        t2 = threading.Thread(target=fwd, args=(b, a), daemon=True)
        t1.start()
        t2.start()
        t1.join()
        t2.join()

    def stop(self):
        self.alive = False
        try:
            self.srv.close()
        except Exception:
            pass
