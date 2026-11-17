#!/usr/bin/env python3
"""Render the Big Remote Play Controller icons from big-remote-play.svg.

Writes into app/src/main/res:
  mipmap-*/ic_launcher.png             legacy launcher icon (48 dp)
  mipmap-*/ic_launcher_foreground.png  adaptive foreground (108 dp, glyph inside the 66 dp safe zone)
  mipmap-*/ic_launcher_background.png  adaptive background (108 dp, brand gradient)
  mipmap-*/ic_launcher_monochrome.png  themed-icon layer (Android 13+), one colour + alpha
  mipmap-*/ic_pc_scut*.png             computer shortcut icon (legacy + adaptive foreground)
  drawable-xhdpi/atv_banner.png        Android TV banner (320x180)
  drawable-xhdpi/ouya_icon.png         OUYA/large TV art (732x412)
  drawable-nodpi/brp_logo.png          192 px logo for the app's own headers

Needs rsvg-convert (librsvg) and the Noto Sans font. Run from the repository root:
  python3 brp-assets/make-icons.py
"""
import math
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC = os.path.join(HERE, "big-remote-play.svg")

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# Content of the source icon, in the 24-unit space of its scaled group.
CONTENT_CX, CONTENT_CY = 12.0, 13.571
SAFE_RADIUS_DP = 33.0  # 66 dp safe zone of a 108 dp adaptive icon

GRADIENT = """<linearGradient id="g" gradientUnits="userSpaceOnUse" x1=".05" y1="0" x2="1" y2="1"
  gradientTransform="matrix(22,0,0,22,0,0)">
  <stop offset="0.2" stop-color="#0d0d54"/>
  <stop offset="0.205" stop-color="#6437f2"/>
  <stop offset="1" stop-color="#8b47ff"/>
</linearGradient>"""


def paths():
    text = open(SRC, encoding="utf-8").read()
    found = {}
    for pid in ("path72", "path6", "path9"):
        m = re.search(r'<path id="%s"[^>]*?\sd="([^"]+)"' % pid, text, re.S)
        if not m:
            sys.exit("path %s not found in %s" % (pid, SRC))
        found[pid] = m.group(1)
    return found


P = paths()


def glyph(opaque=False, only_front_monitor=False):
    """The white glyph (logo + two screens) in the source's 24-unit space."""
    a_front = "1" if opaque else "0.8"
    a_back = "0.6" if opaque else "0.504792"
    if only_front_monitor:
        return '<path fill="#ffffff" d="%s"/>' % P["path6"]
    return (
        '<path fill="#ffffff" d="%s"/>'
        '<path fill="#f9f9f9" fill-opacity="%s" d="%s"/>'
        '<path fill="#f9f9f9" fill-opacity="%s" d="%s"/>'
        % (P["path72"], a_front, P["path6"], a_back, P["path9"])
    )


def svg(width, height, body, defs=""):
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="%s" height="%s" viewBox="0 0 %s %s">'
        "<defs>%s</defs>%s</svg>" % (width, height, width, height, defs, body)
    )


def render(svg_text, out_path, width, height):
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as handle:
        handle.write(svg_text)
        tmp = handle.name
    try:
        subprocess.run(
            ["rsvg-convert", "-w", str(width), "-h", str(height), tmp, "-o", out_path],
            check=True,
        )
    finally:
        os.unlink(tmp)
    if has_optipng():
        subprocess.run(["optipng", "-quiet", "-o2", out_path], check=False)


_OPTIPNG = None


def has_optipng():
    global _OPTIPNG
    if _OPTIPNG is None:
        _OPTIPNG = subprocess.run(["sh", "-c", "command -v optipng"], capture_output=True).returncode == 0
    return _OPTIPNG


def foreground_body(scale, cx, cy, **kw):
    return '<g transform="translate(54 54) scale(%f) translate(%f %f)">%s</g>' % (
        scale, -cx, -cy, glyph(**kw))


