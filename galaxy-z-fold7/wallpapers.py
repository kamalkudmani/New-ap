"""Original wallpaper sets for the Galaxy Z Fold7.

Every set has a matching pair:
  cover  1080 x 2520  (6.5" cover screen)  - the bloom is folded shut
  main   1968 x 2184  (8.0" main screen)   - the bloom is unfolded around the crease
and two video wallpapers:
  cover.mp4  folds the petals closed, ending on cover.png
  main.mp4   unfolds the petals from the crease, ending on main.png

Usage:  python3 wallpapers.py            # stills + videos for every set
        python3 wallpapers.py stills     # PNGs only
        python3 wallpapers.py <set-id>   # one set
"""

import math
import os
import shutil
import subprocess
import sys
from multiprocessing import Pool

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "wallpapers")
COVER = (1080, 2520)
MAIN = (1968, 2184)
FPS = 30

# --------------------------------------------------------------------------- sets
# blades: list of (dark, light) colours, cycled per petal
SETS = {
    "01-blue-bloom": dict(
        name="Blue Bloom",
        bg=((8, 16, 52), (1, 3, 16)), glow=(40, 100, 255),
        blades=[((14, 40, 140), (120, 180, 255)), ((40, 26, 130), (180, 150, 255)), ((6, 80, 150), (120, 235, 255))],
        n_side=5, spread=16.5, hw=11.0, bend=0.30, pivot=1.10, length=1.00),
    "02-silver-mist": dict(
        name="Silver Mist",
        bg=((232, 235, 241), (176, 183, 196)), glow=(255, 255, 255),
        blades=[((140, 148, 165), (252, 253, 255)), ((160, 166, 182), (238, 241, 248)), ((120, 132, 156), (226, 232, 244))],
        n_side=5, spread=16.0, hw=11.5, bend=0.22, pivot=1.08, length=0.98),
    "03-jet-night": dict(
        name="Jet Night",
        bg=((14, 14, 18), (0, 0, 0)), glow=(120, 92, 50),
        blades=[((14, 14, 18), (96, 96, 108)), ((22, 19, 16), (180, 142, 88)), ((10, 10, 14), (70, 72, 84))],
        n_side=6, spread=13.5, hw=9.0, bend=0.40, pivot=1.12, length=1.02),
    "04-mint-glass": dict(
        name="Mint Glass",
        bg=((12, 52, 52), (3, 18, 22)), glow=(80, 230, 190),
        blades=[((24, 116, 104), (180, 250, 225)), ((16, 84, 100), (120, 222, 232)), ((50, 140, 112), (215, 255, 238))],
        n_side=4, spread=20.0, hw=13.5, bend=0.18, pivot=1.06, length=0.96),
    "05-coral-dusk": dict(
        name="Coral Dusk",
        bg=((52, 12, 50), (14, 4, 26)), glow=(255, 110, 90),
        blades=[((170, 44, 88), (255, 172, 140)), ((104, 36, 140), (240, 146, 222)), ((196, 86, 56), (255, 212, 152))],
        n_side=5, spread=17.0, hw=11.0, bend=-0.28, pivot=1.10, length=1.00),
    "06-violet-silk": dict(
        name="Violet Silk",
        bg=((30, 16, 64), (6, 4, 22)), glow=(170, 110, 255),
        blades=[((70, 40, 160), (205, 175, 255)), ((40, 50, 150), (150, 175, 255)), ((110, 50, 170), (245, 190, 255))],
        n_side=7, spread=11.5, hw=8.0, bend=0.55, pivot=1.14, length=1.04),
}


# --------------------------------------------------------------------------- maths
def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def ease_out_back(x, k=1.25):
    x = min(1.0, max(0.0, x))
    return 1 + (k + 1) * (x - 1) ** 3 + k * (x - 1) ** 2


def ease_io(x):
    x = min(1.0, max(0.0, x))
    return 0.5 - 0.5 * math.cos(math.pi * x)


