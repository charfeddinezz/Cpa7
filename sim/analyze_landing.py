#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deep landing analysis via residential proxy: forms, fields, CTAs, steps.
Usage: analyze_landing.py "<landing_url>" <proxy_json> [proxy_index]
READ-ONLY: never submits anything."""
import html.parser
import json
import socket
import sys
import urllib.parse
import urllib.request

try:
    import socks
except ImportError:
    socks = None

MOBILE_UA = ("Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 "
             "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36")


class FormGrabber(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.forms = []
        self._cur = None
        self.ctas = []
        self.scripts = 0
        self.iframes = []

    def handle_starttag(self, tag, attrs):
        a = dict((k.lower(), v or "") for k, v in attrs)
        if tag == "form":
            self._cur = {"action": a.get("action", ""), "method": a.get("method", "get").upper(),
                         "fields": []}
            self.forms.append(self._cur)
        elif tag in ("input", "select", "textarea") and self._cur is not None:
            self._cur["fields"].append({"tag": tag, "type": a.get("type", "text"),
                                        "name": a.get("name", ""), "id": a.get("id", ""),
                                        "required": "required" in a or a.get("required") == "required",
                                        "placeholder": a.get("placeholder", "")[:40]})
        elif tag == "button":
            self.ctas.append({"kind": "button", "text": "", "collect": True})
        elif tag == "a" and a.get("href", "").startswith("http"):
            self.ctas.append({"kind": "link", "text": "", "href": a["href"][:120], "collect": False})
        elif tag == "script":
            self.scripts += 1
            if a.get("src", ""):
                self.scripts_src = getattr(self, "scripts_src", []) + [a["src"][:100]]
        elif tag == "iframe" and a.get("src", ""):
            self.iframes.append(a["src"][:120])

    def handle_data(self, data):
        t = data.strip()
        if t and self.ctas and self.ctas[-1].get("collect") and len(self.ctas[-1]["text"]) < 80:
            self.ctas[-1]["text"] += (" " + t) if self.ctas[-1]["text"] else t


def fetch(url, proxy):
    if proxy and socks:
        socks.set_default_proxy(socks.SOCKS5, proxy["host"], proxy["port"], True,
                                proxy["user"], proxy["pass"])
        socket.socket = socks.socksocket
    req = urllib.request.Request(url, headers={
        "User-Agent": MOBILE_UA, "Accept-Language": "en-US",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Referer": "https://www.google.com/"})
    import http.cookiejar
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    with opener.open(req, timeout=25) as r:
        raw = r.read(800 * 1024)
        enc = r.headers.get_content_charset() or "utf-8"
        return r.geturl(), raw.decode(enc, errors="replace")


def main():
    url = sys.argv[1]
    proxies = json.load(open(sys.argv[2])) if len(sys.argv) > 2 else []
    idx = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    proxy = proxies[idx % len(proxies)] if proxies else None
    final_url, html = fetch(url, proxy)
    print("FINAL: %s" % final_url[:120])
    print("BYTES: %d" % len(html))
    g = FormGrabber()
    try:
        g.feed(html)
    except Exception as e:
        print("parse-note: %s" % str(e)[:80])
    print("SCRIPTS: %d  IFRAMES: %d" % (g.scripts, len(g.iframes)))
    for s in getattr(g, "scripts_src", [])[:12]:
        print("  js: %s" % s)
    for f in g.iframes[:6]:
        print("  iframe: %s" % f)
    print("FORMS: %d" % len(g.forms))
    for i, f in enumerate(g.forms):
        print("  [%d] %s %s" % (i, f["method"], f["action"][:110] or "(same-page)"))
        for fld in f["fields"][:20]:
            print("      %s type=%s name=%s id=%s req=%s ph=%s" % (
                fld["tag"], fld["type"], fld["name"], fld["id"],
                fld["required"], fld["placeholder"]))
    print("CTAS:")
    seen = set()
    n = 0
    for c in g.ctas:
        t = (c.get("text") or "").strip()
        if len(t) < 2 or len(t) > 45 or t in seen:
            continue
        seen.add(t)
        n += 1
        extra = "" if c["kind"] == "button" else " -> " + c.get("href", "")[:70]
        print("  - [%s] %s%s" % (c["kind"], t, extra))
        if n >= 25:
            break
    out = {"url": final_url, "forms": g.forms,
           "ctas": [{"kind": c["kind"], "text": c.get("text", "").strip(),
                     "href": c.get("href", "")} for c in g.ctas if 2 <= len((c.get("text") or "").strip()) <= 45][:25]}
    with open("/tmp/landing_map.json", "w") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("MAP: /tmp/landing_map.json")


if __name__ == "__main__":
    main()
