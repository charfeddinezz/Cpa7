#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ONE clearly-marked QA sweepstakes entry (rewardspark $1000 cash SOI).
Usage: submit_sweep.py <proxy_json> <proxy_index>
Fictional test person, owner domain, 555-free where possible."""
import http.cookiejar
import json
import re
import socket
import sys
import urllib.parse
import urllib.request

import socks

UA = ("Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36")
ENTRY = "https://rewardspark.co/lmain?aid=16473&pid=1088&s1=100_2214761"
POST = "https://rewardspark.co/cash1000"


def main():
    proxies = json.load(open(sys.argv[1]))
    p = proxies[int(sys.argv[2]) % len(proxies)]
    socks.set_default_proxy(socks.SOCKS5, p["host"], p["port"], True, p["user"], p["pass"])
    socket.socket = socks.socksocket

    jar = http.cookiejar.CookieJar()
    op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    def get(url, referer):
        req = urllib.request.Request(url, headers={
            "User-Agent": UA, "Accept-Language": "en-US",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer": referer})
        with op.open(req, timeout=25) as r:
            return r.geturl(), r.read(400 * 1024).decode("utf-8", errors="replace")

    print("STEP 1: GET entry (session)...", flush=True)
    final_url, html = get(ENTRY, "https://www.google.com/")
    print("at: %s (%d bytes, cookies=%d)" % (final_url[:90], len(html), len(list(jar))))

    print("STEP 2: POST QA entry (CpaTest DeleteMe / cpatest-delete@singingfiles.com)...", flush=True)
    form = {
        "first_name": "CpaTest", "last_name": "DeleteMe",
        "email": "cpatest-delete@singingfiles.com",
        "address1": "123 Test Delete Street", "address2": "",
        "city": "New York", "state": "NY", "zip": "10001",
        "phone": "5552345678", "dob": "01/15/1970", "gender": "m",
        "uid": "", "aid": "16473", "pid": "1088",
        "duplicated": "", "converted": "", "conversiontype": "",
        "registration_completed": "", "sid1": "100_2214761", "sid2": "",
    }
    data = urllib.parse.urlencode(form).encode()
    req = urllib.request.Request(POST, data=data, headers={
        "User-Agent": UA, "Accept-Language": "en-US",
        "Content-Type": "application/x-www-form-urlencoded",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Origin": "https://rewardspark.co", "Referer": final_url})
    try:
        with op.open(req, timeout=30) as r:
            f2, body, code = r.geturl(), r.read(400 * 1024).decode("utf-8", errors="replace"), r.status
    except urllib.error.HTTPError as e:
        f2, body, code = e.geturl() or POST, e.read(200 * 1024).decode("utf-8", errors="replace"), e.code
    print("POST -> HTTP %s | final: %s" % (code, f2[:110]))
    low = body.lower()
    print("bytes: %d" % len(body))
    for sig in ["thank", "success", "congrat", "confirm", "winner", "congratulations",
                "check your email", "verify", "error", "invalid", "already", "duplicate",
                "phone", "correct", "required"]:
        if sig in low:
            print("  signal: '%s' x%d" % (sig, low.count(sig)))
    title = re.search(r"<title>(.*?)</title>", body, re.I | re.S)
    print("title: %s" % (title.group(1).strip()[:100] if title else "-"))
    open("/tmp/cash1000.html", "w").write(body)
    print("saved: /tmp/cash1000.html")


if __name__ == "__main__":
    main()
