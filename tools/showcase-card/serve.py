#!/usr/bin/env python3
"""Fishtastic showcase card: an animated lower-third overlay for mod showcase videos.

    python tools/showcase-card/serve.py        (or double-click showcase-card.cmd)

Opens the editor in your browser. It reads the real textures straight from the repo, and Export
streams rendered frames back here so ffmpeg can encode them (WebM with alpha, ProRes 4444 with
alpha, green-screen MP4). Outputs land in tools/showcase-card/out/.
"""
import http.server
import json
import pathlib
import re
import shutil
import socketserver
import subprocess
import sys
import threading
import urllib.parse
import urllib.request
import uuid
import webbrowser

ROOT = pathlib.Path(__file__).resolve().parent
REPO = ROOT.parents[1]
RES = REPO / "common" / "src" / "main" / "resources"
TEX = RES / "assets" / "fishtastic" / "textures"
LANG = RES / "assets" / "fishtastic" / "lang" / "en_us.json"
WORDMARK = REPO / "tools" / "FishtasticBannerTitleNoFrills.png"
FONT = REPO / "tools" / "mod page banners" / "templates" / "fonts" / "Jersey15.ttf"
OUT = ROOT / "out"
UA = "fishtastic-showcase-card"

# Item textures that are not a fish you'd want leaping across the screen.
NOT_LEAPERS = {
    "acute_iaspis", "blazed_grub", "fried_shrimp", "frozen_giant_manta_ray", "generic_fish",
    "hellbender_sheen", "molten_moorish_idol_0", "electric_eel", "electric_eel_single",
    "royal_garden_eel", "royal_garden_eel_single", "garden_eel", "starfish",
}

MIME = {".html": "text/html; charset=utf-8", ".png": "image/png", ".ttf": "font/ttf",
        ".js": "text/javascript", ".css": "text/css", ".json": "application/json"}
JOBS = {}


def gradle_props():
    props = {}
    for line in (REPO / "gradle.properties").read_text(encoding="utf-8").splitlines():
        m = re.match(r"\s*([\w.]+)\s*=\s*(.*)", line)
        if m:
            props[m.group(1)] = m.group(2).strip()
    return props


def fish_list():
    lang = json.loads(LANG.read_text(encoding="utf-8")) if LANG.exists() else {}
    out = []
    for png in sorted((TEX / "item" / "fish").glob("*.png")):
        fid = png.stem
        if fid in NOT_LEAPERS:
            continue
        name = lang.get(f"item.fishtastic.{fid}") or fid.replace("_", " ").title()
        out.append({"id": fid, "name": name, "url": f"/tex/item/fish/{png.name}"})
    return out


def fetch_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.load(r)


def downloads(cf_slug, mr_id):
    result = {"curseforge": None, "modrinth": None, "errors": []}
    if cf_slug:
        try:  # cfwidget is a public mirror of CurseForge project stats; no API key needed.
            result["curseforge"] = fetch_json(
                f"https://api.cfwidget.com/minecraft/mc-mods/{urllib.parse.quote(cf_slug)}")["downloads"]["total"]
        except Exception as e:
            result["errors"].append(f"CurseForge: {e}")
    if mr_id:
        try:
            result["modrinth"] = fetch_json(
                f"https://api.modrinth.com/v2/project/{urllib.parse.quote(mr_id)}")["downloads"]
        except Exception as e:
            result["errors"].append(f"Modrinth: {e}")
    return result


def start_job(cfg):
    """One ffmpeg process per export, fed raw RGBA frames on stdin, writing every format at once."""
    fps, w, h = str(int(cfg["fps"])), int(cfg["width"]), int(cfg["height"])
    safe = re.sub(r"[^\w.-]+", "_", cfg.get("name", "")).strip("_") or "fishtastic_card"
    chroma = re.sub(r"[^0-9a-fA-F]", "", cfg.get("chroma", "00FF00"))[:6] or "00FF00"
    formats = [f for f in cfg.get("formats", []) if f in ("qtrle", "webm", "mov", "green")]
    OUT.mkdir(exist_ok=True)
    args = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-f", "rawvideo", "-pix_fmt", "rgba", "-s", f"{w}x{h}", "-r", fps, "-i", "-"]
    if "green" in formats:
        args += ["-f", "lavfi", "-i", f"color=c=0x{chroma}:s={w}x{h}:r={fps}"]
    labels = [f"v{i}" for i in range(len(formats))]
    graph = f"[0:v]split={len(formats)}" + "".join(f"[{l}]" for l in labels)
    outs, files = [], []
    for fmt, label in zip(formats, labels):
        if fmt == "webm":
            target = OUT / f"{safe}.webm"
            outs += ["-map", f"[{label}]", "-c:v", "libvpx-vp9", "-pix_fmt", "yuva420p", "-crf", "18", "-b:v", "0",
                     "-row-mt", "1", "-auto-alt-ref", "0", "-deadline", "good", "-cpu-used", "2", str(target)]
        elif fmt == "qtrle":  # QuickTime Animation: alpha lives in every frame, no side track to drop
            target = OUT / f"{safe}_qtanimation.mov"
            outs += ["-map", f"[{label}]", "-c:v", "qtrle", "-pix_fmt", "argb", str(target)]
        elif fmt == "mov":
            target = OUT / f"{safe}_prores4444.mov"
            outs += ["-map", f"[{label}]", "-c:v", "prores_ks", "-profile:v", "4444", "-pix_fmt", "yuva444p10le",
                     "-vendor", "apl0", str(target)]
        else:
            target = OUT / f"{safe}_chroma.mp4"
            graph += f";[1:v][{label}]overlay=shortest=1:format=auto,format=yuv420p[{label}g]"
            outs += ["-map", f"[{label}g]", "-c:v", "libx264", "-crf", "14", "-preset", "slow",
                     "-movflags", "+faststart", str(target)]
        files.append(str(target))
    log = OUT / f".ffmpeg-{uuid.uuid4().hex[:6]}.log"
    errf = open(log, "w+", encoding="utf-8")
    proc = subprocess.Popen(args + ["-filter_complex", graph] + outs, stdin=subprocess.PIPE, stderr=errf)
    return {"proc": proc, "files": files, "log": log, "errf": errf, "next": 0, "pending": {},
            "lock": threading.Lock(), "frame_bytes": w * h * 4}


