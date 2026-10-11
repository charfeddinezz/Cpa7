#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Drive funnel to the stuck page and dump full field/error inventory.
Usage: inspect_funnel.py <proxy_json> <proxy_index>"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from browser_task import fill_form, STEALTH_JS, PERSONA
from socks_bridge import Bridge

URL = "https://singingfiles.com/show.php?l=0&u=2214761&id=75222"


def main():
    from playwright.sync_api import sync_playwright
    proxies = json.load(open(sys.argv[1]))
    p = proxies[int(sys.argv[2]) % len(proxies)]
    bridge = Bridge(p)
    with sync_playwright() as pw:
        browser = pw.chromium.launch(
            headless=True, proxy={"server": "http://127.0.0.1:%d" % bridge.port},
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
        page.goto(URL, timeout=45000, wait_until="domcontentloaded")
        # settle
        last, stable = "", 0
        for _ in range(30):
            time.sleep(1)
            try:
                u = page.url
            except Exception:
                break
            if u == last:
                stable += 1
                if stable >= 4:
                    break
            else:
                stable, last = 0, u
        print("AT: %s | %s" % (page.url[:100], page.title()[:60]))
        # one fill+submit to reach registration
        try:
            fill_form(page, lambda m: print(m, flush=True))
            btn = page.locator("button[type=submit]:visible, input[type=submit]:visible").first
            if btn.count() > 0:
                btn.click(timeout=5000)
            time.sleep(7)
        except Exception as e:
            print("interact-note: %s" % str(e)[:100])
        print("NOW AT: %s | %s" % (page.url[:100], page.title()[:60]))
        # full inventory
        try:
            inv = page.evaluate("""() => {
              const out = [];
              document.querySelectorAll('input, select, textarea').forEach(el => {
                if (el.offsetParent === null) return;
                out.push({tag: el.tagName, type: el.type || '', name: el.name || '',
                          id: el.id || '', val: (el.value || '').slice(0, 40),
                          req: !!el.required, checked: !!el.checked,
                          cls: (el.className || '').slice(0, 60)});
              });
              return out;
            }""")
            print("VISIBLE FIELDS: %d" % len(inv))
            for f in inv[:30]:
                print("  %s type=%s name=%s val=%s req=%s checked=%s" % (
                    f["tag"], f["type"], f["name"], f["val"], f["req"], f["checked"]))
        except Exception as e:
            print("inv-fail: %s" % str(e)[:100])
        try:
            errs = page.evaluate("""() => {
              const out = [];
              document.querySelectorAll('[class*=error i], [class*=invalid i], [role=alert], .field-error, .help-block').forEach(el => {
                const t = (el.innerText || '').trim().slice(0, 120);
                if (t) out.push(t);
              });
              return [...new Set(out)].slice(0, 12);
            }""")
            print("VISIBLE ERRORS: %s" % (errs if errs else "none"))
        except Exception as e:
            print("err-fail: %s" % str(e)[:100])
        try:
            btns = page.evaluate("""() => {
              const out = [];
              document.querySelectorAll('button:visible, input[type=submit]').forEach(el => {
                out.push(((el.innerText || el.value || '') + '').trim().slice(0, 40));
              });
              return out.slice(0, 12);
            }""")
            print("BUTTONS: %s" % btns)
        except Exception as e:
            print("btn-fail: %s" % str(e)[:100])
        browser.close()
    bridge.stop()


if __name__ == "__main__":
    main()
