#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Proxied task run: full chain through a working US SOCKS5 proxy + RSS verify
against the PROXY exit IP (leads attribute to the exit IP, not ours).
Usage: python3 proxied_run.py "<task_url>" [attempts] [proxy_index]
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cpa_sim
from cpa_sim import analyze_page, next_url_from_html, plan_from_url, MAX_HOPS
from socks_client import SocksClient

RSS_TMPL = ("https://www.cpagrip.com/common/lead_check_rss.php?user_id=2214761"
            "&key=6358f4b2642c7c6cf721b4fba5244e17&time=1day&check=ip&value=%s")
REFERERS = ["https://www.google.com/", "https://www.bing.com/",
            "https://duckduckgo.com/"]
UA = ("Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36")


def check_lead(ip):
    url = RSS_TMPL % urllib.parse.quote(ip)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            root = ET.fromstring(r.read(8192).decode("utf-8", errors="replace"))
        info = root.find("lead_info")
        found = (info.findtext("lead_found") or "").strip().lower() == "true"
        return found, (info.findtext("lead_timestamp") or "").strip()
    except Exception as e:
        return False, "RSS_ERROR: %s" % str(e)[:100]


def main():
    if len(sys.argv) < 2:
        print('Usage: python3 proxied_run.py "<task_url>" [attempts] [proxy_index]')
        sys.exit(2)
    task_url = sys.argv[1].strip()
    budget = max(1, min(int(sys.argv[2]) if len(sys.argv) > 2 else 2, 4))
    pidx = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    base = os.path.dirname(os.path.abspath(__file__))
    log = cpa_sim.Logger(os.path.join(base, "logs"))
    pool = json.load(open(os.path.join(base, "working_proxies.json")))
    if pidx >= len(pool):
        log.log("error", "لا بروكسي شغال (index %d خارج النطاق)" % pidx)
        log.close()
        return
    proxy = pool[pidx]
    log.log("info", "🌐 البروكسي: خروج=%s (%s %s, %dms) — الاعتمادات مخفية" % (
        proxy["ip"], proxy["cc"], proxy["city"], proxy["ms"]))

    plan = plan_from_url(task_url)
    log.log("info", "🧠 الخطة '%s' (%d مراحل)" % (plan["name"], len(plan["categories"])))
    found0, ts0 = check_lead(proxy["ip"])
    log.log("info", "📊 خط أساس RSS لخروج البروكسي: lead_found=%s %s" % (
        found0, ts0 if found0 else ""))
    if found0:
        log.log("success", "🏆 تحويل موجود مسبقاً بتاريخ %s." % ts0)
        log.close()
        return

    client = SocksClient(proxy, UA)
    try:
        history = []
        success = False
        for n in range(1, budget + 1):
            ref = REFERERS[(n - 1) % len(REFERERS)]
            log.log("info", "🔁 المحاولة #%d عبر البروكسي (مُحيل=%s)" % (n, ref))
            hops, seen, cur, final = [], set(), task_url, None
            for i in range(1, MAX_HOPS + 1):
                if cur in seen:
                    break
                seen.add(cur)
                r = client.get(cur, referer=ref)
                hops.append({"n": i, "url": cur, "status": r.get("status"),
                             "bytes": r.get("bytes"), "error": r.get("error", "")})
                if not r["ok"]:
                    log.log("warning", "⚠️ قفزة %d: %s" % (i, r.get("error")))
                    break
                log.log("info", "🌐 قفزة %d: HTTP %s | %dB | %dms | كوكيز=%d" % (
                    i, r["status"], r["bytes"], r["ms"], r.get("cookies", 0)))
                if r["status"] in (301, 302, 303, 307, 308):
                    loc = r.get("location", "")
                    if not loc:
                        break
                    cur = urllib.parse.urljoin(cur, loc)
                    time.sleep(0.4)
                    continue
                if "text/html" not in r.get("ctype", ""):
                    final = r
                    break
                kind, target = next_url_from_html(r["body"], cur)
                if kind and target:
                    log.log("info", "🔀 (%s) → %s" % (kind, target[:100]))
                    cur = urllib.parse.urljoin(cur, target)
                    time.sleep(0.4)
                    continue
                final = r
                break
            res = {"hops": hops}
            if final is None:
                res.update(converted=False, reason="NO_LANDING")
            else:
                a = analyze_page(final["url"], final["body"])
                log.log("info", "🔍 '%s' | نص=%d | حقول بريد=%d نص=%d | أزرار=%d | CTA='%s' | فارغة=%s | كابتشا=%s | لوكر=%s | كلمات=%s" % (
                    a["title"] or "-", a["text_len"], a["emails"], a["texts"],
                    a["buttons"], a["cta"] or "-", a["blank"], a["captcha"],
                    a["locker"], a["kw_hits"][:3]))
                res["final_url"] = final["url"][:140]
                if a["kw_hits"] or a["thankyou_url"]:
                    res.update(converted=True, reason="SIGNALS")
                elif a["blank"]:
                    res.update(converted=False, reason="BLANK_WALL")
                elif a["captcha"]:
                    res.update(converted=False, reason="CAPTCHA")
                elif final["bytes"] == 0:
                    res.update(converted=False, reason="NETWORK_GATE")
                else:
                    res.update(converted=False, reason="NEEDS_BROWSER_INTERACTION")
            history.append({"attempt": n, "result": res})
            found, ts = check_lead(proxy["ip"])
            res["rss_lead"] = found
            if found:
                log.log("success", "🏆 التحويلة نجحت في المحاولة #%d! بتاريخ %s" % (n, ts))
                success = True
                break
            log.log("warning", "🔎 RSS بعد #%d: لا تحويل (%s)%s" % (
                n, res["reason"], " — إعادة" if n < budget else " — انتهت الميزانية"))
            if n < budget:
                wait = 30 + n * 10
                log.log("info", "⏳ فاصل %d ث..." % wait)
                time.sleep(wait)
        verdict = "CONVERTED" if success else "NOT_CONVERTED_BUDGET_SPENT"
        if not success:
            log.log("error", "⛔ لا تحويل بعد %d محاولات. الأخير: %s" % (
                budget, history[-1]["result"]["reason"] if history else "?"))
        path = os.path.join(base, "logs", "proxied-%s.json" % time.strftime("%Y%m%d-%H%M%S"))
        try:
            with open(path, "w", encoding="utf-8") as f:
                json.dump({"task": task_url, "proxy_ip": proxy["ip"],
                           "proxy_cc": proxy["cc"], "verdict": verdict,
                           "attempts": history}, f, ensure_ascii=False, indent=2)
            log.log("info", "📄 التقرير: %s" % path)
        except Exception as e:
            log.log("warning", "تعذر حفظ التقرير: %s" % e)
        log.log("info", "🏁 الحكم: %s" % verdict)
    finally:
        client.close()
        log.close()


if __name__ == "__main__":
    main()
