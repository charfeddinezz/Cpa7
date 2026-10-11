#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Real headless-browser task runner: residential proxy + mobile emulation +
stealth init + funnel engine (analyze -> act -> verify) + RSS check.
Usage: browser_task.py "<task_url>" <proxy_json> <proxy_index> [max_cycles]
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

CONFIRM_KWS = ["thank you", "thanks for", "congratulations", "successfully",
               "order confirmed", "payment successful", "account created",
               "check your email", "registration complete", "survey completed",
               "entry confirmed", "submission received", "winner", "claim confirmed"]
THANKYOU_BITS = ["/thank", "thank-you", "thankyou", "/success", "confirmation",
                 "order-received", "order-confirm", "/receipt", "signup-success",
                 "register-success", "payment-success", "checkout-success", "/finish"]
CAPTCHA_BITS = ["captcha", "recaptcha", "hcaptcha", "turnstile", "challenge-platform",
                "just a moment", "verifying you are human"]
RSS_TMPL = ("https://www.cpagrip.com/common/lead_check_rss.php?user_id=2214761"
            "&key=6358f4b2642c7c6cf721b4fba5244e17&time=1day&check=ip&value=%s")

PERSONA = {"first": "tonney", "last": "kamlo",
           "email": "tonney.kamlo.test@singingfiles.com",
           "address": "123 Test Delete Street", "city": "New York",
           "state": "NY", "zip": "10001", "phone": "91276465465",
           "dob": "12/25/2002"}

STEALTH_JS = """() => {
  try {
    // Seeded pools mirror the Android shield (fixed 8/8 every session = correlatable)
    const SEED = (window.__cpaSimSeed || (Date.now() % 100000)) >>> 0;
    const H = (n) => { let x = ((n ^ SEED) + 0x9e3779b9) >>> 0; x = Math.imul(x ^ (x >>> 16), 0x85ebca6b) >>> 0; x = Math.imul(x ^ (x >>> 13), 0xc2b2ae35) >>> 0; return (x ^ (x >>> 16)) >>> 0; };
    const CORES = [4, 6, 8, 8][H(101) % 4];
    const MEM = [4, 4, 8][H(102) % 3];
    Object.defineProperty(navigator, 'webdriver', { get: () => false, configurable: true });
    Object.defineProperty(navigator, 'platform', { get: () => 'Linux armv8l', configurable: true });
    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => CORES, configurable: true });
    Object.defineProperty(navigator, 'deviceMemory', { get: () => MEM, configurable: true });
    Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 5, configurable: true });
    if (!window.chrome) window.chrome = {};
    if (!window.chrome.runtime) window.chrome.runtime = {};
    const plugs = ['PDF Viewer','Chrome PDF Viewer','Chromium PDF Viewer','Microsoft Edge PDF Viewer','WebKit built-in PDF'];
    Object.defineProperty(navigator, 'plugins', { get: () => plugs.map(n => ({name:n})), configurable: true });
    try { delete window.ontouchstart; } catch(e) {}
    try { Object.defineProperty(window, 'ontouchstart', { value: null, writable: true, enumerable: true, configurable: true }); } catch(e) {}
    if (navigator.permissions && navigator.permissions.query) {
      const q = navigator.permissions.query.bind(navigator.permissions);
      navigator.permissions.query = (p) => q(p).then(s => ((p && (p.name==='camera'||p.name==='microphone') && s.state==='denied') ? {state:'prompt', onchange:null} : s));
    }
  } catch(e) {}
}"""


def log(msg, fp):
    line = "%s %s" % (time.strftime("%H:%M:%S"), msg)
    print(line, flush=True)
    try:
        fp.write(line + "\n")
        fp.flush()
    except Exception:
        pass


