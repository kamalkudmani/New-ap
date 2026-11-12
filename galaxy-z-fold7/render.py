"""Renders illustrated product images and fold animations of a
Galaxy Z Fold7-style book foldable.

Pure Python: a tiny 3D renderer (numpy + Pillow). Each half of the phone is an
extruded rounded rectangle; textured faces (screens, back glass) are mapped
with perspective transforms, metal edges are shaded per facet, and the hinge
spine is a cylinder segment that wraps the halves as they fold.

Dimensions follow the published spec (mm):
  folded   158.4 x 72.8 x 8.9
  unfolded 158.4 x 143.2 x 4.2

Usage:  python3 render.py            # everything
        python3 render.py stills     # still images only
        python3 render.py anim       # animations only
"""

import math
import os
import sys
from functools import lru_cache
from multiprocessing import Pool

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "images")

# ---------------------------------------------------------------- geometry (mm)
H = 158.4          # height
W = 71.6           # width of one half (143.2 / 2)
T = 4.2            # thickness of one half
R_OUT = 6.0        # outer corner radius
R_HINGE = 1.2      # corner radius at the hinge side
PPM = 13           # texture pixels per mm

FONT_DIR = "/usr/share/fonts/opentype/inter/"


def font(weight, size):
    return ImageFont.truetype(FONT_DIR + f"Inter-{weight}.otf", int(size))


# ---------------------------------------------------------------- colours
COLORWAYS = {
    "blue-shadow":   dict(back=(46, 60, 86),    frame=(70, 84, 110),  name="Blue Shadow"),
    "silver-shadow": dict(back=(178, 182, 190), frame=(196, 199, 205), name="Silver Shadow"),
    "jetblack":      dict(back=(30, 31, 34),    frame=(52, 53, 57),   name="Jetblack"),
    "mint":          dict(back=(176, 214, 200), frame=(160, 196, 184), name="Mint"),
}


# ---------------------------------------------------------------- textures
def rounded_mask(size, radii, ss=4):
    """Alpha mask for a rectangle with per-corner radii (px): tl, tr, br, bl."""
    w, h = size
    big = Image.new("L", (w * ss, h * ss), 0)
    d = ImageDraw.Draw(big)
    d.rectangle([0, 0, w * ss, h * ss], fill=255)
    for (cx, cy, r) in [(0, 0, radii[0]), (w, 0, radii[1]), (w, h, radii[2]), (0, h, radii[3])]:
        r *= ss
        if r <= 0:
            continue
        x0 = 0 if cx == 0 else cx * ss - r
        y0 = 0 if cy == 0 else cy * ss - r
        d.rectangle([x0, y0, x0 + r, y0 + r], fill=0)
        ex = x0 if cx == 0 else x0 - r
        ey = y0 if cy == 0 else y0 - r
        d.ellipse([ex, ey, ex + 2 * r, ey + 2 * r], fill=255)
    return big.resize((w, h), Image.LANCZOS)


def wallpaper(w, h, phase=0.0, palette="blue"):
    """Original abstract wallpaper: soft flowing ribbons of light."""
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    u = x / w
    v = y / h
    if palette == "blue":
        base_top = np.array([8, 14, 38], np.float32)
        base_bot = np.array([18, 10, 46], np.float32)
        ribbons = [
            (0.30, 0.10, 2.2, 0.0, (40, 110, 255), 0.085),
            (0.48, 0.13, 1.7, 1.3, (120, 80, 255), 0.070),
            (0.62, 0.09, 2.6, 2.1, (40, 200, 255), 0.050),
            (0.76, 0.12, 1.4, 3.4, (200, 90, 255), 0.065),
            (0.40, 0.07, 3.1, 4.4, (150, 210, 255), 0.030),
        ]
    else:  # warm variant for the cover lock screen
        base_top = np.array([10, 16, 44], np.float32)
        base_bot = np.array([30, 12, 52], np.float32)
        ribbons = [
            (0.42, 0.12, 2.0, 0.5, (60, 120, 255), 0.090),
            (0.58, 0.10, 2.4, 1.8, (160, 90, 255), 0.075),
            (0.70, 0.08, 1.8, 2.9, (60, 210, 255), 0.050),
            (0.52, 0.06, 3.0, 4.0, (190, 220, 255), 0.030),
        ]
    img = base_top[None, None, :] * (1 - v[..., None]) + base_bot[None, None, :] * v[..., None]
    diag = u * 0.55 + v * 0.45
    for (c, amp, freq, ph, col, width) in ribbons:
        centre = c + amp * np.sin(freq * math.pi * (u - v * 0.35) + ph + phase)
        d = (diag - centre) / width
        glow = np.exp(-d * d) * 0.95 + np.exp(-(d * 0.33) ** 2) * 0.25
        img = 255 - (255 - img) * (1 - glow[..., None] * np.array(col, np.float32)[None, None, :] / 255.0)
    vign = 1 - 0.35 * ((u - 0.5) ** 2 + (v - 0.45) ** 2)
    img = img * vign[..., None]
    rng = np.random.default_rng(7)
    img += rng.normal(0, 1.2, img.shape)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def glass_face(w_mm, h_mm, radii_mm, frame, screen_img, screen_rect_mm, screen_r_mm):
    """A front face: thin metal rim, black bezel, display content."""
    w, h = int(w_mm * PPM), int(h_mm * PPM)
    face = Image.new("RGBA", (w, h), (8, 8, 10, 255))
    rim = int(0.45 * PPM)
    rim_img = Image.new("RGBA", (w, h), frame + (255,))
    inner = rounded_mask((w - 2 * rim, h - 2 * rim), [max(0, r * PPM - rim) for r in radii_mm])
    blk = Image.new("RGBA", (w - 2 * rim, h - 2 * rim), (6, 6, 8, 255))
    rim_img.paste(blk, (rim, rim), inner)
    face = rim_img
    x0, y0, x1, y1 = [int(v * PPM) for v in screen_rect_mm]
    scr = screen_img.resize((x1 - x0, y1 - y0), Image.LANCZOS).convert("RGBA")
    face.paste(scr, (x0, y0), rounded_mask((x1 - x0, y1 - y0), [screen_r_mm * PPM] * 4))
    face.putalpha(rounded_mask((w, h), [r * PPM for r in radii_mm]))
    return face


