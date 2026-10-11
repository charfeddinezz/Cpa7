#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Proxy pool: parse asocks list, test SOCKS5+auth, verify exit IP/geo.
Credentials NEVER printed — hosts are masked in all output.
Usage: python3 proxy_pool.py <list_url_or_file> [max_test]
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request


def mask(url):
    try:
        p = urllib.parse.urlparse(url)
        host = (p.hostname or "?").split(".")
        short = ".".join(host[-2:]) if len(host) >= 2 else (p.hostname or "?")
        return "%s://***@%s:%s" % (p.scheme, short, p.port or "?")
    except Exception:
        return "***"


def parse_line(line):
    line = line.strip()
    if not line or line.startswith("#"):
        return None
    if "://" not in line:
        line = "socks5://" + line
    try:
        p = urllib.parse.urlparse(line)
        if not p.hostname or not p.port:
            return None
        return {"scheme": (p.scheme or "socks5").lower(), "host": p.hostname,
                "port": p.port, "user": urllib.parse.unquote(p.username or ""),
                "pass": urllib.parse.unquote(p.password or ""), "raw": line}
    except Exception:
        return None


def curl_ip(proxy, timeout=18):
    """Exit IP through the proxy (curl handles SOCKS5h + auth)."""
    auth = "%s:%s" % (proxy["user"], proxy["pass"]) if proxy["user"] else ""
    target = "%s@%s:%s" % (auth, proxy["host"], proxy["port"]) if auth else "%s:%s" % (proxy["host"], proxy["port"])
    t0 = time.time()
    try:
        out = subprocess.run(
            ["curl", "-sS", "--max-time", str(timeout), "--socks5-hostname", target,
             "https://api.ipify.org"], capture_output=True, text=True, timeout=timeout + 5)
        ms = int((time.time() - t0) * 1000)
        ip = (out.stdout or "").strip()
        if out.returncode == 0 and re.match(r"^\d+\.\d+\.\d+\.\d+$", ip):
            return {"ok": True, "ip": ip, "ms": ms}
        return {"ok": False, "error": (out.stderr or "bad-ip").strip()[:100], "ms": ms}
    except Exception as e:
        return {"ok": False, "error": "%s: %s" % (type(e).__name__, str(e)[:80]),
                "ms": int((time.time() - t0) * 1000)}


def geo_of(ip):
    try:
        with urllib.request.urlopen("http://ip-api.com/json/%s?fields=status,country,countryCode,city,isp,org,as,query" % ip,
                                    timeout=15) as r:
            return json.loads(r.read(2000).decode())
    except Exception as e:
        return {"status": "fail", "error": str(e)[:80]}


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 proxy_pool.py <list_url_or_file> [max_test]")
        sys.exit(2)
    src = sys.argv[1]
    max_test = int(sys.argv[2]) if len(sys.argv) > 2 else 100
    if src.startswith("http"):
        req = urllib.request.Request(src, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=40) as r:
            lines = r.read(500000).decode("utf-8", errors="replace").splitlines()
    else:
        lines = open(src, encoding="utf-8", errors="replace").read().splitlines()
    proxies = [p for p in (parse_line(l) for l in lines) if p][:max_test]
    print("parsed=%d (to test %d)" % (len(proxies), len(proxies)), flush=True)
    working = []
    for i, p in enumerate(proxies, 1):
        r = curl_ip(p)
        tag = mask(p["raw"])
        if r["ok"]:
            g = geo_of(r["ip"])
            ok_geo = g.get("status") == "success"
            print("[%d/%d] OK %dms exit=%s %s %s %s :: %s" % (
                i, len(proxies), r["ms"], r["ip"], g.get("countryCode", "?"),
                g.get("city", "?"), (g.get("isp", "?") or "?")[:34], tag), flush=True)
            working.append({"proxy": p, "ip": r["ip"], "ms": r["ms"],
                            "cc": g.get("countryCode", "?"), "city": g.get("city", "?"),
                            "isp": g.get("isp", "")})
            if len(working) >= 5 and all(w["cc"] == "US" for w in working):
                print("5 US working proxies found — stopping early.", flush=True)
                break
        else:
            print("[%d/%d] FAIL %s :: %s" % (i, len(proxies), r["error"][:60], tag), flush=True)
    # rank: US first, then latency
    working.sort(key=lambda w: (0 if w["cc"] == "US" else 1, w["ms"]))
    out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "working_proxies.json")
    with open(out_path, "w") as f:
        json.dump([{"host": w["proxy"]["host"], "port": w["proxy"]["port"],
                    "user": w["proxy"]["user"], "pass": w["proxy"]["pass"],
                    "ip": w["ip"], "ms": w["ms"], "cc": w["cc"],
                    "city": w["city"], "isp": w["isp"]} for w in working],
                  f, indent=1)
    # NOTE: file contains credentials — kept local, never printed.
    print("working=%d → %s" % (len(working), out_path), flush=True)


if __name__ == "__main__":
    main()