def max_radius_units(cx, cy, **kw):
    """Farthest drawn point from (cx, cy), in source units (measured on a render)."""
    k = 40.0  # px per unit
    size = 1200
    body = '<g transform="translate(%f %f) scale(%f) translate(%f %f)">%s</g>' % (
        size / 2, size / 2, k, -cx, -cy, glyph(**kw))
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, "m.png")
        render(svg(size, size, body), out, size, size)
        raw = subprocess.run(
            ["magick", out, "-alpha", "extract", "-depth", "8", "gray:-"],
            check=True, capture_output=True).stdout
    best = 0.0
    for y in range(size):
        row = raw[y * size:(y + 1) * size]
        if not any(v > 16 for v in row):
            continue
        for x, v in enumerate(row):
            if v > 16:
                d = math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2)
                best = max(best, d)
    return best / k


def main():
    r = max_radius_units(CONTENT_CX, CONTENT_CY)
    scale = SAFE_RADIUS_DP / r  # dp per source unit
    print("launcher glyph: max radius %.3f units -> scale %.3f dp/unit" % (r, scale))

    # Background: the source's gradient, laid out so the visible 72 dp of the
    # adaptive icon looks like the 22-unit rounded square of the original.
    k = 72.0 / 22.0
    bg_body = ('<g transform="translate(18 18) scale(%f) translate(-1 -1)">'
               '<rect x="-4.5" y="-4.5" width="33" height="33" fill="url(#g)"/></g>' % k)

    # Shortcut glyph (front screen with the gamepad), centred on its own box.
    sc_cx, sc_cy = (7.041 + 20.096) / 2, (11.709 + 23.018) / 2
    r_sc = max_radius_units(sc_cx, sc_cy, only_front_monitor=True)
    sc_scale = (SAFE_RADIUS_DP - 3) / r_sc

    for name, d in DENSITIES.items():
        mip = os.path.join(RES, "mipmap-" + name)
        px108 = round(108 * d)
        px48 = round(48 * d)
        render(svg(108, 108, foreground_body(scale, CONTENT_CX, CONTENT_CY)),
               os.path.join(mip, "ic_launcher_foreground.png"), px108, px108)
        render(svg(108, 108, foreground_body(scale, CONTENT_CX, CONTENT_CY, opaque=True)),
               os.path.join(mip, "ic_launcher_monochrome.png"), px108, px108)
        render(svg(108, 108, bg_body, GRADIENT),
               os.path.join(mip, "ic_launcher_background.png"), px108, px108)
        render(open(SRC, encoding="utf-8").read(), os.path.join(mip, "ic_launcher.png"), px48, px48)

        render(svg(108, 108, foreground_body(sc_scale, sc_cx, sc_cy, only_front_monitor=True)),
               os.path.join(mip, "ic_pc_scut_foreground.png"), px108, px108)
        legacy_sc = ('<circle cx="24" cy="24" r="22" fill="#6f4aff"/>'
                     '<g transform="translate(24 24) scale(%f) translate(%f %f)">%s</g>'
                     % (sc_scale * 0.5, -sc_cx, -sc_cy, glyph(only_front_monitor=True)))
        render(svg(48, 48, legacy_sc), os.path.join(mip, "ic_pc_scut.png"), px48, px48)

    render(open(SRC, encoding="utf-8").read(), os.path.join(RES, "drawable-nodpi", "brp_logo.png"), 192, 192)

    # TV banner: brand gradient, white glyph, the name in two lines.
    banner_defs = ('<linearGradient id="b" x1="0" y1="0" x2="1" y2="1">'
                   '<stop offset="0" stop-color="#4a2bc4"/><stop offset="0.55" stop-color="#6437f2"/>'
                   '<stop offset="1" stop-color="#8b47ff"/></linearGradient>')
    banner_body = (
        '<rect width="320" height="180" fill="url(#b)"/>'
        '<g transform="translate(70 92) scale(4.3) translate(%f %f)">%s</g>'
        '<text x="134" y="86" font-family="Noto Sans" font-weight="700" font-size="25" fill="#ffffff">Big Remote</text>'
        '<text x="134" y="114" font-family="Noto Sans" font-weight="700" font-size="25" fill="#ffffff">Play</text>'
        '<text x="134" y="140" font-family="Noto Sans" font-weight="400" font-size="17" fill="#e6e0ff">Controller</text>'
        % (-CONTENT_CX, -CONTENT_CY, glyph()))
    banner = svg(320, 180, banner_body, banner_defs)
    render(banner, os.path.join(RES, "drawable-xhdpi", "atv_banner.png"), 320, 180)
    render(banner, os.path.join(RES, "drawable-xhdpi", "ouya_icon.png"), 732, 412)


if __name__ == "__main__":
    main()