class Grid:
    """Per-pixel polar coordinates around the pivot (below the bottom centre)."""

    def __init__(self, size, pivot):
        w, h = size
        self.size, self.H = size, float(h)
        y, x = np.mgrid[0:h, 0:w].astype(np.float32)
        self.u, self.v = x / w, y / h
        px, py = w / 2.0, h * pivot
        dx, dy = x - px, py - y
        self.r = np.sqrt(dx * dx + dy * dy) / h          # radius in screen heights
        self.phi = np.degrees(np.arctan2(dx, dy))          # 0 = straight up, + = right


def petals(cfg, u):
    """Petal list for fan amount u (0 = folded shut, 1 = fully open)."""
    n = cfg["n_side"]
    out = []
    for k in range(n, 0, -1):                      # outermost first (drawn at the back)
        for side in (-1, 1):
            open_c = side * k * cfg["spread"]
            shut_c = side * k * 1.6
            c = shut_c + (open_c - shut_c) * u
            hw = cfg["hw"] * (0.72 + 0.28 * u) * (1 - 0.04 * k)
            length = cfg["length"] * (1 - 0.045 * k)
            out.append(dict(c=c, hw=hw, side=side, idx=k, length=length))
    out.append(dict(c=0.0, hw=cfg["hw"] * (0.78 + 0.22 * u), side=1, idx=0, length=cfg["length"] * 1.02))
    return out


def render(cfg, size, u, t=0.0, shimmer=0.0):
    """Render one wallpaper frame. u: fan amount, t: phase for the light sheen."""
    g = getattr(render, "_grid", {}).get((size, cfg["pivot"]))
    if g is None:
        g = Grid(size, cfg["pivot"])
        render._grid = getattr(render, "_grid", {})
        render._grid[(size, cfg["pivot"])] = g
    w, h = size
    H = g.H
    top, bot = (np.array(c, np.float32) for c in cfg["bg"])
    vt = g.v[..., None]
    img = top * (1 - vt) + bot * vt
    # soft glow rising from the crease / pivot
    glow = np.exp(-((g.u - 0.5) * (w / h) / 0.55) ** 2 - ((g.v - 0.95) / 0.65) ** 2)
    img += np.array(cfg["glow"], np.float32) * (glow * 0.32)[..., None]
    bend = cfg["bend"]
    r_in = cfg["pivot"] - 1.0 - 0.06
    for p in petals(cfg, u):
        dark, light = (np.array(c, np.float32) for c in cfg["blades"][p["idx"] % len(cfg["blades"])])
        r_tip = r_in + p["length"]
        pb = 0.0 if p["idx"] == 0 else bend          # the centre petal stays straight
        # work only inside this petal's bounding box (petal + shadow), found on a coarse grid
        cs = 8
        crr = np.clip((g.r[::cs, ::cs] - r_in) / (r_tip - r_in), 0, 1)
        cang = g.phi[::cs, ::cs] - p["c"] - p["side"] * pb * 40 * (crr ** 1.6) * (0.4 + 0.6 * u)
        cpx = np.maximum(g.r[::cs, ::cs] * H, 1.0) * (math.pi / 180)
        near = ((p["hw"] * 1.1 - np.abs(cang)) * cpx > -0.12 * H) & (crr < 1)
        ys, xs = np.nonzero(near)
        if len(ys) == 0:
            continue
        sl = (slice(max(0, ys.min() * cs - cs), min(h, ys.max() * cs + 2 * cs)),
              slice(max(0, xs.min() * cs - cs), min(w, xs.max() * cs + 2 * cs)))
        R, PHI = g.r[sl], g.phi[sl]
        sub = img[sl]
        rr = np.clip((R - r_in) / (r_tip - r_in), 0, 1)
        taper = np.maximum(np.sin(np.pi * rr), 0) ** 0.55
        taper = np.where(rr < 0.5, np.maximum(taper, 0.6), taper)   # keep the base wide
        ang = PHI - p["c"] - p["side"] * pb * 40 * (rr ** 1.6) * (0.4 + 0.6 * u)
        hw = p["hw"] * taper
        rpx = np.maximum(R * H, 1.0) * (math.pi / 180)              # px per degree here
        edge = (hw - np.abs(ang)) * rpx                             # px inside the edge
        inside = (R > r_in) & (rr < 1)
        # soft shadow cast onto everything behind
        sh_edge = (hw * 1.08 - np.abs(ang + p["side"] * 1.2)) * rpx
        shadow = np.clip(sh_edge / (0.05 * H) + 0.55, 0, 1) * inside * 0.42
        sub *= (1 - shadow)[..., None]
        alpha = np.clip(edge / 1.4 + 0.5, 0, 1) * inside
        # folded-paper shading: a crease down the middle, light on the side facing the centre
        s = np.clip(ang / np.maximum(hw, 1e-3), -1, 1) * (-p["side"])
        crease = smoothstep(-0.5, 0.5, s * rpx * np.maximum(hw, 1e-3) * 0.5)
        shade_light = 0.88 - 0.24 * np.clip(s, 0, 1)
        shade_dark = 0.30 + 0.30 * (1 + np.clip(s, -1, 0))
        shade = shade_dark * (1 - crease) + shade_light * crease
        shade *= 0.70 + 0.38 * rr
        if shimmer > 0:
            sheen = np.exp(-((rr - (0.35 + 0.5 * ((t * 0.6 + p["idx"] * 0.13) % 1))) / 0.12) ** 2) * shimmer
            shade = shade + 0.18 * sheen * crease
        col = dark + (light - dark) * np.clip(shade, 0, 1.2)[..., None]
        rim = np.exp(-np.maximum(edge, 0) / 2.2) * 0.55 * alpha
        col = col + (light * 0.9 + 25)[None, None, :] * rim[..., None]
        a = (alpha * 0.94)[..., None]
        img[sl] = sub * (1 - a) + col * a
    # gentle vignette and dither (prevents banding on the AMOLED panel)
    vign = 1 - 0.28 * (((g.u - 0.5) * w / h) ** 2 + (g.v - 0.55) ** 2)
    img *= vign[..., None]
    rng = np.random.default_rng(int(t * 1000) + 11)
    img += rng.uniform(-1.6, 1.6, (h, w, 1)).astype(np.float32)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


