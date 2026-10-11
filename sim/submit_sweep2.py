#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Full QA chain in ONE session: entry GET -> cash1000 POST -> flow POST (confirm).
Usage: submit_sweep2.py <proxy_json> <proxy_index>"""
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
CASH = "https://rewardspark.co/cash1000"


def main():
    proxies = json.load(open(sys.argv[1]))
    p = proxies[int(sys.argv[2]) % len(proxies)]
    socks.set_default_proxy(socks.SOCKS5, p["host"], p["port"], True, p["user"], p["pass"])
    socket.socket = socks.socksocket

    jar = http.cookiejar.CookieJar()
    op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    def req(url, data=None, referer="https://www.google.com/"):
        h = {"User-Agent": UA, "Accept-Language": "en-US",
             "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
             "Referer": referer}
        if data is not None:
            h["Content-Type"] = "application/x-www-form-urlencoded"
            h["Origin"] = "https://rewardspark.co"
        rq = urllib.request.Request(url, data=data, headers=h)
        with op.open(rq, timeout=30) as r:
            return r.geturl(), r.status, r.read(900 * 1024).decode("utf-8", errors="replace")

    print("STEP 1: entry...", flush=True)
    u1, _, _ = req(ENTRY, None)
    print("STEP 2: cash1000 with QA data...", flush=True)
    f1 = {"first_name": "CpaTest", "last_name": "DeleteMe",
          "email": "cpatest-delete@singingfiles.com",
          "address1": "123 Test Delete Street", "address2": "",
          "city": "New York", "state": "NY", "zip": "10001",
          "phone": "5552345678", "dob": "01/15/1970", "gender": "m",
          "uid": "", "aid": "16473", "pid": "1088", "duplicated": "",
          "converted": "", "conversiontype": "", "registration_completed": "",
          "sid1": "100_2214761", "sid2": ""}
    u2, c2, b2 = req(CASH, urllib.parse.urlencode(f1).encode(), u1)
    print("cash1000 -> %s | %s | %d bytes" % (c2, u2[:80], len(b2)))
    m = re.search(r'<form id="formSubmit"[^>]*action="([^"]+)"', b2, re.I)
    if not m:
        print("FAIL: no step-2 form")
        return
    action = m.group(1).replace("&#x3D;", "=").replace("&amp;", "&")
    blk = re.search(r'<form id="formSubmit".*?</form>', b2, re.I | re.S).group(0)
    f2 = {}
    for mm in re.finditer(r'<input[^>]*name="([^"]+)"[^>]*>', blk, re.I):
        tag = mm.group(0)
        nm = mm.group(1)
        if not nm:
            continue
        vm = re.search(r'value="([^"]*)"', tag, re.I)
        tp = re.search(r'type="([^"]*)"', tag, re.I)
        t = (tp.group(1) if tp else "text").lower()
        if t in ("checkbox", "radio"):
            if "checked" in tag.lower() or "chktcpa" in nm or "chkterms" in nm:
                f2[nm] = "on"
            continue
        f2[nm] = vm.group(1) if vm else ""
    f2.update({"email": "cpatest-delete@singingfiles.com", "first_name": "CpaTest",
               "last_name": "DeleteMe", "zip": "10001", "city": "New York",
               "phone": "5552345678", "chktcpa": "on", "chkterms": "on",
               "tcpaconsent": "on", "consent": "on"})
    post_url = urllib.parse.urljoin(u2, action)
    print("STEP 3: flow confirm -> %s" % post_url[:100], flush=True)
    try:
        u3, c3, b3 = req(post_url, urllib.parse.urlencode(f2).encode(), u2)
    except urllib.error.HTTPError as e:
        u3, c3 = e.geturl() or post_url, e.code
        b3 = e.read(300 * 1024).decode("utf-8", errors="replace")
    print("flow -> HTTP %s | final: %s | %d bytes" % (c3, u3[:110], len(b3)))
    low = b3.lower()
    for sig in ["thank", "success", "congrat", "confirm", "winner", "check your email",
                "verify", "complete", "error", "invalid", "declined", "duplicate"]:
        if sig in low:
            print("  signal: '%s' x%d" % (sig, low.count(sig)))
    title = re.search(r"<title>(.*?)</title>", b3, re.I | re.S)
    print("title: %s" % (title.group(1).strip()[:100] if title else "-"))
    open("/tmp/flow_result.html", "w").write(b3)
    print("saved: /tmp/flow_result.html")


if __name__ == "__main__":
    main()
