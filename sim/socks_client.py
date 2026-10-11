#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SOCKS5h fetch via curl: DNS resolves through the tunnel (no leak), cookies in jar."""
import os
import subprocess
import tempfile
import time


class SocksClient:
    def __init__(self, proxy, ua, lang="en-US"):
        self.proxy = proxy
        auth = "%s:%s" % (proxy["user"], proxy["pass"]) if proxy.get("user") else ""
        hostport = "%s:%s" % (proxy["host"], proxy["port"])
        self.target = "%s@%s" % (auth, hostport) if auth else hostport
        self.ua = ua
        self.lang = lang
        fd, self.jar = tempfile.mkstemp(prefix="cpa_cookies_", suffix=".txt")
        os.close(fd)

    def close(self):
        try:
            os.unlink(self.jar)
        except Exception:
            pass

    def get(self, url, referer="https://www.google.com/", timeout=25):
        hdr_file = self.jar + ".hdr"
        body_file = self.jar + ".body"
        t0 = time.time()
        try:
            cmd = ["curl", "-sS", "--max-time", str(timeout),
                   "--socks5-hostname", self.target,
                   "-D", hdr_file, "-o", body_file,
                   "-b", self.jar, "-c", self.jar,
                   "-A", self.ua, "-H", "Accept-Language: %s" % self.lang,
                   "-H", ("Accept: text/html,application/xhtml+xml,application/xml;"
                          "q=0.9,*/*;q=0.8"),
                   "-H", "Referer: %s" % referer, url]
            p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout + 10)
            ms = int((time.time() - t0) * 1000)
            if p.returncode != 0:
                return {"ok": False, "status": 0, "url": url,
                        "error": "CURL: %s" % (p.stderr or "").strip()[:120],
                        "bytes": 0, "ms": ms, "body": "", "ctype": ""}
            try:
                with open(hdr_file, "rb") as f:
                    hdr_raw = f.read().decode("utf-8", errors="replace")
            except Exception:
                hdr_raw = ""
            # last response block (after redirects curl followed? we disable --location: parse first only)
            status = 0
            ctype = ""
            location = ""
            for line in hdr_raw.splitlines():
                if line.startswith("HTTP/"):
                    try:
                        status = int(line.split()[1])
                    except Exception:
                        pass
                elif line.lower().startswith("content-type:"):
                    ctype = line.split(":", 1)[1].strip()
                elif line.lower().startswith("location:"):
                    location = line.split(":", 1)[1].strip()
            try:
                with open(body_file, "rb") as f:
                    raw = f.read(600 * 1024)
            except Exception:
                raw = b""
            enc = "utf-8"
            if "charset=" in ctype:
                try:
                    enc = ctype.split("charset=")[1].split(";")[0].strip() or "utf-8"
                except Exception:
                    pass
            try:
                body = raw.decode(enc, errors="replace")
            except Exception:
                body = ""
            ncookies = 0
            try:
                with open(self.jar) as f:
                    ncookies = sum(1 for l in f if l.strip() and not l.startswith("#"))
            except Exception:
                pass
            if status == 0:
                return {"ok": False, "status": 0, "url": url, "error": "NO_STATUS",
                        "bytes": 0, "ms": ms, "body": "", "ctype": ""}
            return {"ok": True, "status": status, "url": url, "ctype": ctype,
                    "bytes": len(raw), "ms": ms, "body": body, "cookies": ncookies,
                    "location": location}
        except Exception as e:
            return {"ok": False, "status": 0, "url": url,
                    "error": "%s: %s" % (type(e).__name__, str(e)[:100]),
                    "bytes": 0, "ms": int((time.time() - t0) * 1000), "body": "", "ctype": ""}
        finally:
            for f in (hdr_file, body_file):
                try:
                    os.unlink(f)
                except Exception:
                    pass
