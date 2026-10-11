#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Test asocks sessions: exit IP + geo per session. Usage: proxy_test.py [count]"""
import json
import subprocess
import sys
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor


def load_list(path="/tmp/proxies.txt"):
    out = []
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            u = urllib.parse.urlparse(line)
            out.append({"user": u.username, "pass": u.password,
                        "host": u.hostname, "port": u.port})
    return out


def exit_ip(p, timeout=14):
    try:
        r = subprocess.run(
            ["curl", "-s", "--max-time", str(timeout),
             "-x", "socks5h://%s:%d" % (p["host"], p["port"]),
             "-U", "%s:%s" % (p["user"], p["pass"]),
             "https://api.ipify.org"],
            capture_output=True, text=True, timeout=timeout + 5)
        ip = (r.stdout or "").strip()
        return ip if ip.count(".") == 3 else ""
    except Exception:
        return ""


def geo(ip):
    try:
        with urllib.request.urlopen("http://ip-api.com/json/%s?fields=status,country,countryCode,city,isp,org,as,query" % ip,
                                    timeout=12) as r:
            return json.load(r)
    except Exception:
        return {}


def test_one(idx_p):
    idx, p = idx_p
    ip = exit_ip(p)
    if not ip:
        return {"idx": idx, "ok": False}
    g = geo(ip)
    return {"idx": idx, "ok": True, "ip": ip,
            "country": g.get("countryCode", "?"), "city": g.get("city", "?"),
            "isp": (g.get("isp", "?") or "?")[:40]}


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 15
    proxies = load_list()[:n]
    print("testing %d sessions..." % len(proxies), flush=True)
    with ThreadPoolExecutor(max_workers=8) as ex:
        results = sorted(ex.map(test_one, enumerate(proxies)), key=lambda r: r["idx"])
    ok = [r for r in results if r["ok"]]
    for r in ok:
        print("OK #%d exit=%s %s/%s isp=%s" % (
            r["idx"], r["ip"], r["country"], r["city"], r["isp"]), flush=True)
    print("working: %d/%d" % (len(ok), len(results)), flush=True)
    with open("/tmp/proxy_working.json", "w") as f:
        json.dump([{"idx": r["idx"], "ip": r["ip"], "country": r["country"]} for r in ok], f)
    # save full creds of working ones for the loop (local file only, never printed)
    full = load_list()
    with open("/tmp/proxy_working_full.json", "w") as f:
        json.dump([full[r["idx"]] for r in ok], f)


if __name__ == "__main__":
    main()