def punch_hole(img, cx, cy, r):
    d = ImageDraw.Draw(img)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(4, 4, 6))
    d.ellipse([cx - r * 0.45, cy - r * 0.45, cx + r * 0.45, cy + r * 0.45], fill=(18, 20, 34))
    d.ellipse([cx - r * 0.2, cy - r * 0.35, cx + r * 0.05, cy - r * 0.1], fill=(60, 70, 110))


def lock_screen(w, h):
    img = wallpaper(w, h, phase=0.7, palette="cover")
    d = ImageDraw.Draw(img)
    s = w / 66.0  # px per mm of the cover display
    d.text((w / 2, h * 0.17), "12:45", font=font("Light", 21 * s), fill=(255, 255, 255), anchor="mm")
    d.text((w / 2, h * 0.255), "Thursday, November 12", font=font("Medium", 3.4 * s),
           fill=(235, 238, 255), anchor="mm")
    # status bar
    d.text((w * 0.08, h * 0.022), "12:45", font=font("SemiBold", 2.6 * s), fill="white", anchor="lm")
    battery(d, w * 0.84, h * 0.022, s)
    # shortcuts
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    for cx in (w * 0.17, w * 0.83):
        r = 5.5 * s
        cy = h * 0.93
        od.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 48))
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
    d = ImageDraw.Draw(img)
    # phone glyph and camera glyph (simple shapes)
    cx, cy, r = w * 0.17, h * 0.93, 2.2 * s
    d.rounded_rectangle([cx - r * 0.55, cy - r, cx + r * 0.55, cy + r], radius=r * 0.25, outline="white", width=int(0.35 * s))
    cx = w * 0.83
    d.rounded_rectangle([cx - r, cy - r * 0.7, cx + r, cy + r * 0.75], radius=r * 0.3, outline="white", width=int(0.35 * s))
    d.ellipse([cx - r * 0.42, cy - r * 0.38, cx + r * 0.42, cy + r * 0.46], outline="white", width=int(0.35 * s))
    punch_hole(img, w / 2, 4.0 * s, 1.7 * s)
    return img


def battery(d, x, y, s):
    d.rounded_rectangle([x, y - 1.1 * s, x + 4.6 * s, y + 1.1 * s], radius=0.5 * s, outline="white", width=max(1, int(0.25 * s)))
    d.rectangle([x + 0.5 * s, y - 0.6 * s, x + 3.6 * s, y + 0.6 * s], fill="white")
    d.rectangle([x + 4.7 * s, y - 0.45 * s, x + 5.1 * s, y + 0.45 * s], fill="white")
    for i in range(4):  # signal bars
        bx = x - 6.5 * s + i * 1.05 * s
        d.rectangle([bx, y + 1.0 * s - (i + 1) * 0.5 * s, bx + 0.7 * s, y + 1.0 * s], fill="white")


ICON_COLORS = [
    ((255, 94, 98), (255, 153, 102)), ((54, 209, 220), (91, 134, 229)), ((67, 233, 123), (56, 249, 215)),
    ((250, 112, 154), (254, 225, 64)), ((161, 140, 209), (251, 194, 235)), ((48, 207, 208), (51, 8, 103)),
    ((255, 175, 64), (255, 110, 64)), ((102, 126, 234), (118, 75, 162)), ((240, 240, 245), (200, 205, 215)),
    ((86, 204, 242), (47, 128, 237)), ((255, 81, 47), (221, 36, 118)), ((17, 153, 142), (56, 239, 125)),
]