# --------------------------------------------------------------------------- poses
COVER_U = 0.0     # cover screen: folded shut
MAIN_U = 1.0      # main screen: unfolded


def cover_still(cfg):
    return render(cfg, COVER, COVER_U, t=0.0, shimmer=0.0)


def main_still(cfg):
    return render(cfg, MAIN, MAIN_U, t=0.0, shimmer=0.0)


def main_timeline(seconds=4.0):
    """Unfold from the crease, overshoot a touch, settle on the still."""
    n = int(seconds * FPS)
    frames = []
    for i in range(n):
        tt = i / FPS
        x = (tt - 0.25) / 2.2
        u = ease_out_back(x) if x > 0 else 0.0
        shimmer = math.sin(math.pi * min(1, max(0, (tt - 0.6) / (seconds - 0.8))))
        frames.append((u, tt, shimmer))
    frames[-1] = (MAIN_U, 0.0, 0.0)
    return frames


def cover_timeline(seconds=3.5):
    """Petals start a little open and fold shut, with a soft spring."""
    n = int(seconds * FPS)
    frames = []
    for i in range(n):
        tt = i / FPS
        x = min(1, tt / 2.4)
        spring = math.exp(-4.2 * x) * math.cos(2.6 * math.pi * x)
        u = 0.55 * spring * (1 - ease_io((tt - 2.4) / 0.8) if tt > 2.4 else 1)
        shimmer = math.sin(math.pi * min(1, max(0, (tt - 0.3) / (seconds - 0.5))))
        frames.append((max(-0.03, u), tt, shimmer))
    frames[-1] = (COVER_U, 0.0, 0.0)
    return frames


