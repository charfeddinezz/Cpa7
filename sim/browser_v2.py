#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Definitive funnel runner v2: exact field maps, parked-state diagnosis,
no blind loops. Usage: browser_v2.py <proxy_json> <proxy_index> <email>"""
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
CAPTCHA_BITS = ["captcha", "recaptcha", "hcaptcha", "turnstile", "challenge-platform",
                "just a moment", "verifying you are human"]
SKIP_TXT = ["privacy", "terms", "cookie", "login", "sign in", "unsubscribe",
            "do not sell", "back to top", "program requirements", "no, thank"]
P = {"first": "tonney", "last": "kamlo", "email": "",
     "address": "123 Test Delete Street", "city": "New York", "state": "NY",
     "zip": "10001", "phone": "91276465465",
     "dob_m": "12", "dob_d": "25", "dob_y": "2002", "gender": "M"}


def main():
    from playwright.sync_api import sync_playwright
    proxies = json.load(open(sys.argv[1]))
    p = proxies[int(sys.argv[2]) % len(proxies)]
    P["email"] = sys.argv[3]
    base = os.path.dirname(os.path.abspath(__file__))
    ts = time.strftime("%Y%m%d-%H%M%S")
    fp = open(os.path.join(base, "logs", "v2-%s.log" % ts), "w", encoding="utf-8")

    def tag(m):
        line = "%s %s" % (time.strftime("%H:%M:%S"), m)
        print(line, flush=True)
        fp.write(line + "\n")
        fp.flush()

    bridge = Bridge(p)
    exit_ip, converted, parked = "", False, 0
    last_sig = ""
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
        tag("🌐 خروج: %s" % (exit_ip or "?"))
        try:
            pg.goto("https://singingfiles.com/show.php?l=0&u=2214761&id=75222",
                    timeout=45000, wait_until="domcontentloaded")
        except Exception as e:
            tag("⚠️ تحميل: %s" % str(e)[:90])
        time.sleep(10)

        for cycle in range(1, 19):
            try:
                url, title = pg.url, pg.title()
            except Exception:
                tag("⚠️ فقدان الصفحة")
                break
            try:
                body = pg.evaluate("() => (document.body ? document.body.innerText : '').slice(0,4000)") or ""
            except Exception:
                body = ""
            low = (body + " " + title).lower()
            pct = ""
            try:
                m = re.search(r"(\d{1,3})\s*%", body)
                pct = m.group(1) if m else ""
            except Exception:
                pass
            tag("🔍 #%d [%s%%] '%s' | %s" % (cycle, pct or "-", title[:45] or "-", url[:75]))
            if any(k in low for k in CONFIRM_KWS) or any(x in url.lower() for x in THANKYOU_BITS):
                converted = True
                tag("🏆 تحويل مرصود!")
                break
            if any(k in low or k in url.lower() for k in CAPTCHA_BITS):
                tag("🧩 كابتشا — 15 ث...")
                time.sleep(15)
                continue
            # fill everything known
            n = 0
            for sel, val in [("input[type=email]:visible", P["email"]),
                             ("input[name*=first i]:visible", P["first"]),
                             ("input[name*=last i]:visible", P["last"]),
                             ("input[name*=address i]:visible", P["address"]),
                             ("input[name*=zip i]:visible", P["zip"]),
                             ("input[name*=city i]:visible", P["city"]),
                             ("input[name*=phone i]:visible, input[type=tel]:visible", P["phone"])]:
                try:
                    loc = pg.locator(sel).first
                    if loc.count() > 0 and loc.is_visible() and loc.is_editable() and not loc.input_value():
                        loc.fill(val, timeout=4000)
                        n += 1
                except Exception:
                    continue
            for nm, val in [("state", P["state"]), ("dob_month", P["dob_m"]),
                            ("dob_day", P["dob_d"]), ("dob_year", P["dob_y"]),
                            ("gender", P["gender"])]:
                try:
                    loc = pg.locator("select[name=%s]:visible" % nm).first
                    if loc.count() > 0:
                        loc.select_option(val, timeout=4000)
                        n += 1
                except Exception:
                    continue
            try:
                for cb in pg.locator("input[type=checkbox]:visible").all():
                    try:
                        cb.check(timeout=2500, force=True)
                        n += 1
                    except Exception:
                        continue
            except Exception:
                pass
            # click: exact priority words first
            clicked = None
            try:
                els = pg.locator("button:visible, a:visible, input[type=submit]:visible").all()[:40]
            except Exception:
                els = []
            pri, fall = [], []
            for el in els:
                try:
                    tn = el.evaluate("e => e.tagName")
                    t = ((el.inner_text() if tn in ("BUTTON", "A") else el.get_attribute("value")) or "").strip()
                except Exception:
                    continue
                if not t or len(t) > 35 or any(k in t.lower() for k in SKIP_TXT):
                    continue
                (pri if t.lower() in ("yes", "continue", "submit", "next", "claim",
                                      "claim now", "get started", "start", "join now",
                                      "sign up", "register") else fall).append((t, el))
            for t, el in (pri or fall):
                try:
                    el.click(timeout=5000)
                    clicked = t
                    break
                except Exception:
                    continue
            tag("   تعبئة=%d نقر=%s" % (n, (clicked or "-")[:30]))
            sig = "%s|%s|%d|%s" % (url.split("?")[0][-40:], title[:30], n, (clicked or "-")[:20])
            if sig == last_sig:
                parked += 1
            else:
                parked, last_sig = 0, sig
            if parked >= 3:
                tag("🅿️ ركن تام (3 لقطات متطابقة) — تشخيص بدل الطرق الأعمى:")
                try:
                    dump = pg.evaluate("""() => {
                      const o = [];
                      document.querySelectorAll('button, a, input[type=submit]').forEach(el => {
                        if (el.offsetParent === null) return;
                        o.push(el.tagName + '|' + ((el.innerText||el.value||'')+'').trim().slice(0,45)); });
                      return [...new Set(o)].slice(0,20); }""")
                    for d in dump:
                        tag("     . " + d)
                except Exception as e:
                    tag("     dump-fail %s" % str(e)[:80])
                break
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
    with open(os.path.join(base, "logs", "v2-%s.json" % ts), "w", encoding="utf-8") as f:
        json.dump({"exit_ip": exit_ip, "verdict": "CONVERTED" if converted else "NOT_CONVERTED"},
                  f, ensure_ascii=False, indent=2)
    fp.close()


if __name__ == "__main__":
    main()