def app_icon(size, idx):
    c0, c1 = ICON_COLORS[idx % len(ICON_COLORS)]
    t = np.linspace(0, 1, size, dtype=np.float32)
    g = (t[:, None] * 0.6 + t[None, :] * 0.4)[..., None]
    arr = np.array(c0, np.float32) * (1 - g) + np.array(c1, np.float32) * g
    ic = Image.fromarray(arr.astype(np.uint8), "RGB").convert("RGBA")
    d = ImageDraw.Draw(ic)
    s = size
    glyph = (255, 255, 255) if idx % len(ICON_COLORS) != 8 else (90, 96, 110)
    k = idx % 6
    if k == 0:
        d.ellipse([s * .28, s * .28, s * .72, s * .72], outline=glyph, width=int(s * .08))
    elif k == 1:
        d.rounded_rectangle([s * .25, s * .3, s * .75, s * .7], radius=s * .08, outline=glyph, width=int(s * .07))
        d.line([s * .25, s * .42, s * .75, s * .42], fill=glyph, width=int(s * .06))
    elif k == 2:
        d.polygon([(s * .5, s * .25), (s * .76, s * .72), (s * .24, s * .72)], outline=glyph, width=int(s * .07))
    elif k == 3:
        for i in range(3):
            d.line([s * .28, s * (.35 + i * .15), s * .72, s * (.35 + i * .15)], fill=glyph, width=int(s * .07))
    elif k == 4:
        d.ellipse([s * .3, s * .3, s * .7, s * .7], fill=glyph)
    else:
        d.line([s * .5, s * .26, s * .5, s * .74], fill=glyph, width=int(s * .08))
        d.line([s * .26, s * .5, s * .74, s * .5], fill=glyph, width=int(s * .08))
    ic.putalpha(rounded_mask((s, s), [s * 0.3] * 4))
    return ic


def home_screen(w, h):
    img = wallpaper(w, h, phase=0.0, palette="blue").convert("RGBA")
    d = ImageDraw.Draw(img)
    s = w / 140.0
    half = w / 2
    # glassy card behind the agenda widget
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    card = (int(half - 16 * s), int(30 * s))
    layer.paste(Image.new("RGBA", card, (255, 255, 255, 36)), (int(8 * s), int(h * 0.52)), rounded_mask(card, [4 * s] * 4))
    img = Image.alpha_composite(img, layer)
    d = ImageDraw.Draw(img)
    d.text((w * 0.03, 3.2 * s), "12:45", font=font("SemiBold", 2.6 * s), fill="white", anchor="lm")
    battery(d, w - 9 * s, 3.2 * s, s)
    d.text((8 * s, 22 * s), "12:45", font=font("Light", 15 * s), fill="white", anchor="lm")
    d.text((8.6 * s, 33 * s), "Thu, November 12  ·  18°", font=font("Medium", 3.0 * s), fill=(225, 230, 255), anchor="lm")
    cy = h * 0.52
    d.text((13 * s, cy + 7 * s), "Today", font=font("SemiBold", 3.0 * s), fill="white", anchor="lm")
    d.text((13 * s, cy + 13 * s), "Design review  ·  2:30 PM", font=font("Regular", 2.6 * s), fill=(230, 232, 245), anchor="lm")
    d.text((13 * s, cy + 19 * s), "Gym  ·  6:00 PM", font=font("Regular", 2.6 * s), fill=(230, 232, 245), anchor="lm")
    # app grid on the right half
    isz = int(11 * s)
    cols, rows = 4, 4
    gx0, gy0 = half + 9 * s, 18 * s
    gx = (half - 18 * s - isz) / (cols - 1)
    gy = 21 * s
    n = 0
    for r in range(rows):
        for c in range(cols):
            x = int(gx0 + c * gx)
            y = int(gy0 + r * gy)
            img.alpha_composite(app_icon(isz, n), (x, y))
            n += 1
    # dock
    dock_w, dock_h = int(80 * s), int(17 * s)
    dx, dy = int(w / 2 - dock_w / 2), int(h - dock_h - 6 * s)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    layer.paste(Image.new("RGBA", (dock_w, dock_h), (255, 255, 255, 46)), (dx, dy), rounded_mask((dock_w, dock_h), [6 * s] * 4))
    img = Image.alpha_composite(img, layer)
    for i in range(6):
        img.alpha_composite(app_icon(int(11 * s), i + 3), (int(dx + 4 * s + i * 12.4 * s), int(dy + 3 * s)))
    return img