# --------------------------------------------------------------------------- output
def _frame(args):
    set_id, which, i, u, tt, shimmer, d = args
    cfg = SETS[set_id]
    size = COVER if which == "cover" else MAIN
    render(cfg, size, u, tt, shimmer).save(os.path.join(d, f"f{i:04d}.png"), compress_level=1)


def make_video(set_id, which, pool):
    d = os.path.join(HERE, ".frames", set_id, which)
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    tl = main_timeline() if which == "main" else cover_timeline()
    jobs = [(set_id, which, i, u, tt, sh, d) for i, (u, tt, sh) in enumerate(tl)]
    list(pool.imap_unordered(_frame, jobs, chunksize=2))
    out = os.path.join(OUT, set_id, f"{which}.mp4")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", f"{d}/f%04d.png",
                    "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-pix_fmt", "yuv420p",
                    "-profile:v", "high", "-movflags", "+faststart", out], check=True)
    shutil.rmtree(d, ignore_errors=True)
    return out


def preview(set_id, cover, main):
    """Cover and main wallpaper side by side, scaled to the same height."""
    hgt = 1000
    c = cover.resize((int(COVER[0] * hgt / COVER[1]), hgt), Image.LANCZOS)
    m_h = int(hgt * (MAIN[1] / MAIN[1]))
    m = main.resize((int(MAIN[0] * m_h / MAIN[1]), m_h), Image.LANCZOS)
    pad = 60
    sheet = Image.new("RGB", (c.width + m.width + pad * 3, hgt + pad * 2 + 70), (18, 19, 24))
    sheet.paste(c, (pad, pad))
    sheet.paste(m, (pad * 2 + c.width, pad))
    d = ImageDraw.Draw(sheet)
    f = ImageFont.truetype("/usr/share/fonts/opentype/inter/Inter-Medium.otf", 26)
    d.text((pad + c.width / 2, pad + hgt + 40), "Cover screen", font=f, fill=(200, 204, 215), anchor="mm")
    d.text((pad * 2 + c.width + m.width / 2, pad + hgt + 40), "Main screen", font=f, fill=(200, 204, 215), anchor="mm")
    sheet.save(os.path.join(OUT, set_id, "preview.png"), optimize=True)
    return sheet


def overview(sheets):
    cols = 2
    w, h = sheets[0].size
    rows = math.ceil(len(sheets) / cols)
    big = Image.new("RGB", (w * cols, h * rows), (18, 19, 24))
    for i, s in enumerate(sheets):
        big.paste(s.resize((w, h)), ((i % cols) * w, (i // cols) * h))
    big = big.resize((big.width // 2, big.height // 2), Image.LANCZOS)
    big.save(os.path.join(OUT, "all-sets-preview.png"), optimize=True)


def _still(set_id):
    cfg = SETS[set_id]
    os.makedirs(os.path.join(OUT, set_id), exist_ok=True)
    c, m = cover_still(cfg), main_still(cfg)
    c.save(os.path.join(OUT, set_id, "cover-1080x2520.png"), optimize=True)
    m.save(os.path.join(OUT, set_id, "main-1968x2184.png"), optimize=True)
    return set_id, preview(set_id, c, m)


def main():
    arg = sys.argv[1] if len(sys.argv) > 1 else "all"
    ids = list(SETS) if arg in ("all", "stills") else [arg]
    with Pool(os.cpu_count()) as pool:
        sheets = dict(pool.imap_unordered(_still, ids))
        print("stills done:", ", ".join(sorted(sheets)))
        if arg in ("all", "stills"):
            overview([sheets[k] for k in SETS if k in sheets])
        if arg != "stills":
            for sid in ids:
                for which in ("cover", "main"):
                    print("wrote", make_video(sid, which, pool), flush=True)
    shutil.rmtree(os.path.join(HERE, ".frames"), ignore_errors=True)


if __name__ == "__main__":
    main()
