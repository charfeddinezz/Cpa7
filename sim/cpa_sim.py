#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CPA task simulation environment — runnable anywhere Python exists.
Pipeline (mirrors the Android app): funnel plan → redirect chain →
page analysis → funnel walk → conversion verdict.
Usage: python3 cpa_sim.py "<url>"
"""
import html.parser
import http.cookiejar
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime

MOBILE_UA = ("Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 "
             "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36")
LANG = "en-US"
TIMEOUT = 20
MAX_HOPS = 8

UNIVERSAL_CATS = ["Offer Click", "Survey / Quiz", "Email Submit", "Zip Submit",
                  "Lead Gen Form", "PIN Submit", "Terms Agreement",
                  "Skip Upsells", "Confirmation"]
CONFIRM_KWS = ["thank you", "thanks for", "congratulations", "successfully",
               "order confirmed", "payment successful", "account created",
               "check your email", "registration complete", "survey completed",
               "entry confirmed", "submission received"]
THANKYOU_BITS = ["/thank", "thank-you", "thankyou", "/success", "confirmation",
                 "order-received", "order-confirm", "/receipt", "signup-success",
                 "register-success", "payment-success", "checkout-success",
                 "/finish", "lead-success"]
CAPTCHA_BITS = ["captcha", "recaptcha", "hcaptcha", "turnstile", "cf-challenge",
                "just a moment", "verifying you are human", "challenge-platform"]
LOCKER_BITS = ["locker", "unlock", "contentlock", "call_locker", "ogads",
               "adscend", "cpaLead", "fileice"]


class Logger:
    def __init__(self, logdir):
        os.makedirs(logdir, exist_ok=True)
        ts = datetime.now().strftime("%Y%m%d-%H%M%S")
        self.path = os.path.join(logdir, "sim-%s.log" % ts)
        self.fp = open(self.path, "w", encoding="utf-8")

    def log(self, level, msg):
        line = "%s [%s] %s" % (datetime.now().strftime("%H:%M:%S"), level.upper(), msg)
        print(line, flush=True)
        try:
            self.fp.write(line + "\n")
            self.fp.flush()
        except Exception:
            pass

    def close(self):
        try:
            self.fp.close()
        except Exception:
            pass


class TextGrabber(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.texts = []
        self.inputs = []
        self.buttons = []
        self.title = ""
        self._in_title = False
        self.meta_refresh = ""
        self.noscript_links = []
        self._in_noscript_a = False

    def handle_starttag(self, tag, attrs):
        a = dict((k.lower(), v or "") for k, v in attrs)
        if tag == "title":
            self._in_title = True
        elif tag == "meta" and a.get("http-equiv", "").lower() == "refresh":
            self.meta_refresh = a.get("content", "")
        elif tag == "input":
            self.inputs.append({"type": a.get("type", "text").lower(),
                                "name": a.get("name", "")})
        elif tag == "button":
            self.buttons.append({"text": "", "collect": True})
        elif tag == "a":
            href = a.get("href", "")
            if href.startswith("http"):
                self.buttons.append({"text": "", "href": href, "collect": False})
            if self._in_noscript_a and href:
                self.noscript_links.append(href)
        elif tag == "noscript":
            self._in_noscript_a = True

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False
        if tag == "noscript":
            self._in_noscript_a = False

    def handle_data(self, data):
        t = data.strip()
        if not t:
            return
        if self._in_title:
            self.title += t + " "
        else:
            self.texts.append(t)
        for b in self.buttons:
            if b.get("collect") and len(b["text"]) < 120:
                b["text"] += (" " + t) if b["text"] else t


def next_url_from_html(page, base_url):
    """Find client-side redirect target: meta refresh, JS location, noscript link."""
    m = re.search(
        r'<meta[^>]+http-equiv=["\']?refresh["\']?[^>]+content=["\']?\s*\d+\s*;\s*url=([^"\'>\s]+)',
        page, re.IGNORECASE)
    if m:
        return ("meta-refresh", html.unescape(m.group(1)).strip())
    m = re.search(
        r'(?:window\.)?(?:location\.href|location\s*=|top\.location\s*=|location\.replace\s*\()\s*["\']([^"\']+)["\']',
        page)
    if m:
        return ("js-redirect", m.group(1).strip())
    g = TextGrabber()
    try:
        g.feed(page)
    except Exception:
        pass
    if g.noscript_links:
        return ("noscript-link", g.noscript_links[0])
    return (None, "")


class Client:
    def __init__(self, log):
        self.log = log
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar),
            urllib.request.HTTPRedirectHandler())

    def get(self, url, referer="https://www.google.com/", ua=None):
        req = urllib.request.Request(url, headers={
            "User-Agent": ua or MOBILE_UA,
            "Accept-Language": LANG,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer": referer,
        })
        t0 = time.time()
        try:
            with self.opener.open(req, timeout=TIMEOUT) as r:
                raw = r.read(600 * 1024)
                ms = int((time.time() - t0) * 1000)
                ctype = r.headers.get("Content-Type", "")
                enc = r.headers.get_content_charset() or "utf-8"
                try:
                    body = raw.decode(enc, errors="replace")
                except Exception:
                    body = ""
                return {"ok": True, "status": r.status, "url": r.geturl(),
                        "ctype": ctype, "bytes": len(raw), "ms": ms, "body": body,
                        "cookies": len(list(self.jar))}
        except urllib.error.HTTPError as e:
            return {"ok": False, "status": e.code, "url": url, "error": "HTTP %s" % e.code,
                    "bytes": 0, "ms": int((time.time() - t0) * 1000), "body": "", "cookies": 0}
        except Exception as e:
            return {"ok": False, "status": 0, "url": url, "error": "%s: %s" % (type(e).__name__, str(e)[:120]),
                    "bytes": 0, "ms": int((time.time() - t0) * 1000), "body": "", "cookies": 0}


def plan_from_url(raw_url):
    """Python port of TaskCategoryPlanner.extractFunnelPlanFromUrl (core)."""
    u = raw_url.strip()
    if not u.lower().startswith("http"):
        u = "https://" + u
    p = urllib.parse.urlparse(u)
    host = (p.hostname or "").lower()
    path = (p.path or "").lower()
    query = (p.query or "").lower()
    full = (u + " " + host + " " + path + " " + query).lower()
    domain = host[4:] if host.startswith("www.") else host
    domain = (domain.split(".")[0] if domain else "smartweb").capitalize()
    m = re.search(r"[?&](?:id|offer_id|oid|camp_id|cid|s)=([^&#]+)", u, re.IGNORECASE)
    if not m:
        m = re.search(r"/(\d{4,12})(?:[/?#]|$)", path)
    oid = m.group(1) if m else ""
    tracking = (path.endswith(".php") or "u=" in query or "id=" in query
                or "l=" in query or "redirect" in full or "track" in full
                or "click" in full or re.search(r"/\d+/\d+", path) is not None)
    locker = any(k in full for k in ("locker", "unlock", "verify"))
    name = "%s #%s" % (domain, oid) if oid else ("%s Smart Redirect" % domain if tracking else "%s Auto Flow" % domain)
    return {"name": name, "url": u, "offer_id": oid, "tracking": tracking,
            "locker_hint": locker, "mode": "mode1", "duration": 60,
            "categories": UNIVERSAL_CATS}


def analyze_page(url, html):
    g = TextGrabber()
    try:
        g.feed(html)
    except Exception:
        pass
    body_text = " ".join(g.texts)
    low = (body_text + " " + g.title).lower()
    inputs = g.inputs
    emails = sum(1 for i in inputs if i["type"] in ("email",) or "mail" in i["name"])
    tels = sum(1 for i in inputs if i["type"] in ("tel",) or "phone" in i["name"])
    texts = sum(1 for i in inputs if i["type"] in ("text",))
    pwds = sum(1 for i in inputs if i["type"] == "password")
    radios = sum(1 for i in inputs if i["type"] == "radio")
    checks = sum(1 for i in inputs if i["type"] == "checkbox")
    cta = ""
    for b in g.buttons:
        t = b.get("text", "").strip()
        if 2 <= len(t) <= 40:
            cta = t
            break
    blank = (len(body_text.strip()) < 60 and not inputs and not g.buttons
             and len(g.title.strip()) < 4)
    return {
        "title": g.title.strip()[:100], "text_len": len(body_text),
        "emails": emails, "tels": tels, "texts": texts, "pwds": pwds,
        "radios": radios, "checks": checks, "buttons": len(g.buttons),
        "cta": cta, "blank": blank,
        "captcha": any(k in low or k in url.lower() for k in CAPTCHA_BITS),
        "locker": any(k in low or k in url.lower() for k in LOCKER_BITS),
        "kw_hits": [k for k in CONFIRM_KWS if len(k) >= 6 and k in low],
        "thankyou_url": any(b in url.lower() for b in THANKYOU_BITS),
    }


def main():
    if len(sys.argv) < 2:
        print('Usage: python3 cpa_sim.py "<url>"')
        sys.exit(2)
    start_url = sys.argv[1].strip()
    base = os.path.dirname(os.path.abspath(__file__))
    log = Logger(os.path.join(base, "logs"))
    log.log("info", "🚀 بدء مهمة محاكاة كاملة: %s" % start_url)

    # ── Stage 0: funnel plan ──
    plan = plan_from_url(start_url)
    log.log("info", "🧠 الخطة: '%s' | offer_id=%s | tracking=%s | mode=%s | %ss | %d مراحل" % (
        plan["name"], plan["offer_id"] or "-", plan["tracking"], plan["mode"],
        plan["duration"], len(plan["categories"])))
    log.log("info", "🧠 المراحل: %s" % " ➔ ".join(plan["categories"]))

    # ── Stage 1: redirect chain ──
    client = Client(log)
    hops = []
    seen = set()
    url = start_url
    final = None
    for i in range(1, MAX_HOPS + 1):
        if url in seen:
            log.log("warning", "⏭️ حلقة تحويل عند %s — إيقاف" % url[:80])
            break
        seen.add(url)
        r = client.get(url)
        hops.append({"n": i, "url": url, "status": r.get("status"),
                     "bytes": r.get("bytes"), "ms": r.get("ms"),
                     "error": r.get("error", "")})
        if not r["ok"]:
            log.log("error", "❌ القفزة %d فشلت: %s ← %s" % (i, r.get("error"), url[:90]))
            break
        log.log("info", "🌐 القفزة %d: HTTP %s | %d bytes | %dms | كوكيز=%d ← %s" % (
            i, r["status"], r["bytes"], r["ms"], r["cookies"], url[:90]))
        if r["status"] in (301, 302, 303, 307, 308) or "text/html" not in r.get("ctype", ""):
            url = r["url"]
            if url == hops[-1]["url"] or i == MAX_HOPS:
                final = r
                break
            continue
        kind, target = next_url_from_html(r["body"], r["url"])
        if kind and target:
            log.log("info", "🔀 تحويل (%s) → %s" % (kind, target[:100]))
            url = urllib.parse.urljoin(r["url"], target)
            time.sleep(0.4)
            continue
        final = r
        break

    report = {"task": start_url, "plan": plan["name"], "hops": hops}
    if final is None:
        log.log("error", "⛔ لا صفحة نهائية — انتهت السلسلة بفشل/حلقة.")
        report["verdict"] = "NO_LANDING"
        finish(log, report)
        return

    log.log("success", "🏁 الصفحة النهائية: %s (%d bytes)" % (final["url"][:100], final["bytes"]))

    # ── Stage 2: page analysis ──
    a = analyze_page(final["url"], final["body"])
    log.log("info", "🔍 العنوان: '%s' | نص=%d حرف | حقول: بريد=%d هاتف=%d نص=%d | أزرار=%d | CTA='%s'" % (
        a["title"] or "(بلا عنوان)", a["text_len"], a["emails"], a["tels"],
        a["texts"], a["buttons"], a["cta"] or "-"))
    report["analysis"] = a

    # ── Stage 3: funnel walk (simulated decisions) ──
    for idx, stage in enumerate(plan["categories"], 1):
        if a["blank"]:
            log.log("warning", "⬜ المرحلة %d/%d %s: صفحة فارغة — قرار محاكى: حرق البروكسي + تدوير" % (
                idx, len(plan["categories"]), stage))
            break
        if a["captcha"]:
            log.log("warning", "🧩 المرحلة %d/%d %s: كابتشا مرصودة — قرار محاكى: انتظار صبور" % (
                idx, len(plan["categories"]), stage))
            break
        action = {
            "Offer Click": "نقر CTA '%s'" % (a["cta"] or "الزر الرئيسي"),
            "Survey / Quiz": "إجابة أسئلة (%d مجموعات)" % a["radios"],
            "Email Submit": "تعبئة بريد (%d حقول)" % max(a["emails"], 1),
            "Zip Submit": "تعبئة رمز بريدي",
            "Lead Gen Form": "تعبئة استمارة (%d حقول نصية)" % a["texts"],
            "PIN Submit": "تعبئة PIN",
            "Terms Agreement": "تأشير الشروط",
            "Skip Upsells": "تخطي العروض الإضافية",
            "Confirmation": "تأكيد نهائي",
        }.get(stage, "تنفيذ")
        if stage == "Offer Click" and a["locker"]:
            action += " + مسار لوكر 🔒"
        log.log("info", "▶️ المرحلة %d/%d %s: %s" % (idx, len(plan["categories"]), stage, action))
        time.sleep(0.3)

    # ── Stage 4: verdict ──
    if a["kw_hits"] or a["thankyou_url"]:
        log.log("success", "🏆 تحويل مؤكد: كلمات=%s | رابط شكر=%s" % (a["kw_hits"][:3], a["thankyou_url"]))
        report["verdict"] = "CONVERTED"
    elif a["blank"]:
        log.log("error", "⛔ الحكم: جدار أبيض (صفحة فارغة بعد التحميل) — يحتاج بروكسي سكنياً + بصمة جوال.")
        report["verdict"] = "BLANK_WALL"
    elif a["captcha"]:
        log.log("warning", "⚠️ الحكم: تحدي تحقق بشري — يحتاج انتظاراً/حلاً قبل المتابعة.")
        report["verdict"] = "CAPTCHA"
    elif final["bytes"] == 0:
        log.log("error", "⛔ الحكم: استجابة فارغة من الشبكة — تصفية على مستوى IP/TLS.")
        report["verdict"] = "NETWORK_GATE"
    else:
        log.log("info", "ℹ️ الحكم: صفحة تفاعلية بلا إشارة تحويل — تحتاج متصفحاً حقيقياً للنقر والتعبئة.")
        report["verdict"] = "INTERACTIVE_NO_CONVERSION"
    finish(log, report)


def finish(log, report):
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "logs",
                        "report-%s.json" % datetime.now().strftime("%Y%m%d-%H%M%S"))
    try:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        log.log("info", "📄 التقرير: %s" % path)
    except Exception as e:
        log.log("warning", "تعذر حفظ التقرير: %s" % e)
    log.log("info", "🏁 الحكم النهائي: %s" % report.get("verdict"))
    log.close()


if __name__ == "__main__":
    main()
