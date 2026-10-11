#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Full sweep: every proxy gets one fresh-context funnel run + RSS check.
- Fresh browser context per attempt (cookies/storage isolated)
- Per-attempt canvas-noise seed (unlinkable rendering fingerprints)
- Radio groups answered, Google-dump quick-detect, parked diagnosis
- Referer rotation, respectful pacing, JSONL progress (resumable)
Usage: sweep_all.py <task_url> <email> <start> <count>
"""
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
P = {"first": "tonney", "last": "kamlo",
     "address": "123 Test Delete Street", "city": "New York", "state": "NY",
     "zip": "10001", "phone": "91276465465",
     "dob_m": "12", "dob_d": "25", "dob_y": "2002", "gender": "M"}
REFERERS = ["https://www.google.com/", "https://www.bing.com/",
            "https://duckduckgo.com/", "https://search.yahoo.com/"]
RSS_TMPL = ("https://www.cpagrip.com/common/lead_check_rss.php?user_id=2214761"
            "&key=6358f4b2642c7c6cf721b4fba5244e17&time=1day&check=ip&value=%s")
STATE_PATH = "/tmp/sweep_state.json"


def canvas_noise_js(seed):
    return ("""() => {
  var __s = (%d) >>> 0;
  function __h(n) { var x = ((n ^ __s) + 0x9e3779b9) >>> 0;
    x = Math.imul(x ^ (x >>> 16), 0x85ebca6b) >>> 0;
    x = Math.imul(x ^ (x >>> 13), 0xc2b2ae35) >>> 0; return (x ^ (x >>> 16)) >>> 0; }
  try {
    var orig = CanvasRenderingContext2D.prototype.getImageData;
    CanvasRenderingContext2D.prototype.getImageData = function(sx, sy, sw, sh) {
      var img = orig.apply(this, arguments);
      try {
        var d = img.data;
        for (var i = 0; i + 4 < d.length; i += 64) {
          if (d[i+3] > 16) { var h = __h(i); d[i + (h %% 3)] += ((h & 1) ? 1 : -1); }
        }
      } catch(e) {}
      return img;
    };
    var origTD = HTMLCanvasElement.prototype.toDataURL;
    HTMLCanvasElement.prototype.toDataURL = function() {
      var c = document.createElement('canvas');
      c.width = this.width; c.height = this.height;
      var x = c.getContext('2d');
      x.drawImage(this, 0, 0);
      var img = x.getImageData(0, 0, c.width, c.height);
      var d = img.data;
      for (var i = 0; i + 4 < d.length && i < 4096; i += 32) {
        if (d[i+3] > 16) { var h2 = __h(i + 7); d[i + (h2 %% 3)] += ((h2 & 1) ? 1 : -1); }
      }
      x.putImageData(img, 0, 0);
      return origTD.apply(c, arguments);
    };
  } catch(e) {}
}""" % (seed & 0xFFFFFFFF))


def load_state():
    try:
        return json.load(open(STATE_PATH))
    except Exception:
        return {"done": [], "conversions": []}


def save_state(st):
    try:
        json.dump(st, open(STATE_PATH, "w"))
    except Exception:
        pass


def rss_lead(ip):
    try:
        req = urllib.request.Request(RSS_TMPL % urllib.parse.quote(ip),
                                     headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=25) as r:
            import xml.etree.ElementTree as ET
            root = ET.fromstring(r.read(8192).decode("utf-8", errors="replace"))
            info = root.find("lead_info")
            if (info.findtext("lead_found") or "").strip().lower() == "true":
                return True, (info.findtext("lead_timestamp") or "").strip()
    except Exception:
        pass
    return False, ""


def main():
    from playwright.sync_api import sync_playwright
    task_url, email = sys.argv[1], sys.argv[2]
    start, count = int(sys.argv[3]), int(sys.argv[4])
    order = json.load(open("/tmp/proxy_sweep_order.json"))
    P_email = email
    import browser_task
    base = os.path.dirname(os.path.abspath(__file__))
    ts = time.strftime("%Y%m%d-%H%M%S")
    fp = open(os.path.join(base, "logs", "sweep-%s.log" % ts), "w", encoding="utf-8")

    def tag(m):
        line = "%s %s" % (time.strftime("%H:%M:%S"), m)
        print(line, flush=True)
        fp.write(line + "\n")
        fp.flush()

    st = load_state()
    done_ips = set(st["done"])
    with sync_playwright() as pw:
        for k in range(start, start + count):
            if k >= len(order):
                break
            entry = order[k]
            if entry["ip"] in done_ips:
                tag("⏭️ #%d %s مستخدم سابقاً — تخطي" % (k, entry["ip"]))
                continue
            tag("═══ محاولة #%d عبر %s (%s) ═══" % (k, entry["ip"], entry["isp"][:30]))
            bridge = Bridge(entry["proxy"])
            exit_ip, verdict, detail = "", "INIT", ""
            browser = pw.chromium.launch(
                headless=True, proxy={"server": "http://127.0.0.1:%d" % bridge.port},
                args=["--disable-blink-features=AutomationControlled"])
            # NOTE: per-attempt inline engine duplicated from run_attempt for clarity
            try:
                exit_ip, verdict, detail = attempt_inline(pw, browser, bridge, task_url, email, tag)
            except Exception as e:
                verdict, detail = "THROW", str(e)[:100]
                tag("💥 عطل: %s" % detail)
            try:
                browser.close()
            except Exception:
                pass
            try:
                bridge.stop()
            except Exception:
                pass
            found, tsamp = (False, "")
            if exit_ip:
                # RSS check EVERY attempt as instructed
                try:
                    req = urllib.request.Request(
                        ("https://www.cpagrip.com/common/lead_check_rss.php?user_id=2214761"
                         "&key=6358f4b2642c7c6cf721b4fba5244e17&time=1day&check=ip&value=%s") % exit_ip,
                        headers={"User-Agent": "Mozilla/5.0"})
                    with urllib.request.urlopen(req, timeout=25) as r:
                        import xml.etree.ElementTree as ET
                        root = ET.fromstring(r.read(8192).decode("utf-8", errors="replace"))
                        info = root.find("lead_info")
                        if (info.findtext("lead_found") or "").strip().lower() == "true":
                            found = True
                            tsamp = (info.findtext("lead_timestamp") or "").strip()
                except Exception as e:
                    tag("⚠️ RSS: %s" % str(e)[:80])
            tag("🔎 RSS (%s): lead_found=%s %s | الحكم: %s" % (
                exit_ip, found, tsamp,
                "CONVERTED" if (verdict == "CONVERTED" or found) else verdict))
            st["done"].append(exit_ip or entry["ip"])
            if found or verdict == "CONVERTED":
                st["conversions"].append({"proxy": entry["ip"], "ts": tsamp})
                tag("🏆 تحويل! (رصيد محتمل — تحقق من لوحتك)")
            save_state(st)
            time.sleep(15)
    tag("🏁 نهاية الدفعة. المحوَّل: %d" % len(st["conversions"]))
    fp.close()


def attempt_inline(pw, browser, bridge, task_url, email, tag):
    import re as _re
    ctx = browser.new_context(
        user_agent=("Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 "
                    "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"),
        viewport={"width": 412, "height": 915}, device_scale_factor=2.6,
        is_mobile=True, has_touch=True, locale="en-US",
        timezone_id="America/New_York",
        geolocation={"latitude": 40.7128, "longitude": -74.0060},
        permissions=["geolocation"])
    from browser_task import STEALTH_JS
    ctx.add_init_script(STEALTH_JS)
    ctx.add_init_script(canvas_noise_js_fn())
    pg = ctx.new_page()
    try:
        pg.goto("https://api.ipify.org", timeout=30000)
        exit_ip = (pg.inner_text("body") or "").strip()
    except Exception:
        exit_ip = ""
    tag("🌐 خروج=%s" % (exit_ip or "?"))
    try:
        pg.goto(task_url, timeout=45000, wait_until="domcontentloaded")
    except Exception as e:
        tag("⚠️ تحميل: %s" % str(e)[:80])
    time.sleep(9)
    last_sig, parked = "", 0
    for cycle in range(1, 9):
        try:
            url, title = pg.url, pg.title()
        except Exception:
            return exit_ip, "PAGE_LOST", ""
        if "google.com" in (url or "") and "singingfiles" not in (url or ""):
            return exit_ip, "DUMPED_TO_GOOGLE", url[:80]
        try:
            body = pg.evaluate("() => (document.body ? document.body.innerText : '').slice(0,4000)") or ""
        except Exception:
            body = ""
        low = (body + " " + title).lower()
        if any(k in low for k in CONFIRM_KWS) or any(x in url.lower() for x in THANKYOU_BITS):
            return exit_ip, "CONVERTED", "page-signals"
        if any(k in low or k in url.lower() for k in CAPTCHA_BITS):
            tag("🧩 كابتشا — 12 ث...")
            time.sleep(12)
            continue
        n = 0
        for sel, val in [("input[type=email]:visible", email),
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
            seen_g = set()
            for rb in pg.locator("input[type=radio]:visible").all():
                try:
                    gname = rb.get_attribute("name") or ""
                    if gname in seen_g:
                        continue
                    seen_g.add(gname)
                    rb.check(timeout=2500, force=True)
                    n += 1
                except Exception:
                    continue
        except Exception:
            pass
        try:
            for cb in pg.locator("input[type=checkbox]:visible").all():
                try:
                    cb.check(timeout=2500, force=True)
                    n += 1
                except Exception:
                    continue
        except Exception:
            pass
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
        tag("  #%d تعبئة=%d نقر=%s | %s" % (cycle, n, (clicked or "-")[:28], url[:70]))
        sig = "%s|%d|%s" % (url.split("?")[0][-40:], n, (clicked or "-")[:20])
        if sig == last_sig:
            parked += 1
        else:
            parked, last_sig = 0, sig
        if parked >= 2:
            return exit_ip, "PARKED", url[:80]
        time.sleep(6)
    return exit_ip, "CYCLES_SPENT", ""


_SEED_CTR = [0]


def canvas_noise_js_fn():
    _SEED_CTR[0] += 1
    import random
    return canvas_noise_js(random.randint(1, 2 ** 31))


if __name__ == "__main__":
    main()
