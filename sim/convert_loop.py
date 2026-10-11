#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Conversion loop: attempt task → verify via CPA Grip lead RSS → retry w/ variation.
BOUNDED by design: endless hammering from a flagged IP gets the account/IP
blacklisted and kills even legitimate chances. Stops on success or budget end.
Usage: python3 convert_loop.py "<task_url>" [max_attempts]
"""
import json
import os
import re
import socket
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

try:
    import socks
    HAVE_SOCKS = True
except ImportError:
    HAVE_SOCKS = False

_ORIG_SOCKET = socket.socket


def use_proxy(p):
    socks.set_default_proxy(socks.SOCKS5, p["host"], p["port"], True,
                            p["user"], p["pass"])
    socket.socket = socks.socksocket


def use_direct():
    try:
        socket.socket = _ORIG_SOCKET
    except Exception:
        pass

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cpa_sim
from cpa_sim import Client, analyze_page, next_url_from_html, plan_from_url, MAX_HOPS

RSS_TMPL = ("https://www.cpagrip.com/common/lead_check_rss.php?user_id=2214761"
            "&key=6358f4b2642c7c6cf721b4fba5244e17&time=1day&check=ip&value=%s")
REFERERS = ["https://www.google.com/", "https://www.bing.com/",
            "https://duckduckgo.com/", "https://search.yahoo.com/"]
UAS = [
    ("Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 "
     "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"),
    ("Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 "
     "(KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"),
    ("Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 "
     "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"),
]


def my_ip():
    try:
        with urllib.request.urlopen("https://api.ipify.org", timeout=15) as r:
            ip = r.read(64).decode().strip()
            return ip if ip.count(".") == 3 else ""
    except Exception:
        return ""


def load_proxies(path):
    try:
        with open(path) as f:
            return json.load(f)
    except Exception:
        return []


def check_lead(ip):
    url = RSS_TMPL % urllib.parse.quote(ip)
    req = urllib.request.Request(url, headers={"User-Agent": UAS[0]})
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            xml = r.read(8192).decode("utf-8", errors="replace")
        root = ET.fromstring(xml)
        info = root.find("lead_info")
        found = (info.findtext("lead_found") or "").strip().lower() == "true"
        return found, (info.findtext("lead_timestamp") or "").strip(), xml[:300]
    except Exception as e:
        return False, "", "RSS_ERROR: %s" % str(e)[:120]


def attempt(client, url, referer, attempt_no, log):
    log.log("info", "🔁 المحاولة #%d: مُحيل=%s" % (attempt_no, referer))
    hops = []
    seen = set()
    cur = url
    final = None
    for i in range(1, MAX_HOPS + 1):
        if cur in seen:
            break
        seen.add(cur)
        # per-attempt referer variation (unlinkability)
        r = client.get(cur, referer=referer)
        hops.append({"n": i, "url": cur, "status": r.get("status"),
                     "bytes": r.get("bytes"), "error": r.get("error", "")})
        if not r["ok"]:
            log.log("warning", "⚠️ قفزة %d فشلت: %s" % (i, r.get("error")))
            break
        if r["status"] in (301, 302, 303, 307, 308) or "text/html" not in r.get("ctype", ""):
            cur = r["url"]
            if cur == hops[-1]["url"]:
                final = r
                break
            time.sleep(0.4)
            continue
        kind, target = next_url_from_html(r["body"], r["url"])
        if kind and target:
            cur = urllib.parse.urljoin(r["url"], target)
            time.sleep(0.4)
            continue
        final = r
        break
    if final is None:
        return {"converted": False, "reason": "NO_LANDING", "hops": hops}
    a = analyze_page(final["url"], final["body"])
    log.log("info", "🔍 '%s' | نص=%d | أزرار=%d | فارغة=%s | كابتشا=%s | كلمات=%s" % (
        a["title"] or "-", a["text_len"], a["buttons"], a["blank"],
        a["captcha"], a["kw_hits"][:2]))
    if a["kw_hits"] or a["thankyou_url"]:
        return {"converted": True, "reason": "SIGNALS", "hops": hops,
                "kw": a["kw_hits"][:3]}
    if a["blank"]:
        return {"converted": False, "reason": "BLANK_WALL", "hops": hops}
    if a["captcha"]:
        return {"converted": False, "reason": "CAPTCHA", "hops": hops}
    if final["bytes"] == 0:
        return {"converted": False, "reason": "NETWORK_GATE", "hops": hops}
    return {"converted": False, "reason": "NEEDS_BROWSER_INTERACTION", "hops": hops,
            "final_url": final["url"][:120]}


def main():
    if len(sys.argv) < 2:
        print('Usage: python3 convert_loop.py "<task_url>" [max_attempts] [--proxies file]')
        sys.exit(2)
    args = sys.argv[1:]
    proxy_file = ""
    if "--proxies" in args:
        i = args.index("--proxies")
        proxy_file = args[i + 1] if i + 1 < len(args) else ""
        args = [a for j, a in enumerate(args) if j not in (i, i + 1)]
    task_url = args[0].strip()
    budget = int(args[1]) if len(args) > 1 else 4
    budget = max(1, min(budget, 6))
    proxies = load_proxies(proxy_file) if proxy_file else []
    if proxy_file and not proxies:
        print("no proxies loaded from %s" % proxy_file)
        sys.exit(3)
    if proxies and not HAVE_SOCKS:
        print("PySocks required for proxy mode")
        sys.exit(4)
    base = os.path.dirname(os.path.abspath(__file__))
    log = cpa_sim.Logger(os.path.join(base, "logs"))

    if not proxies:
        ip = my_ip()
        log.log("info", "🌐 IP الخروج المباشر (بلا بروكسي): %s" % (ip or "?"))
        found0, ts0, _ = check_lead(ip) if ip else (False, "", "")
        log.log("info", "📊 خط أساس RSS: lead_found=%s %s" % (found0, "(موجود مسبقاً!)" if found0 else ""))
        if found0:
            log.log("success", "🏆 تحويل موجود مسبقاً لهذا IP بتاريخ %s — لا حاجة للمحاولات." % ts0)
            log.close()
            return
    else:
        ip = ""
        log.log("info", "🌐 وضع البروكسي: %d جلسات سكنية أمريكية — كل محاولة بخروج مختلف" % len(proxies))

    plan = plan_from_url(task_url)
    log.log("info", "🧠 الخطة '%s' (%d مراحل) — بدء حلقة التحويل (الميزانية: %d محاولات بفواصل محترمة)" % (
        plan["name"], len(plan["categories"]), budget))

    history = []
    success = False
    for n in range(1, budget + 1):
        ref = REFERERS[(n - 1) % len(REFERERS)]
        exit_ip = ip
        if proxies:
            p = proxies[(n - 1) % len(proxies)]
            try:
                use_proxy(p)
                exit_ip = my_ip()
            except Exception as e:
                log.log("warning", "⚠️ تعذر تفعيل البروكسي للمحاولة #%d: %s" % (n, str(e)[:80]))
                use_direct()
                continue
            log.log("info", "🌐 المحاولة #%d عبر خروج سكني: %s" % (n, exit_ip or "?"))
        client = Client(log)  # fresh cookie session per attempt/IP
        res = attempt(client, task_url, ref, n, log)
        use_direct()
        history.append({"attempt": n, "referer": ref, "exit_ip": exit_ip, "result": res})
        found, ts, _ = check_lead(exit_ip) if exit_ip else (False, "", "")
        res["rss_lead"] = found
        if found:
            log.log("success", "🏆 نجحت التحويلة في المحاولة #%d! IP=%s بتاريخ %s" % (n, exit_ip, ts))
            success = True
            break
        log.log("warning", "🔎 تحقق RSS بعد المحاولة #%d (IP=%s): لا تحويل (%s) — %s" % (
            n, exit_ip, res["reason"],
            "إعادة المحاولة بتنويع" if n < budget else "انتهت الميزانية"))
        if n < budget:
            wait = 25 + n * 10
            log.log("info", "⏳ فاصل محترم %d ث قبل المحاولة التالية (ضد كشف السرعة)..." % wait)
            time.sleep(wait)
    use_direct()

    verdict = "CONVERTED" if success else "NOT_CONVERTED_BUDGET_SPENT"
    if not success:
        log.log("error", "⛔ بعد %d محاولات: لا تحويل. السبب الغالب: %s" % (
            budget, history[-1]["result"]["reason"] if history else "?"))
        log.log("info", "💡 التالي على الجهاز: بروكسي سكني أمريكي + بصمة جوال + نقرة وتعبئة حقيقية بالمتصفح.")
    path = os.path.join(base, "logs", "loop-%s.json" % time.strftime("%Y%m%d-%H%M%S"))
    try:
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"task": task_url, "ip": ip, "verdict": verdict,
                       "attempts": history}, f, ensure_ascii=False, indent=2)
        log.log("info", "📄 تقرير الحلقة: %s" % path)
    except Exception as e:
        log.log("warning", "تعذر حفظ التقرير: %s" % e)
    log.log("info", "🏁 الحكم النهائي: %s" % verdict)
    log.close()


if __name__ == "__main__":
    main()