def feed(job, n, data):
    if len(data) != job["frame_bytes"]:
        raise ValueError(f"frame {n}: {len(data)} bytes, expected {job['frame_bytes']}")
    with job["lock"]:  # frames can arrive out of order; ffmpeg must get them in order
        job["pending"][n] = data
        while job["next"] in job["pending"]:
            job["proc"].stdin.write(job["pending"].pop(job["next"]))
            job["next"] += 1


def finish(job):
    job["proc"].stdin.close()
    code = job["proc"].wait()
    job["errf"].seek(0)
    err = job["errf"].read().strip()
    job["errf"].close()
    job["log"].unlink(missing_ok=True)
    ok = code == 0 and not job["pending"]
    return {"files": job["files"] if ok else [], "frames": job["next"],
            "errors": [] if ok else [err[-800:] or f"ffmpeg exit {code}, {len(job['pending'])} frames never arrived"]}


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def send(self, code, body, ctype="application/json"):
        if isinstance(body, (dict, list)):
            body = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def file(self, path):
        if not path.is_file():
            return self.send(404, {"error": "not found"})
        self.send(200, path.read_bytes(), MIME.get(path.suffix, "application/octet-stream"))

    def body(self):
        return self.rfile.read(int(self.headers.get("Content-Length", 0)))

    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        p, q = url.path, urllib.parse.parse_qs(url.query)
        if p in ("/", "/index.html"):
            return self.file(ROOT / "card.html")
        if p == "/font/Jersey15.ttf":
            return self.file(FONT)
        if p == "/icon.png":
            return self.file(RES / "icon.png")
        if p == "/wordmark.png":
            return self.file(WORDMARK)
        if p.startswith("/tex/"):
            target = (TEX / urllib.parse.unquote(p[len("/tex/"):])).resolve()
            if TEX.resolve() not in target.parents:
                return self.send(403, {"error": "outside"})
            return self.file(target)
        if p == "/api/meta":
            g = gradle_props()
            return self.send(200, {"name": g.get("mod_name", "Fishtastic"), "version": g.get("mod_version", ""),
                                   "author": g.get("mod_authors", ""), "mc": g.get("minecraft_version", ""),
                                   "ffmpeg": shutil.which("ffmpeg") is not None, "fish": fish_list()})
        if p == "/api/downloads":
            return self.send(200, downloads(q.get("cf", [""])[0].strip(), q.get("mr", [""])[0].strip()))
        if p == "/api/open":
            OUT.mkdir(exist_ok=True)
            subprocess.Popen(["explorer", str(OUT)] if sys.platform == "win32" else ["xdg-open", str(OUT)])
            return self.send(200, {})
        self.send(404, {"error": "not found"})

    def do_POST(self):
        p = urllib.parse.urlparse(self.path).path
        if p == "/api/job":
            jid = uuid.uuid4().hex[:10]
            JOBS[jid] = start_job(json.loads(self.body()))
            return self.send(200, {"id": jid})
        m = re.fullmatch(r"/api/job/(\w+)/frame/(\d+)", p)
        if m and m.group(1) in JOBS:
            try:
                feed(JOBS[m.group(1)], int(m.group(2)), self.body())
            except (ValueError, OSError) as e:
                return self.send(500, {"error": str(e)})
            return self.send(200, {})
        m = re.fullmatch(r"/api/job/(\w+)/finish", p)
        if m and m.group(1) in JOBS:
            return self.send(200, finish(JOBS.pop(m.group(1))))
        self.send(404, {"error": "not found"})


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    # On Windows SO_REUSEADDR lets a second copy bind the same port and steal requests.
    allow_reuse_address = sys.platform != "win32"


def main():
    port = 8765
    while True:
        try:
            srv = Server(("127.0.0.1", port), Handler)
            break
        except OSError:
            port += 1
    url = f"http://127.0.0.1:{port}/"
    print(f"Fishtastic showcase card -> {url}  (Ctrl+C to stop)")
    if "--no-browser" not in sys.argv:
        threading.Timer(0.4, lambda: webbrowser.open(url)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
