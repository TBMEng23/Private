#!/usr/bin/env python3
"""מריץ תרחיש בדיקה על האמולטור: מעבר בין חלוניות, לחיצות מהירות, פרופיל ילדים. שומר צילומי מסך ולוגים."""
import subprocess, time, re, os, sys, html
OUT = "perf-out"; os.makedirs(OUT, exist_ok=True)
PKG = "com.tbm.iptv"
log = open(f"{OUT}/drive.log", "a", encoding="utf-8")
def P(*a):
    s = " ".join(str(x) for x in a); print(s, flush=True); log.write(s + "\n"); log.flush()
def adb(*a, timeout=60):
    try: return subprocess.run(["adb", *a], capture_output=True, timeout=timeout).stdout
    except Exception as e: P("adb err", a, e); return b""
def sh(cmd, timeout=60): return adb("shell", cmd, timeout=timeout).decode("utf-8", "replace")
def shot(name):
    open(f"{OUT}/{name}.png", "wb").write(adb("exec-out", "screencap", "-p", timeout=30)); P("shot", name)
def nodes():
    sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1", timeout=40)
    x = sh("cat /sdcard/ui.xml")
    out = []
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        t = html.unescape(m.group(1) or m.group(2))
        if t: out.append((t, (int(m.group(3)) + int(m.group(5))) // 2, (int(m.group(4)) + int(m.group(6))) // 2))
    return out
def texts(): return [n[0] for n in nodes()]
def tap_text(label):
    for t, x, y in nodes():
        if t.strip() == label:
            sh(f"input tap {x} {y}"); P("tap", label, x, y); return (x, y)
    P("NOT FOUND", label); return None
def key(*codes): sh("input keyevent " + " ".join(str(c) for c in codes))
def wait_text(label, secs):
    t0 = time.time()
    while time.time() - t0 < secs:
        if any(label in t for t in texts()): P("found", label, int(time.time() - t0), "s"); return True
        time.sleep(4)
    P("timeout waiting", label); return False
def launch(seed):
    sh(f"am force-stop {PKG}"); sh(f"pm clear {PKG}")
    sh(f"setprop debug.tbm.seed http://10.0.2.2:8000/{seed}"); sh("setprop debug.tbm.perf 1"); sh("setprop debug.tbm.perfms 80")
    adb("logcat", "-c")
    sh(f"monkey -p {PKG} -c android.intent.category.LEANBACK_LAUNCHER 1")
    time.sleep(3)
def enter(profile):
    """עובר את מסכי הפתיחה: התחברות (אולי אחר כך) ובורר הפרופילים"""
    t0 = time.time()
    while time.time() - t0 < 240:
        tx = texts()
        if "סרטים" in tx and "מי צופה?" not in tx: P("home after", int(time.time() - t0), "s"); return True
        if "אולי אחר כך" in tx: tap_text("אולי אחר כך"); time.sleep(3); continue
        if "מי צופה?" in tx: tap_text(profile); time.sleep(5); continue
        time.sleep(4)
    P("enter timeout; texts:", texts()[:40]); return False
def dumplog(name):
    l = adb("logcat", "-d", "-v", "time", timeout=60).decode("utf-8", "replace")
    keep = [x for x in l.splitlines() if "TBMPERF" in x or "ANR in" in x or "FATAL" in x or "AndroidRuntime" in x or " E/" in x and PKG in x]
    open(f"{OUT}/{name}-logcat.txt", "w").write("\n".join(keep)); open(f"{OUT}/{name}-logcat-full.txt", "w").write(l[-400000:])
    blocks = [x for x in keep if "BLOCK" in x]
    for f in [x for x in keep if "FATAL" in x or "AndroidRuntime" in x][:12]: P("   !", f[:300])
    P(f"[{name}] blocks>80ms: {len(blocks)}; ANR lines: {sum('ANR in' in x for x in keep)}; FATAL: {sum('FATAL' in x for x in keep)}")
    for b in sorted(blocks, key=lambda s: -int(re.search(r'BLOCK (\d+)ms', s).group(1)))[:25]: P("   ", b[b.find('BLOCK'):][:400])

phase = sys.argv[1] if len(sys.argv) > 1 else "all"
sections = ["בית", "טלוויזיה", "סרטים", "סדרות"]

if phase in ("all", "main"):
    launch("seed.json")
    for i in range(6): time.sleep(15); shot(f"main-start-{i}")
    P("texts:", texts()[:60])
    enter("Tamir"); time.sleep(10); shot("main-home"); P("home texts:", texts()[:50])
    dumplog("main-load"); adb("logcat", "-c")
    for s in sections + ["סרטים", "סדרות", "ערוצים", "בית"]:
        tap_text(s); time.sleep(4); shot(f"main-sec-{s}")
    dumplog("main-switch"); adb("logcat", "-c")
    # לחיצות מהירות בין חלוניות
    pos = {}
    for t, x, y in nodes():
        if t in sections: pos[t] = (x, y)
    P("rail", pos)
    for r in range(4):
        for s in sections:
            if s in pos: sh(f"input tap {pos[s][0]} {pos[s][1]}"); time.sleep(0.15)
    time.sleep(8); shot("main-after-rapid-taps")
    for r in range(3):
        key(*([22] * 8)); key(*([21] * 8)); key(*([20] * 6)); key(*([19] * 6))
    time.sleep(8); shot("main-after-rapid-keys")
    dumplog("main-rapid"); adb("logcat", "-c")
    # פתיחת קטגוריה, סרט, חזרה
    tap_text("סרטים"); time.sleep(4)
    tap_text("📂 קטגוריות הספק"); time.sleep(4); shot("main-cats"); P("cats:", texts()[:30])
    for t, x, y in nodes():
        if "(" in t and ")" in t and len(t) > 6: sh(f"input tap {x} {y}"); P("open", t); break
    time.sleep(6); shot("main-details"); P("details:", texts()[:25])
    key(4); time.sleep(4); key(4); time.sleep(4); shot("main-back")
    tap_text("סדרות"); time.sleep(4)
    for t, x, y in nodes():
        if "(" in t and ")" in t and len(t) > 6: sh(f"input tap {x} {y}"); P("open", t); break
    time.sleep(6); shot("ser-details"); P("ser details:", texts()[:25]); key(4); time.sleep(4)
    tap_text("חיפוש"); time.sleep(3); sh("input text Love"); time.sleep(5); shot("search"); P("search:", texts()[:20])
    dumplog("main-nav")
    P("alive:", PKG in sh("pidof " + PKG) or sh("pidof " + PKG).strip() != "")

if phase in ("all", "kids"):
    launch("seed_kids.json")
    time.sleep(20); enter("ילדים")
    for i in range(8): time.sleep(15); shot(f"kids-{i}"); P(f"kids t={15*(i+1)}s:", [t for t in texts() if len(t) > 1][:25])
    # מעבר בין חלוניות בפרופיל ילדים
    for s_ in ["סרטים", "סדרות", "ערוצים", "בית", "סרטים", "בית"]:
        tap_text(s_); time.sleep(4); shot(f"kids-sec-{s_}"); P(f"kids {s_}:", [t for t in texts() if len(t) > 1][:15])
    dumplog("kids")