def make_inner_textures(cw):
    """Main 8.0" display across both halves -> (left_half, right_half)."""
    frame = cw["frame"]
    w, h = int(2 * W * PPM), int(H * PPM)
    scr_w_mm, scr_h_mm = 2 * W - 3.0, H - 3.0
    scr = home_screen(int(scr_w_mm * 10), int(scr_h_mm * 10)).convert("RGB")
    face = glass_face(2 * W, H, [R_OUT] * 4, frame, scr, (1.5, 1.5, 2 * W - 1.5, H - 1.5), 4.0)
    # punch-hole camera on the right half
    punch_hole(face, int((W + W * 0.5) * PPM), int(5.0 * PPM), 1.6 * PPM)
    # the crease: a very faint vertical highlight / shadow pair
    crease = Image.new("RGBA", face.size, (0, 0, 0, 0))
    cd = ImageDraw.Draw(crease)
    cx = w // 2
    for i in range(-14, 15):
        a = int(10 * math.exp(-(i / 7.0) ** 2))
        col = (255, 255, 255, a) if i < 0 else (0, 0, 0, a + 4)
        cd.line([cx + i, int(1.5 * PPM), cx + i, h - int(1.5 * PPM)], fill=col)
    face = Image.alpha_composite(face, crease)
    left = face.crop((0, 0, w // 2, h))
    right = face.crop((w // 2, 0, w, h))
    left.putalpha(ImageChops.multiply(left.getchannel("A"), rounded_mask(left.size, [R_OUT * PPM, R_HINGE * PPM, R_HINGE * PPM, R_OUT * PPM])))
    right.putalpha(ImageChops.multiply(right.getchannel("A"), rounded_mask(right.size, [R_HINGE * PPM, R_OUT * PPM, R_OUT * PPM, R_HINGE * PPM])))
    return left, right


def make_cover_texture(cw):
    """6.5" cover display on the outer face of the left half (seen from outside,
    the hinge is on the LEFT)."""
    scr = lock_screen(int((W - 3.4) * 10), int((H - 3.4) * 10))
    return glass_face(W, H, [R_HINGE, R_OUT, R_OUT, R_HINGE], cw["frame"], scr,
                      (1.7, 1.7, W - 1.7, H - 1.7), 4.5)


def make_back_texture(cw):
    """Rear glass with the triple camera; seen from outside the hinge is on the RIGHT."""
    w, h = int(W * PPM), int(H * PPM)
    base = np.array(cw["back"], np.float32)
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    sheen = 1.0 + 0.10 * np.exp(-(((x / w) - 0.25 + (y / h) * 0.3) / 0.35) ** 2) - 0.06 * (y / h)
    rng = np.random.default_rng(3)
    arr = base[None, None, :] * sheen[..., None] + rng.normal(0, 1.6, (h, w, 1))
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    d = ImageDraw.Draw(img)
    frame = cw["frame"]
    rim = int(0.45 * PPM)
    # metal rim
    rim_layer = Image.new("RGBA", (w, h), frame + (255,))
    inner = rounded_mask((w - 2 * rim, h - 2 * rim), [max(0, r * PPM - rim) for r in [R_OUT, R_HINGE, R_HINGE, R_OUT]])
    rim_layer.paste(img.crop((rim, rim, w - rim, h - rim)), (rim, rim), inner)
    img = rim_layer
    d = ImageDraw.Draw(img)
    p = PPM
    light = sum(cw["back"]) / 3 > 120
    # three separate camera rings stacked vertically, near the outer (left) edge
    for i, cy in enumerate((15.0, 29.0, 43.0)):
        cx = 13.0
        ring_r = 6.0
        d.ellipse([(cx - ring_r) * p, (cy - ring_r) * p, (cx + ring_r) * p, (cy + ring_r) * p],
                  fill=tuple(int(c * 0.75) for c in frame))
        d.ellipse([(cx - ring_r + 0.5) * p, (cy - ring_r + 0.5) * p, (cx + ring_r - 0.5) * p, (cy + ring_r - 0.5) * p],
                  fill=tuple(min(255, int(c * 1.25 + 20)) for c in frame))
        d.ellipse([(cx - ring_r + 1.0) * p, (cy - ring_r + 1.0) * p, (cx + ring_r - 1.0) * p, (cy + ring_r - 1.0) * p],
                  fill=(14, 14, 18))
        lr = 3.3 if i == 0 else 2.9
        d.ellipse([(cx - lr) * p, (cy - lr) * p, (cx + lr) * p, (cy + lr) * p], fill=(22, 24, 36))
        d.ellipse([(cx - lr * 0.6) * p, (cy - lr * 0.6) * p, (cx + lr * 0.6) * p, (cy + lr * 0.6) * p], fill=(8, 10, 20))
        d.ellipse([(cx - lr * 0.55) * p, (cy - lr * 0.6) * p, (cx - lr * 0.05) * p, (cy - lr * 0.15) * p], fill=(70, 80, 140))
        d.ellipse([(cx + lr * 0.2) * p, (cy + lr * 0.15) * p, (cx + lr * 0.45) * p, (cy + lr * 0.4) * p], fill=(120, 60, 120))
    # flash and sensor
    fx, fy = 24.5, 15.0
    d.ellipse([(fx - 2.0) * p, (fy - 2.0) * p, (fx + 2.0) * p, (fy + 2.0) * p], fill=(235, 228, 205) if light else (220, 214, 190))
    d.ellipse([(fx - 1.3) * p, (fy - 1.3) * p, (fx + 1.3) * p, (fy + 1.3) * p], fill=(250, 246, 230))
    sx, sy = 24.5, 22.0
    d.ellipse([(sx - 1.1) * p, (sy - 1.1) * p, (sx + 1.1) * p, (sy + 1.1) * p], fill=(20, 20, 26))
    img.putalpha(rounded_mask((w, h), [R_OUT * p, R_HINGE * p, R_HINGE * p, R_OUT * p]))
    return img


# ---------------------------------------------------------------- 3D model
def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def outline(sign, n=10):
    """Rounded-rect outline of one half in (x, y); x from 0 (hinge) to sign*W."""
    pts = []
    corners = [  # centre, radius, start angle (ccw) for x>0 half
        ((W - R_OUT, H / 2 - R_OUT), R_OUT, 0),
        ((R_HINGE, H / 2 - R_HINGE), R_HINGE, 90),
        ((R_HINGE, -H / 2 + R_HINGE), R_HINGE, 180),
        ((W - R_OUT, -H / 2 + R_OUT), R_OUT, 270),
    ]
    for (cx, cy), r, a0 in corners:
        for k in range(n + 1):
            a = math.radians(a0 + 90 * k / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    pts = [(sign * x, y) for x, y in pts]
    if sign < 0:
        pts = pts[::-1]
    return pts


class Face:
    __slots__ = ("pts", "normal", "kind", "tex", "color", "spec")

    def __init__(self, pts, normal, kind, tex=None, color=None, spec=0.0):
        self.pts = np.asarray(pts, float)
        self.normal = np.asarray(normal, float)
        self.kind = kind        # "tex" | "flat"
        self.tex = tex
        self.color = color
        self.spec = spec


def build_model(theta_deg, cw, tex):
    """Faces in model space for opening angle theta (0 = closed, 180 = flat)."""
    half = math.radians((180 - theta_deg) / 2)
    faces = []
    frame = cw["frame"]
    for sign, ang, inner_tex, outer_tex in (
        (-1, half, tex["inner_left"], tex["cover"]),
        (+1, -half, tex["inner_right"], tex["back"]),
    ):
        Rm = rot_y(ang)
        ol = outline(sign)
        # inner face (z = 0, normal +z). Texture corners tl,tr,br,bl as seen from +z
        if sign < 0:
            quad = [(-W, H / 2, 0), (0, H / 2, 0), (0, -H / 2, 0), (-W, -H / 2, 0)]
        else:
            quad = [(0, H / 2, 0), (W, H / 2, 0), (W, -H / 2, 0), (0, -H / 2, 0)]
        faces.append(Face(np.array(quad) @ Rm.T, Rm @ [0, 0, 1], "tex", inner_tex, spec=0.25))
        # outer face (z = -T, normal -z) as seen from -z: left is +x
        if sign < 0:
            quad = [(0, H / 2, -T), (-W, H / 2, -T), (-W, -H / 2, -T), (0, -H / 2, -T)]
        else:
            quad = [(W, H / 2, -T), (0, H / 2, -T), (0, -H / 2, -T), (W, -H / 2, -T)]
        faces.append(Face(np.array(quad) @ Rm.T, Rm @ [0, 0, -1], "tex", outer_tex, spec=0.2))
        # side walls
        cx = sign * W / 2
        m = len(ol)
        for i in range(m):
            (x0, y0), (x1, y1) = ol[i], ol[(i + 1) % m]
            if abs(x0) < 1e-6 and abs(x1) < 1e-6:
                continue  # hinge wall, hidden in the spine
            nx, ny = (y1 - y0), -(x1 - x0)
            if nx * ((x0 + x1) / 2 - cx) + ny * ((y0 + y1) / 2) < 0:
                nx, ny = -nx, -ny
            ln = math.hypot(nx, ny)
            quad = [(x0, y0, 0), (x1, y1, 0), (x1, y1, -T), (x0, y0, -T)]
            faces.append(Face(np.array(quad) @ Rm.T, Rm @ [nx / ln, ny / ln, 0], "flat", color=frame, spec=1.0))
    # hinge spine: a flattened curve joining the two backs, tangent to both
    # (closed it bulges ~1.2 mm past the panels: 72.8 mm vs 71.6 mm).
    RA, RB = rot_y(half), rot_y(-half)
    PA = (RA @ [0, 0, -T])[[0, 2]]
    PB = (RB @ [0, 0, -T])[[0, 2]]
    dA = -(RA @ [-1, 0, 0])[[0, 2]]   # continue away from panel A
    dB = -(RB @ [1, 0, 0])[[0, 2]]
    gap = np.linalg.norm(PA - PB)
    if gap > 0.05:
        c = 0.25 * gap
        P = [PA, PA + c * dA, PB + c * dB, PB]
        steps = max(3, int(gap / 0.5))
        curve = []
        for k in range(steps + 1):
            u = k / steps
            curve.append((1 - u) ** 3 * P[0] + 3 * (1 - u) ** 2 * u * P[1] + 3 * (1 - u) * u * u * P[2] + u ** 3 * P[3])
        hs = H / 2 - 0.8
        spine_col = tuple(int(ch * 0.92) for ch in frame)
        for k in range(steps):
            p0, p1 = curve[k], curve[k + 1]
            tx, tz = p1 - p0
            n = np.array([tz, 0, -tx])
            mid = (p0 + p1) / 2
            if n[0] * mid[0] + n[2] * mid[1] < 0:
                n = -n
            n /= np.linalg.norm(n) + 1e-12
            quad = [(p0[0], hs, p0[1]), (p1[0], hs, p1[1]), (p1[0], -hs, p1[1]), (p0[0], -hs, p0[1])]
            faces.append(Face(quad, n, "flat", color=spine_col, spec=1.0))
        for yy, ny in ((hs, 1), (-hs, -1)):
            poly = [(0, yy, 0)] + [(p[0], yy, p[1]) for p in curve]
            faces.append(Face(poly, (0, ny, 0), "flat", color=spine_col, spec=0.6))
    return faces


# ---------------------------------------------------------------- renderer
LIGHT = np.array([-0.45, 0.65, 0.75]); LIGHT /= np.linalg.norm(LIGHT)
FILL = np.array([0.7, 0.05, 0.45]); FILL /= np.linalg.norm(FILL)
CAM_D = 900.0


def perspective_coeffs(dst, src):
    """Coefficients for PIL PERSPECTIVE mapping output (dst) -> input (src)."""
    A, b = [], []
    for (x, y), (u, v) in zip(dst, src):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.append(u)
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y]); b.append(v)
    return np.linalg.solve(np.array(A, float), np.array(b, float))


@lru_cache(maxsize=64)
def _scaled(tex_id, factor):
    tex = _TEX_REGISTRY[tex_id]
    if factor >= 1:
        return tex
    return tex.resize((max(8, int(tex.width * factor)), max(8, int(tex.height * factor))), Image.LANCZOS)


_TEX_REGISTRY = {}


def register(tex):
    _TEX_REGISTRY[id(tex)] = tex
    return tex


def transform_faces(faces, theta, yaw, pitch, roll=0.0, center=None):
    pres = math.radians(90 * (1 - theta / 180.0))
    M = rot_z(math.radians(roll)) @ rot_x(math.radians(pitch)) @ rot_y(math.radians(yaw) + pres)
    out = []
    allp = []
    for f in faces:
        p = f.pts @ M.T
        n = M @ f.normal
        out.append((f, p, n))
        allp.append(p)
    allp = np.vstack(allp)
    if center is None:
        center = (allp.min(0) + allp.max(0)) / 2
    return [(f, p - center, n) for f, p, n in out], allp - center


def project(p, f_len, cx, cy):
    z = CAM_D - p[:, 2]
    return np.stack([cx + f_len * p[:, 0] / z, cy - f_len * p[:, 1] / z], 1)


def shade(n, base, spec_amt):
    lam = max(0.0, float(n @ LIGHT))
    fill = max(0.0, float(n @ FILL))
    view = np.array([0, 0, 1.0])
    hv = LIGHT + view; hv /= np.linalg.norm(hv)
    sp = max(0.0, float(n @ hv)) ** 36 * spec_amt
    k = 0.32 + 0.62 * lam + 0.22 * fill
    return tuple(int(min(255, c * k + 255 * sp * 0.55)) for c in base)


def render_scene(size, theta, yaw, pitch, cw, tex, roll=0.0, ss=2, f_len=None, offset=(0, 0),
                 bg=None, shadow=True, fit=0.80):
    w, h = size[0] * ss, size[1] * ss
    faces = build_model(theta, cw, tex)
    tfaces, allp = transform_faces(faces, theta, yaw, pitch, roll)
    if f_len is None:
        pr = project(allp, 1.0, 0, 0)
        ext = pr.max(0) - pr.min(0)
        f_len = min(w * fit / ext[0], h * fit / ext[1])
        mid = (pr.max(0) + pr.min(0)) / 2 * f_len
        cx, cy = w / 2 - mid[0], h / 2 - mid[1]
    else:
        f_len *= ss
        cx, cy = w / 2 + offset[0] * ss, h / 2 + offset[1] * ss
    canvas = (bg.resize((w, h)) if bg is not None else studio_bg((w, h))).convert("RGBA")
    pr_all = project(allp, f_len, cx, cy)
    if shadow:
        canvas = drop_shadow(canvas, pr_all, ss)
    cam = np.array([0, 0, CAM_D])
    drawn = []
    for f, p, n in tfaces:
        cen = p.mean(0)
        if n @ (cam - cen) <= 0:
            continue
        drawn.append((np.linalg.norm(cam - cen), f, p, n))
    drawn.sort(key=lambda t: -t[0])
    flat_layer = None
    for _, f, p, n in drawn:
        q = project(p, f_len, cx, cy)
        if f.kind == "flat":
            d = ImageDraw.Draw(canvas)
            d.polygon([tuple(v) for v in q], fill=shade(n, f.color, f.spec) + (255,))
        else:
            draw_textured(canvas, q, f, n)
    out = canvas.convert("RGB").resize(size, Image.LANCZOS)
    return out


def draw_textured(canvas, q, f, n):
    x0, y0 = np.floor(q.min(0)).astype(int) - 2
    x1, y1 = np.ceil(q.max(0)).astype(int) + 2
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(canvas.width, x1), min(canvas.height, y1)
    if x1 - x0 < 2 or y1 - y0 < 2:
        return
    tex = f.tex
    edge_px = max(np.linalg.norm(q[1] - q[0]), np.linalg.norm(q[2] - q[1]) * tex.width / tex.height)
    factor = min(1.0, round(edge_px / tex.width * 1.15, 2))
    tex = _scaled(id(f.tex), factor if factor < 0.9 else 1.0)
    tw, th = tex.size
    src = [(0, 0), (tw, 0), (tw, th), (0, th)]
    dst = [(v[0] - x0, v[1] - y0) for v in q]
    coeffs = perspective_coeffs(dst, src)
    patch = tex.transform((x1 - x0, y1 - y0), Image.Transform.PERSPECTIVE, tuple(coeffs), Image.BICUBIC)
    lam = max(0.0, float(n @ LIGHT))
    if f.spec >= 0.25:  # screens glow: almost unaffected by light
        k = 0.88 + 0.12 * lam
    else:
        k = 0.55 + 0.50 * lam + 0.12 * max(0.0, float(n @ FILL))
    k = min(1.15, k)
    rgb = patch.convert("RGB")
    if abs(k - 1) > 0.01:
        arr = np.asarray(rgb, np.float32) * k
        # gentle glossy reflection, stronger at grazing angles
        rgb = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    graze = 1 - abs(float(n[2]))
    if graze > 0.05:
        g = Image.new("RGB", rgb.size, (255, 255, 255))
        rgb = Image.blend(rgb, g, min(0.18, graze * 0.16))
    rgb.putalpha(patch.getchannel("A"))
    canvas.alpha_composite(rgb, (x0, y0))


def studio_bg(size, top=(247, 248, 251), bottom=(222, 226, 234)):
    w, h = size
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    r = np.sqrt(((x - w * 0.5) / w) ** 2 + ((y - h * 0.42) / h) ** 2)
    t = np.clip(r * 1.25, 0, 1)[..., None]
    arr = np.array(top, np.float32) * (1 - t) + np.array(bottom, np.float32) * t
    return Image.fromarray(arr.astype(np.uint8), "RGB")


def drop_shadow(canvas, pr, ss):
    xmin, ymin = pr.min(0)
    xmax, ymax = pr.max(0)
    wd = xmax - xmin
    layer = Image.new("L", canvas.size, 0)
    d = ImageDraw.Draw(layer)
    cy = ymax + 4 * ss
    d.ellipse([xmin + wd * 0.02, cy - wd * 0.035 - 6 * ss, xmax - wd * 0.02, cy + wd * 0.035 + 6 * ss], fill=70)
    layer = layer.filter(ImageFilter.GaussianBlur(18 * ss))
    contact = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(contact).ellipse([xmin + wd * 0.08, cy - 3 * ss, xmax - wd * 0.08, cy + 5 * ss], fill=90)
    contact = contact.filter(ImageFilter.GaussianBlur(5 * ss))
    sh = ImageChops.lighter(layer, contact)
    dark = Image.new("RGBA", canvas.size, (20, 24, 36, 255))
    dark.putalpha(sh)
    return Image.alpha_composite(canvas, dark)


# ---------------------------------------------------------------- assets
_ASSETS = {}


def assets(colorway="blue-shadow"):
    if colorway not in _ASSETS:
        cw = COLORWAYS[colorway]
        il, ir = make_inner_textures(cw)
        tex = dict(inner_left=register(il), inner_right=register(ir),
                   cover=register(make_cover_texture(cw)), back=register(make_back_texture(cw)))
        _ASSETS[colorway] = (cw, tex)
    return _ASSETS[colorway]


def ease_io(x):
    return 0.5 - 0.5 * math.cos(math.pi * min(1, max(0, x)))


# ---------------------------------------------------------------- outputs
STILLS = [
    # name, theta, yaw, pitch, roll, size
    ("01-closed-front-cover-screen", 0, 0, 0, 0, (1200, 1500)),
    ("02-closed-back-cameras", 0, 180, 0, 0, (1200, 1500)),
    ("03-open-front-main-screen", 180, 0, 0, 0, (1600, 1500)),
    ("04-open-back", 180, 180, 0, 0, (1600, 1500)),
    ("05-closed-three-quarter", 0, -32, 10, 0, (1400, 1500)),
    ("06-closed-back-three-quarter", 0, 148, 10, 0, (1400, 1500)),
    ("07-half-open-folding", 70, -40, 12, 0, (1600, 1500)),
    ("08-flex-mode-100deg", 100, -50, 14, 0, (1600, 1500)),
    ("09-almost-open-unfolding", 150, -28, 10, 0, (1600, 1500)),
    ("10-open-three-quarter-thin-profile", 180, -38, 8, 0, (1600, 1500)),
    ("11-closed-side-profile", 0, 80, 8, 0, (1200, 1500)),
]


def render_still(args):
    name, theta, yaw, pitch, roll, size, colorway = args
    cw, tex = assets(colorway)
    img = render_scene(size, theta, yaw, pitch, cw, tex, roll=roll, fit=0.78)
    path = os.path.join(OUT, f"{name}.png")
    img.save(path, optimize=True)
    return path


def label(img, text, sub=None):
    d = ImageDraw.Draw(img)
    d.text((img.width / 2, img.height - 70), text, font=font("SemiBold", 30), fill=(30, 34, 46), anchor="mm")
    if sub:
        d.text((img.width / 2, img.height - 32), sub, font=font("Regular", 22), fill=(110, 116, 130), anchor="mm")
    return img


def fold_sequence():
    cw, tex = assets("blue-shadow")
    angles = [0, 45, 90, 135, 180]
    tiles = []
    for a in angles:
        t = render_scene((760, 900), a, -30, 10, cw, tex, f_len=3300,
                         shadow=True)
        tiles.append(label(t, f"{a}°", ["Closed", "Folding", "Flex", "Unfolding", "Open"][angles.index(a)]))
    sheet = Image.new("RGB", (760 * len(tiles), 900), (240, 242, 246))
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * 760, 0))
    path = os.path.join(OUT, "12-fold-sequence-0-to-180.png")
    sheet.save(path, optimize=True)
    return path


def colors_lineup():
    tiles = []
    for key in COLORWAYS:
        cw, tex = assets(key)
        t = render_scene((700, 1000), 0, 150, 8, cw, tex, fit=0.74)
        tiles.append(label(t, cw["name"]))
    sheet = Image.new("RGB", (700 * len(tiles), 1000))
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * 700, 0))
    path = os.path.join(OUT, "13-colors-lineup.png")
    sheet.save(path, optimize=True)
    return path


