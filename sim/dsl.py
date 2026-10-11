#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Task DSL + interpreter — define CPA tasks declaratively, run them here.

Task spec (Python dict):
  {"name": str, "start_url": str,
   "proxy": {"file": path, "index": int} (optional),
   "referer": str (optional),
   "retry_until_converted": int (default 1),
   "steps": [
     {"do": "goto", "url": "...", "as": "landing"},
     {"do": "submit", "form_action_contains": "cash1000" | "form_id": "...",
      "fields": {...}, "as": "step1"},
     {"do": "verify", "type": "rss|keywords|url", "ip_from": "proxy|value:1.2.3.4"},
     {"do": "wait", "seconds": 20},
     {"do": "log", "message": "..."},
   ]}

Usage: python3 dsl.py tasks/<name>.py [attempts]
"""
import html.parser
import http.cookiejar
import json
import os
import re
import socket
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cpa_sim
from cpa_sim import Client, analyze_page, next_url_from_html, plan_from_url

try:
    import socks
    HAVE_SOCKS = True
except ImportError:
    HAVE_SOCKS = False

_ORIG_SOCKET = socket.socket
RSS_TMPL = ("https://www.cpagrip.com/common/lead_check_rss.php?user_id=2214761"
            "&key=6358f4b2642c7c6cf721b4fba5244e17&time=1day&check=ip&value=%s")
MAX_HOPS = 8


class Ctx:
    def __init__(self, log, referer):
        self.log = log
        self.referer = referer
        self.client = Client(log)
        self.pages = {}      # as-name -> {"url", "body", "status"}
        self.vars = {}
        self.exit_ip = ""
        self.converted = False


def set_proxy(p):
    socks.set_default_proxy(socks.SOCKS5, p["host"], p["port"], True, p["user"], p["pass"])
    socket.socket = socks.socksocket


def clear_proxy():
    try:
        socket.socket = _ORIG_SOCKET
    except Exception:
        pass


def my_ip():
    try:
        with urllib.request.urlopen("https://api.ipify.org", timeout=15) as r:
            ip = r.read(64).decode().strip()
            return ip if ip.count(".") == 3 else ""
    except Exception:
        return ""


def rss_lead(ip):
    url = RSS_TMPL % urllib.parse.quote(ip)
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            root = ET.fromstring(r.read(8192).decode("utf-8", errors="replace"))
            info = root.find("lead_info")
            if (info.findtext("lead_found") or "").strip().lower() == "true":
                return True, (info.findtext("lead_timestamp") or "").strip()
    except Exception:
        pass
    return False, ""


def do_goto(ctx, step):
    url = step["url"]
    cur, hops, final = url, [], None
    seen = set()
    for _ in range(MAX_HOPS):
        if cur in seen:
            break
        seen.add(cur)
        r = ctx.client.get(cur, referer=ctx.referer)
        hops.append((r.get("status"), cur[:80], r.get("bytes")))
        ctx.log.log("info", "  hop: HTTP %s %dB ← %s" % (r.get("status"), r.get("bytes"), cur[:85]))
        if not r["ok"]:
            return False, "hop-fail %s" % r.get("error", "")
        if r["status"] in (301, 302, 303, 307, 308) or "text/html" not in r.get("ctype", ""):
            if r["url"] == cur:
                final = r
                break
            cur = r["url"]
            time.sleep(0.3)
            continue
        kind, target = next_url_from_html(r["body"], r["url"])
        if kind and target:
            cur = urllib.parse.urljoin(r["url"], target)
            time.sleep(0.3)
            continue
        final = r
        break
    if final is None:
        return False, "no-landing (%d hops)" % len(hops)
    ctx.pages[step.get("as", "page")] = final
    ctx.log.log("info", "🌐 goto: %d قفزات ← %s (%dB)" % (len(hops), final["url"][:90], final["bytes"]))
    return True, ""


def find_form(html, step):
    blocks = re.findall(r"<form\b.*?</form>", html, re.I | re.S)
    want_action = (step.get("form_action_contains") or "").lower()
    want_id = (step.get("form_id") or "").lower()
    for blk in blocks:
        tag = blk[:blk.find(">") + 1]
        if want_id and ('id="%s"' % want_id).lower() not in tag.lower():
            continue
        if want_action:
            am = re.search(r'action="([^"]+)"', tag, re.I)
            if not am or want_action not in am.group(1).lower():
                continue
        return blk
    return ""


def do_submit(ctx, step):
    src = ctx.pages.get(step.get("from", "landing"))
    if not src:
        return False, "no source page (run goto first)"
    blk = find_form(src["body"], step)
    if not blk:
        return False, "form not found"
    tag = blk[:blk.find(">") + 1]
    am = re.search(r'action="([^"]+)"', tag, re.I)
    action = am.group(1).replace("&#x3D;", "=").replace("&amp;", "&") if am else src["url"]
    mm = re.search(r'method="([^"]+)"', tag, re.I)
    method = (mm.group(1) if mm else "post").upper()
    fields = {}
    for im in re.finditer(r'<input[^>]*name="([^"]+)"[^>]*>', blk, re.I):
        itag, nm = im.group(0), im.group(1)
        if not nm:
            continue
        tp = re.search(r'type="([^"]*)"', itag, re.I)
        t = (tp.group(1) if tp else "text").lower()
        if t in ("checkbox", "radio", "submit", "button", "image"):
            continue
        vm = re.search(r'value="([^"]*)"', itag, re.I)
        fields[nm] = vm.group(1) if vm else ""
    for sm in re.finditer(r'<select[^>]*name="([^"]+)"[^>]*>(.*?)</select>', blk, re.I | re.S):
        nm, inner = sm.group(1), sm.group(2)
        if not nm:
            continue
        sel = re.search(r'<option[^>]*selected[^>]*value="([^"]*)"', inner, re.I)
        if not sel:
            sel = re.search(r'<option[^>]*value="([^"]*)"', inner, re.I)
        fields[nm] = sel.group(1) if sel else ""
    given = step.get("fields", {})
    fields.update(given)  # explicit data wins over discovered hidden fields
    for cb in step.get("checks", []):
        fields[cb] = "on"
    post_url = urllib.parse.urljoin(src["url"], action)
    data = urllib.parse.urlencode(fields).encode() if method == "POST" else None
    url = post_url if method == "POST" else post_url + ("&" if "?" in post_url else "?") + urllib.parse.urlencode(fields)
    req = urllib.request.Request(url, data=data, headers={
        "User-Agent": cpa_sim.MOBILE_UA, "Accept-Language": "en-US",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Referer": src["url"],
        "Origin": "{0.scheme}://{0.netloc}".format(urllib.parse.urlparse(src["url"])),
        "Content-Type": "application/x-www-form-urlencoded"})
    try:
        with ctx.client.opener.open(req, timeout=30) as r:
            body = r.read(900 * 1024).decode("utf-8", errors="replace")
            ctx.pages[step.get("as", "result")] = {"url": r.geturl(), "body": body,
                                                  "status": r.status, "bytes": len(body), "ctype": ""}
            ctx.log.log("info", "📤 submit → HTTP %s | %s (%dB)" % (r.status, r.geturl()[:90], len(body)))
            return True, ""
    except Exception as e:
        return False, "submit-fail %s" % str(e)[:100]


def do_verify(ctx, step):
    kind = step.get("type", "rss")
    if kind == "rss":
        ip = ctx.exit_ip if step.get("ip_from", "proxy") == "proxy" else step.get("ip", "")
        found, ts = rss_lead(ip) if ip else (False, "")
        ctx.converted = found
        return found, "rss=%s ip=%s" % (found, ip)
    page = ctx.pages.get(step.get("from", "result"), {})
    body, url = page.get("body", ""), page.get("url", "")
    if kind == "keywords":
        low = body.lower()
        hits = [k for k in cpa_sim.CONFIRM_KWS if len(k) >= 6 and k in low]
        ctx.converted = bool(hits)
        return bool(hits), "kw=%s" % hits[:3]
    if kind == "url":
        hit = any(b in (url or "").lower() for b in cpa_sim.THANKYOU_BITS)
        ctx.converted = hit
        return hit, "thankyou_url=%s" % hit
    return False, "unknown-verify"


def run_task(task, log, proxy=None, referer="https://www.google.com/"):
    ctx = Ctx(log, referer)
    if proxy:
        if not HAVE_SOCKS:
            return {"converted": False, "reason": "no-pysocks"}
        set_proxy(proxy)
    try:
        ctx.exit_ip = my_ip()
    except Exception:
        pass
    log.log("info", "🌐 خروج المهمة: %s" % (ctx.exit_ip or "?"))
    steps = task.get("steps", [])
    for i, st in enumerate(steps, 1):
        do = st.get("do")
        try:
            if do == "goto":
                ok, info = do_goto(ctx, st)
            elif do == "submit":
                ok, info = do_submit(ctx, st)
            elif do == "verify":
                ok, info = do_verify(ctx, st)
            elif do == "wait":
                time.sleep(int(st.get("seconds", 5)))
                ok, info = True, "waited"
            elif do == "log":
                log.log("info", st.get("message", ""))
                ok, info = True, ""
            else:
                ok, info = False, "unknown-do:%s" % do
        except Exception as e:
            ok, info = False, "throw:%s" % str(e)[:100]
        log.log("info" if ok else "warning",
                "%s خطوة %d/%d [%s]: %s" % ("✅" if ok else "⛔", i, len(steps), do, info))
        if not ok and st.get("stop_on_fail", True):
            break
    try:
        clear_proxy()
    except Exception:
        pass
    return {"converted": ctx.converted, "exit_ip": ctx.exit_ip}


def main():
    if len(sys.argv) < 2:
        print('Usage: python3 dsl.py tasks/<name>.py [attempts]')
        sys.exit(2)
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "tasks"))
    mod = __import__(os.path.splitext(os.path.basename(sys.argv[1]))[0])
    task = mod.TASK
    budget = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    base = os.path.dirname(os.path.abspath(__file__))
    log = cpa_sim.Logger(os.path.join(base, "logs"))
    log.log("info", "🚀 مهمة DSL: %s (ميزانية %d)" % (task.get("name"), budget))
    proxies = []
    if task.get("proxy", {}).get("file"):
        try:
            proxies = json.load(open(task["proxy"]["file"]))
        except Exception as e:
            log.log("warning", "تعذر تحميل البروكسيات: %s" % e)
    referers = ["https://www.google.com/", "https://www.bing.com/",
                "https://duckduckgo.com/"]
    history = []
    for n in range(1, budget + 1):
        p = proxies[(task.get("proxy", {}).get("index", 0) + n - 1) % len(proxies)] if proxies else None
        res = run_task(task, log, proxy=p, referer=referers[(n - 1) % len(referers)])
        history.append({"attempt": n, "exit_ip": res.get("exit_ip"), "converted": res.get("converted")})
        if res.get("converted"):
            log.log("success", "🏆 تحويل ناجح في المحاولة #%d (IP=%s)" % (n, res.get("exit_ip")))
            break
        if n < budget:
            w = 25 + n * 10
            log.log("info", "⏳ فاصل %d ث..." % w)
            time.sleep(w)
    verdict = "CONVERTED" if any(h["converted"] for h in history) else "NOT_CONVERTED"
    log.log("info" if verdict == "CONVERTED" else "error", "🏁 الحكم: %s" % verdict)
    with open(os.path.join(base, "logs", "dsl-%s.json" % time.strftime("%Y%m%d-%H%M%S")),
              "w", encoding="utf-8") as f:
        json.dump({"task": task.get("name"), "verdict": verdict, "attempts": history},
                  f, ensure_ascii=False, indent=2)
    log.close()


if __name__ == "__main__":
    main()