def rss_lead(ip):
    try:
        req = urllib.request.Request(
            RSS_TMPL % urllib.parse.quote(ip),
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


def analyze(page):
    """Snapshot DOM state for decisions."""
    try:
        url = page.url
    except Exception:
        url = ""
    try:
        title = page.title()
    except Exception:
        title = ""
    try:
        body = page.evaluate("() => (document.body ? document.body.innerText : '').slice(0,6000)")
    except Exception:
        body = ""
    low = (body + " " + title).lower()
    try:
        buttons = page.locator("button:visible, a[href]:visible, input[type=submit]:visible").count()
    except Exception:
        buttons = 0
    try:
        inputs = page.locator("input:visible, select:visible, textarea:visible").count()
    except Exception:
        inputs = 0
    cta = ""
    try:
        els = page.locator("button:visible, a.btn:visible, a[href]:visible").all()
        for el in els[:25]:
            try:
                t = (el.inner_text() or "").strip()
            except Exception:
                continue
            if 2 <= len(t) <= 40 and not re.search(r"privacy|terms|cookie|login|sign in", t, re.I):
                cta = t
                break
    except Exception:
        pass
    return {"url": url, "title": title, "text_len": len(body), "buttons": buttons,
            "inputs": inputs, "cta": cta,
            "captcha": any(k in low or k in url.lower() for k in CAPTCHA_BITS),
            "kw": [k for k in CONFIRM_KWS if len(k) >= 6 and k in low],
            "thankyou": any(b in url.lower() for b in THANKYOU_BITS)}


def fill_form(page, tag):
    """Fill visible form fields with persona data. Returns fields filled."""
    filled = 0
    rules = [
        ("input[type=email], input[name*=mail i]", PERSONA["email"]),
        ("input[type=tel], input[name*=phone i]", PERSONA["phone"]),
        ("input[name*=zip i], input[name*=postal i]", PERSONA["zip"]),
        ("input[name*=city i]", PERSONA["city"]),
        ("input[name*=address i]:not([name*=address2 i]):not([name*=address_2 i])", PERSONA["address"]),
        ("input[name*=first i], input[name*=fname i]", PERSONA["first"]),
        ("input[name*=last i], input[name*=lname i], input[name*=surname i]", PERSONA["last"]),
    ]
    for sel, val in rules:
        try:
            loc = page.locator(sel).first
            if loc.count() > 0 and loc.is_visible() and loc.is_editable():
                cur = loc.input_value()
                if not cur:
                    loc.fill(val, timeout=4000)
                    filled += 1
        except Exception:
            continue
    # state select
    try:
        loc = page.locator("select[name*=state i]").first
        if loc.count() > 0 and loc.is_visible():
            try:
                loc.select_option("NY", timeout=3000)
                filled += 1
            except Exception:
                pass
    except Exception:
        pass
    # required checkboxes (terms/consent)
    for sel in ["input[type=checkbox][required]:not(:checked)",
                "input[name*=terms i]:not(:checked)",
                "input[name*=consent i]:not(:checked)",
                "input[name*=agree i]:not(:checked)"]:
        try:
            loc = page.locator(sel).first
            if loc.count() > 0 and loc.is_visible():
                loc.check(timeout=3000, force=True)
                filled += 1
        except Exception:
            continue
    tag("📝 تعبئة: %d حقول" % filled)
    return filled


def main():
    from playwright.sync_api import sync_playwright
    task_url = sys.argv[1]
    proxies = json.load(open(sys.argv[2]))
    p = proxies[int(sys.argv[3]) % len(proxies)]
    max_cycles = int(sys.argv[4]) if len(sys.argv) > 4 else 12
    if len(sys.argv) > 5 and "@" in sys.argv[5]:
        PERSONA["email"] = sys.argv[5].strip()
        log_extra = " (البريد: %s)" % PERSONA["email"]
    else:
        log_extra = ""
    base = os.path.dirname(os.path.abspath(__file__))
    os.makedirs(os.path.join(base, "logs", "shots"), exist_ok=True)
    ts = time.strftime("%Y%m%d-%H%M%S")
    fp = open(os.path.join(base, "logs", "browser-%s.log" % ts), "w", encoding="utf-8")
    tag = lambda m: log(m, fp)

    tag("🚀 مهمة متصفح حقيقية: %s%s" % (task_url[:90], log_extra))
    from socks_bridge import Bridge
    bridge = Bridge(p)
    tag("🌉 جسر محلي: 127.0.0.1:%d ← SOCKS5 upstream" % bridge.port)
    exit_ip, converted, detail = "", False, "init"
    with sync_playwright() as pw:
        browser = pw.chromium.launch(
            headless=True,
            proxy={"server": "http://127.0.0.1:%d" % bridge.port},
            args=["--disable-blink-features=AutomationControlled"])
        ctx = browser.new_context(
            user_agent=("Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 "
                        "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"),
            viewport={"width": 412, "height": 915}, device_scale_factor=2.6,
            is_mobile=True, has_touch=True, locale="en-US",
            timezone_id="America/New_York",
            geolocation={"latitude": 40.7128, "longitude": -74.0060},
            permissions=["geolocation"])
        ctx.add_init_script(STEALTH_JS)
        page = ctx.new_page()
        try:
            page.goto("https://api.ipify.org", timeout=30000)
            exit_ip = (page.inner_text("body") or "").strip()
        except Exception as e:
            exit_ip = ""
        tag("🌐 خروج المتصفح: %s" % (exit_ip or "?"))
        try:
            page.goto(task_url, timeout=45000, wait_until="domcontentloaded")
        except Exception as e:
            tag("⚠️ تحميل أولي: %s" % str(e)[:100])
        # settle redirect chain
        last_url, stable = "", 0
        for _ in range(30):
            time.sleep(1)
            try:
                u = page.url
            except Exception:
                break
            if u == last_url:
                stable += 1
                if stable >= 4:
                    break
            else:
                stable, last_url = 0, u
        same_title_streak = 0
        last_title = ""
        for cycle in range(1, max_cycles + 1):
            a = analyze(page)
            tag("🔍 دورة %d: '%s' | نص=%d أزرار=%d حقول=%d CTA='%s' | %s" % (
                cycle, a["title"][:50] or "-", a["text_len"], a["buttons"],
                a["inputs"], a["cta"][:30] or "-",
                ("CAPTCHA 🧩" if a["captcha"] else "نظيفة")))
            # Wrong-page eviction: same title + zero progress twice = abort (budget saver)
            if a["title"] and a["title"] == last_title and a["inputs"] == 0:
                same_title_streak += 1
            else:
                same_title_streak = 0
            last_title = a["title"]
            if same_title_streak >= 2 and not (a["kw"] or a["thankyou"]):
                tag("🛑 صفحة خارج المهمة مكررة (%s) — إجهاض موفر للميزانية." % a["title"][:40])
                break
            try:
                page.screenshot(path=os.path.join(base, "logs", "shots", "c%d.png" % cycle))
            except Exception:
                pass
            if a["kw"] or a["thankyou"]:
                converted, detail = True, "kw=%s thankyou=%s" % (a["kw"][:2], a["thankyou"])
                tag("🏆 تحويل مرصود في الصفحة!")
                break
            if a["captcha"]:
                tag("🧩 كابتشا — انتظار صبور 15 ث...")
                time.sleep(15)
                continue
            if a["inputs"] > 0:
                n = fill_form(page, tag, )
                # submit: first visible submit control
                submitted = False
                for sel in ["button[type=submit]:visible", "input[type=submit]:visible",
                            "button:has-text('Continue'):visible", "button:has-text('Submit'):visible",
                            "button:has-text('Claim'):visible", "button:has-text('Next'):visible"]:
                    try:
                        loc = page.locator(sel).first
                        if loc.count() > 0:
                            loc.click(timeout=5000)
                            submitted = True
                            tag("👆 نقر إرسال (%s)" % sel.split(":")[0])
                            break
                    except Exception:
                        continue
                if not submitted:
                    tag("⚠️ لا زر إرسال ظاهر — انتظار تغير DOM")
                time.sleep(6)
                continue
            if a["cta"]:
                try:
                    clicked = False
                    for el in page.locator("button:visible, a[href]:visible").all()[:25]:
                        try:
                            if (el.inner_text() or "").strip() == a["cta"]:
                                el.click(timeout=5000)
                                clicked = True
                                break
                        except Exception:
                            continue
                    tag("👆 نقر '%s' %s" % (a["cta"][:30], "✅" if clicked else "❌"))
                except Exception as e:
                    tag("⚠️ فشل النقر: %s" % str(e)[:80])
                time.sleep(6)
                continue
            tag("⏳ صفحة بلا إجراء واضح — انتظار 8 ث...")
            time.sleep(8)
        if not converted:
            try:
                a = analyze(page)
                converted = bool(a["kw"] or a["thankyou"])
                detail = "final-check kw=%s" % a["kw"][:2]
            except Exception:
                pass
        browser.close()
    try:
        bridge.stop()
    except Exception:
        pass
    if exit_ip:
        found, tsamp = rss_lead(exit_ip)
        tag("🔎 تحقق RSS (%s): lead_found=%s %s" % (exit_ip, found, tsamp))
        converted = converted or found
    verdict = "CONVERTED" if converted else "NOT_CONVERTED"
    tag("🏁 الحكم: %s (%s)" % (verdict, detail))
    with open(os.path.join(base, "logs", "browser-%s.json" % ts), "w", encoding="utf-8") as f:
        json.dump({"task": task_url, "exit_ip": exit_ip, "verdict": verdict,
                   "detail": detail}, f, ensure_ascii=False, indent=2)
    fp.close()


if __name__ == "__main__":
    main()
