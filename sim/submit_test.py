#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ONE clearly-marked QA submission through the mapped registration form.
Test data is fictional (555 phones avoided where possible; Test/Delete markers).
Usage: submit_test.py <proxy_json> <proxy_index>"""
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
JOIN = "https://appsec.aarp.org/mem/join"
POST_URL = "https://appsec.aarp.org/mem/joinbilling?intcmp=DSO-MEM-JOIN-BILLING"


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
            return r.geturl(), r.read(900 * 1024).decode("utf-8", errors="replace")

    print("STEP 1: GET join page (session + CSRF)...", flush=True)
    _, html = get(JOIN, "https://www.aarp.org/membership/lp/444-bc5-calc-faq-hero-simple-wm/")
    m = re.search(r'<form id="joinAppForm".*?</form>', html, re.I | re.S)
    if not m:
        print("FAIL: join form not present")
        return
    block = m.group(0)

    def hidden(name):
        mm = re.search(r'name="%s"[^>]*value="([^"]*)"' % re.escape(name), block, re.I)
        if not mm:
            mm = re.search(r'value="([^"]*)"[^>]*name="%s"' % re.escape(name), block, re.I)
        return mm.group(1) if mm else ""

    form = {}
    for mm in re.finditer(r'<input[^>]*name="([^"]+)"[^>]*>', block, re.I):
        tag = mm.group(0)
        nm = mm.group(1)
        vm = re.search(r'value="([^"]*)"', tag, re.I)
        tp = re.search(r'type="([^"]*)"', tag, re.I)
        t = (tp.group(1) if tp else "text").lower()
        if t in ("checkbox", "radio"):
            continue  # set explicitly below
        form[nm] = vm.group(1) if vm else ""

    # Clearly-marked QA data (fictional persons, owner domain, 555-free zone avoided where possible)
    form.update({
        "membershipVO.firstName": "CpaTest",
        "membershipVO.middleName": "",
        "membershipVO.lastName": "DeleteMe",
        "dobAsString": "01/15/1970",
        "membershipVO.mailingAddress.addressLine1": "123 Test Delete Street",
        "membershipVO.mailingAddress.addressLine2": "",
        "membershipVO.mailingAddress.city": "New York",
        "membershipVO.mailingAddress.state": "NY",
        "MailingCountry": "US",
        "membershipVO.mailingAddress.postalCode": "10001",
        "membershipVO.spFirstName": "SpouseTest",
        "membershipVO.spLastName": "DeleteMe",
        "membershipVO.email": "cpatest-delete@singingfiles.com",
        "membershipVO.manage": "Test#Delete1234",
        "regTermsAndConditions": "on",
        "digitalTermCheckbox": "true",
    })
    print("STEP 2: POST registration with QA data (email=cpatest-delete@singingfiles.com)...", flush=True)
    data = urllib.parse.urlencode(form).encode()
    req = urllib.request.Request(POST_URL, data=data, headers={
        "User-Agent": UA, "Accept-Language": "en-US",
        "Content-Type": "application/x-www-form-urlencoded",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Origin": "https://appsec.aarp.org",
        "Referer": JOIN})
    try:
        with op.open(req, timeout=30) as r:
            final = r.geturl()
            body = r.read(900 * 1024).decode("utf-8", errors="replace")
            code = r.status
    except urllib.error.HTTPError as e:
        final, body, code = e.geturl() or POST_URL, e.read(200 * 1024).decode("utf-8", errors="replace"), e.code
    print("POST -> HTTP %s | final: %s" % (code, final[:120]))
    low = body.lower()
    print("bytes: %d" % len(body))
    for sig in ["billing", "payment", "card number", "order summary", "thank", "success",
                "error", "invalid", "required", "already", "exists", "verify your email",
                "check your inbox", "confirmation"]:
        if sig in low:
            print("  signal: '%s' x%d" % (sig, low.count(sig)))
    title = re.search(r"<title>(.*?)</title>", body, re.I | re.S)
    print("title: %s" % (title.group(1).strip()[:100] if title else "-"))
    open("/tmp/joinbilling.html", "w").write(body)
    print("saved: /tmp/joinbilling.html")


if __name__ == "__main__":
    main()