# animation ------------------------------------------------------------------
FPS = 30
ANIM_SIZE = (1080, 1080)


def anim_timeline():
    """(theta, yaw) per frame: hold closed, unfold, hold open, fold."""
    frames = []
    hold0, move, hold1 = int(0.6 * FPS), int(2.0 * FPS), int(0.9 * FPS)
    for _ in range(hold0):
        frames.append(0.0)
    for i in range(move):
        frames.append(180 * ease_io(i / (move - 1)))
    for _ in range(hold1):
        frames.append(180.0)
    for i in range(move):
        frames.append(180 * (1 - ease_io(i / (move - 1))))
    return frames


def anim_frame(args):
    i, theta, f_len, yaw_base, colorway, frame_dir = args
    cw, tex = assets(colorway)
    # a gentle turn while it unfolds: three-quarter view when closed, near frontal when open
    yaw = yaw_base * (1 - 0.55 * theta / 180)
    pitch = 9 - 3 * theta / 180
    img = render_scene(ANIM_SIZE, theta, yaw, pitch, cw, tex, f_len=f_len, offset=(0, 25))
    img.save(os.path.join(frame_dir, f"f{i:04d}.png"))
    return i


def animate(colorway="blue-shadow", tag="fold-animation", yaw_base=-30):
    frame_dir = os.path.join(os.path.dirname(OUT), ".frames", tag)
    os.makedirs(frame_dir, exist_ok=True)
    for fn in os.listdir(frame_dir):
        os.remove(os.path.join(frame_dir, fn))
    tl = anim_timeline()
    f_len = ANIM_SIZE[0] * 0.95 / 175 * (CAM_D - 60)
    jobs = [(i, th, f_len, yaw_base, colorway, frame_dir) for i, th in enumerate(tl)]
    with Pool(os.cpu_count()) as pool:
        for _ in pool.imap_unordered(anim_frame, jobs):
            pass
    mp4 = os.path.join(OUT, f"{tag}.mp4")
    gif = os.path.join(OUT, f"{tag}.gif")
    os.system(f"ffmpeg -y -loglevel error -framerate {FPS} -i {frame_dir}/f%04d.png "
              f"-c:v libx264 -pix_fmt yuv420p -crf 18 -movflags +faststart {mp4}")
    os.system(f"ffmpeg -y -loglevel error -framerate {FPS} -i {frame_dir}/f%04d.png "
              f"-vf \"fps=24,scale=540:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=192[p];[b][p]paletteuse=dither=sierra2_4a\" "
              f"-loop 0 {gif}")
    return mp4, gif


def main():
    os.makedirs(OUT, exist_ok=True)
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("all", "stills"):
        jobs = [s + ("blue-shadow",) for s in STILLS]
        with Pool(os.cpu_count()) as pool:
            for p in pool.imap_unordered(render_still, jobs):
                print("wrote", p)
        print("wrote", fold_sequence())
        print("wrote", colors_lineup())
    if what in ("all", "anim"):
        print("wrote", animate("blue-shadow", "fold-animation", -30))
        print("wrote", animate("silver-shadow", "fold-animation-silver-shadow", 30))


if __name__ == "__main__":
    main()
