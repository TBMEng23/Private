#!/usr/bin/env python3
"""שרת Xtream מזויף לבדיקות ביצועים: קטלוג גדול ומציאותי (עברית/אנגלית, כפילויות, ילדים, 18+)."""
import json, random, time, sys
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

random.seed(7)
N_LIVE, N_VOD, N_SER = (int(x) for x in (sys.argv[2:5] if len(sys.argv) > 4 else (12000, 40000, 10000)))
countries = ["IL", "UK", "US", "FR", "DE", "AR", "TR", "RU", "ES", "IT", "NL", "PT", "IN", "PL", "GR"]
words = ["Love", "Night", "Dark", "Star", "Blood", "Road", "Ghost", "King", "Queen", "River", "Fire", "Dream", "Shadow",
         "Hunter", "City", "Lost", "Secret", "Last", "First", "Wild", "Silent", "Broken", "Golden", "Iron", "Black"]
heb = ["אהבה", "לילה", "המלך", "הסוד", "האחרון", "הדרך", "בית", "משפחה", "חלום", "צל", "אש", "נהר", "עיר", "גיבור"]
kids = ["KIDS | Disney", "KIDS | Pixar", "ילדים | מדובב", "Cartoon Network", "ילדים | סרטים מצוירים", "Kids Movies", "Animation"]

def cats(prefix, n):
    out = []
    for i in range(n):
        c = countries[i % len(countries)]
        out.append({"category_id": str(i + 1), "category_name": f"{c} | {prefix} {i // len(countries) + 1}", "parent_id": 0})
    base = len(out)
    for j, k in enumerate(kids):
        out.append({"category_id": str(base + j + 1), "category_name": k, "parent_id": 0})
    out.append({"category_id": str(base + len(kids) + 1), "category_name": "XXX | Adult 18+", "parent_id": 0})
    return out

def title(i, kidsy=False):
    if kidsy: return random.choice(["Frozen", "Toy Story", "Cars", "Moana", "Bluey", "Peppa Pig", "פו הדב", "מלך האריות"]) + f" {i % 7 + 1}"
    if i % 5 == 0: return f"{random.choice(heb)} {random.choice(heb)}"
    return f"{random.choice(words)} {random.choice(words)}"

live_c, vod_c, ser_c = cats("Live", 220), cats("Movies", 180), cats("Series", 120)
now = int(time.time())

def streams(n, cs, kind):
    out = []
    kid_ids = [c["category_id"] for c in cs if c["category_name"] in kids]
    adult_id = cs[-1]["category_id"]
    for i in range(n):
        r = random.random()
        if r < 0.06: cid = random.choice(kid_ids); name = title(i, True)
        elif r < 0.08: cid = adult_id; name = f"[XXX] {random.choice(words)} Studio {i}"
        else: cid = random.choice(cs[:-len(kids) - 1])["category_id"]; name = title(i)
        year = 1980 + (i * 7) % 46
        q = random.choice(["", " 4K", " FHD", " HD", " (MULTI)", ""])
        if kind == "live":
            out.append({"num": i + 1, "name": f"{name}{q}", "stream_id": i + 1, "stream_icon": "", "category_id": cid,
                        "epg_channel_id": f"ch{i}", "tv_archive": 1 if i % 9 == 0 else 0, "tv_archive_duration": 3})
        elif kind == "vod":
            # כפילויות: אותו שם בכמה איכויות
            nm = f"{name} ({year}){q}" if i % 4 else f"{title(i - 1)} ({1980 + ((i - 1) * 7) % 46}){q}"
            out.append({"num": i + 1, "name": nm, "stream_id": i + 1, "stream_icon": "", "category_id": cid,
                        "container_extension": "mkv", "added": str(now - (i * 3700) % (86400 * 400)), "rating": str(round(random.uniform(4, 9), 1))})
        else:
            out.append({"num": i + 1, "name": f"{name} ({year})", "series_id": i + 1, "cover": "", "category_id": cid,
                        "plot": "A story about " + name + ". " * 3, "last_modified": str(now - (i * 5300) % (86400 * 400)),
                        "rating": str(round(random.uniform(4, 9), 1))})
    return out

data = {
    "get_live_categories": live_c, "get_vod_categories": vod_c, "get_series_categories": ser_c,
    "get_live_streams": streams(N_LIVE, live_c, "live"), "get_vod_streams": streams(N_VOD, vod_c, "vod"),
    "get_series": streams(N_SER, ser_c, "series"),
}
blobs = {k: json.dumps(v, ensure_ascii=False).encode() for k, v in data.items()}
account = json.dumps({"user_info": {"username": "test", "status": "Active", "exp_date": str(now + 86400 * 200), "auth": 1,
                                    "active_cons": "0", "max_connections": "1", "created_at": str(now - 86400 * 30)},
                      "server_info": {"url": "10.0.2.2", "port": "8000", "timezone": "Asia/Jerusalem", "time_now": "2026-11-28 12:00:00"}}).encode()

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        u = urlparse(self.path); q = parse_qs(u.query)
        if u.path.endswith("seed.json") or u.path.endswith("seed_kids.json"):
            body = open("perf/" + u.path.strip("/"), "rb").read()
        elif u.path.endswith("player_api.php"):
            a = q.get("action", [""])[0]
            body = blobs.get(a) or (account if not a else b"[]")
        else:
            self.send_response(404); self.end_headers(); return
        self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(body))); self.end_headers()
        self.wfile.write(body)

print("sizes", {k: len(v) for k, v in data.items()}, flush=True)
ThreadingHTTPServer(("0.0.0.0", int(sys.argv[1]) if len(sys.argv) > 1 else 8000), H).serve_forever()
