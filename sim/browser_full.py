#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Full-funnel browser runner: chain -> email -> registration (mapped) ->
offer-wall Yes/No walk -> conversion detect -> RSS verify.
Usage: browser_full.py <proxy_json> <proxy_index> <email> [max_cycles]"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from browser_task import STEALTH_JS
from socks_bridge import Bridge

CONFIRM_KWS = ["thank you", "thanks for", "congratulations", "successfully",
               "order confirmed", "payment successful", "account created",
               "check your email", "registration complete", "survey completed",
               "entry confirmed", "submission received", "winner"]
THANKYOU_BITS = ["/thank", "thank-you", "thankyou", "/success", "confirmation",
                 "order-received", "order-confirm", "/receipt", "signup-success"]
NO_TXT = ["privacy", "terms", "cookie", "login", "sign in", "unsubscribe",
          "do not sell", "back to top", "program requirements", "no, thank"]

PERSONA = {"first": "tonney", "last": "kamlo",
           "address": "123 Test Delete Street", "city": "New York",
           "state": "NY", "zip": "10001", "phone": "91276465465",
           "dob_m": "12", "dob_d": "25", "dob_y": "2002", "gender": "M"}


def main():
    from playwright.sync_api import sync_playwright
    proxies = json.load(open(sys.argv[1]))
    p = proxies[int(sys.argv[2]) % len(proxies)]
    PERSONA["email"] = sys.argv[3]
    max_cycles = int(sys.argv[4]) if len(sys.argv) > 4 else 16
    base = os.path.dirname(os.path.abspath(__file__))
    ts = time.strftime("%Y%m%d-%H%M%S")
    fp = open(os.path.join(base, "logs", "full-%s.log" % ts), "w", encoding="utf-8")

    def tag(m):
        line = "%s %s" % (time.strftime("%H:%M:%S"), m)
        print(line, flush=True)
        fp.write(line + "\n")
        fp.flush()

    bridge = Bridge(p)
    exit_ip, converted = "", False
    with sync_playwright() as pw:
        b = pw.chromium.launch(
            headless=True, proxy={"server": "http://127.0.0.1:%d" % bridge.port},
            args=["--disable-blink-features=AutomationControlled"])
        ctx = b.new_context(
            user_agent=("Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 "
                        "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"),
            viewport={"width": 412, "height": 915}, device_scale_factor=2.6,
            is_mobile=True, has_touch=True, locale="en-US",
            timezone_id="America/New_York",
            geolocation={"latitude": 40.7128, "longitude": -74.0060},
            permissions=["geolocation"])
        ctx.add_init_script(STEALTH_JS)
        pg = ctx.new_page()
        try:
            pg.goto("https://api.ipify.org", timeout=30000)
            exit_ip = (pg.inner_text("body") or "").strip()
        except Exception:
            pass
        tag("🌐 خروج: %s | بريد: %s" % (exit_ip or "?", PERSONA["email"]))
        try:
            pg.goto("https://singingfiles.com/show.php?l=0&u=2214761&id=75222",
                    timeout=45000, wait_until="domcontentloaded")
        except Exception as e:
            tag("⚠️ تحميل: %s" % str(e)[:90])
        time.sleep(10)

        def snap():
            try:
                u, t = pg.url, pg.title()
            except Exception:
                return {}
            try:
                body = pg.evaluate("() => (document.body ? document.body.innerText : '').slice(0,4000)") or ""
            except Exception:
                body = ""
            low = (body + " " + t).lower()
            try:
                pct = pg.evaluate("() => { const m = (document.body ? document.body.innerText : '').match(/(\\d{1,3})\\s*%/); return m ? m[1] : ''; }") or ""
            except Exception:
                pct = ""
            return {"url": u, "title": t[:60], "text": body, "low": low, "pct": pct}

        def fill_all():
            n = 0
            maps = [("input[type=email]:visible", PERSONA["email"]),
                    ("input[name*=first i]:visible", PERSONA["first"]),
                    ("input[name*=last i]:visible", PERSONA["last"]),
                    ("input[name*=address i]:visible", PERSONA["address"]),
                    ("input[name*=zip i]:visible", PERSONA["zip"]),
                    ("input[name*=city i]:visible", PERSONA["city"]),
                    ("input[name*=phone i]:visible, input[type=tel]:visible", PERSONA["phone"])]
            for sel, val in maps:
                try:
                    loc = pg.locator(sel).first
                    if loc.count() > 0 and loc.is_visible() and loc.is_editable():
                        if not loc.input_value():
                            loc.fill(val, timeout=4000)
                            n += 1
                except Exception:
                    continue
            for name, val in [("state", PERSONA["state"]), ("dob_month", PERSONA["dob_m"]),
                              ("dob_day", PERSONA["dob_d"]), ("dob_year", PERSONA["dob_y"]),
                              ("gender", PERSONA["gender"])]:
                try:
                    loc = pg.locator("select[name=%s]:visible" % name).first
                    if loc.count() > 0:
                        loc.select_option(val, timeout=4000)
                        n += 1
                except Exception:
                    continue
            for sel in ["input[name*=terms i]:not(:checked):visible",
                        "input[name*=consent i]:not(:checked):visible",
                        "input[name*=agree i]:not(:checked):visible"]:
                try:
                    loc = pg.locator(sel).first
                    if loc.count() > 0:
                        loc.check(timeout=3000, force=True)
                        n += 1
                except Exception:
                    continue
            return n

        def click_best():
            # priority: Yes/Continue/Submit/Next/Claim, skip nav/legal
            try:
                els = pg.locator("button:visible, a:visible, input[type=submit]:visible").all()[:40]
            except Exception:
                return None
            cands, fallbacks = [], []
            for el in els:
                try:
                    tagname = el.evaluate("e => e.tagName")
                    t = ((el.inner_text() if tagname == "BUTTON" or tagname == "A"
                          else el.get_attribute("value")) or "").strip()
                except Exception:
                    continue
                if not t or len(t) > 35 or any(k in t.lower() for k in NO_TXT):
                    continue
                low = t.lower()
                if low in ("yes", "continue", "submit", "next", "claim", "claim now",
                           "get started", "start", "join now", "sign up", "register"):
                    cands.append((t, el))
                else:
                    fallbacks.append((t, el))
            for t, el in (cands or fallbacks):
                try:
                    el.click(timeout=5000)
                    return t
                except Exception:
                    continue
            return None

        for cycle in range(1, max_cycles + 1):
            a = snap()
            if not a:
                tag("⚠️ دورة %d: لا صفحة" % cycle)
                break
            tag("🔍 #%d [%s%%] '%s' | %s" % (cycle, a["pct"] or "-", a["title"][:45] or "-",
                                            a["url"][:75]))
            if any(k in a["low"] for k in CONFIRM_KWS) or any(b in a["url"].lower() for b in THANKYOU_BITS):
                converted = True
                tag("🏆 تحويل مرصود في الصفحة!")
                break
            if any(k in a["low"] for k in ["captcha", "recaptcha", "turnstile", "just a moment"]):
                tag("🧩 كابتشا — انتظار 15 ث...")
                time.sleep(15)
                continue
            n = fill_all()
            clicked = click_best()
            tag("   تعبئة=%d نقر=%s" % (n, (clicked or "-")[:30]))
            if n == 0 and not clicked:
                tag("   ⏳ لا إجراء — انتظار 8 ث...")
                time.sleep(8)
            else:
                time.sleep(7)
        b.close()
    bridge.stop()
    if exit_ip:
        try:
            req = urllib.request.Request(
                ("https://www.cpagrip.com/common/lead_check_rss.php?user_id=2214761"
                 "&key=6358f4b2642c7c6cf721b4fba5244e17&time=1day&check=ip&value=%s") % exit_ip,
                headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=25) as r:
                import xml.etree.ElementTree as ET
                root = ET.fromstring(r.read(8192).decode("utf-8", errors="replace"))
                info = root.find("lead_info")
                found = (info.findtext("lead_found") or "").strip().lower() == "true"
                tsamp = (info.findtext("lead_timestamp") or "").strip()
            tag("🔎 RSS (%s): lead_found=%s %s" % (exit_ip, found, tsamp))
            converted = converted or found
        except Exception as e:
            tag("⚠️ RSS: %s" % str(e)[:80])
    tag("🏁 الحكم: %s" % ("CONVERTED" if converted else "NOT_CONVERTED"))
    with open(os.path.join(base, "logs", "full-%s.json" % ts), "w", encoding="utf-8") as f:
        import json as J
        J.dump({"exit_ip": exit_ip, "verdict": "CONVERTED" if converted else "NOT_CONVERTED"},
               f, ensure_ascii=False, indent=2)
    fp.close()


if __name__ == "__main__":
    main()
