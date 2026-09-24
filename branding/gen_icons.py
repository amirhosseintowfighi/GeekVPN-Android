#!/usr/bin/env python3
"""Regenerate every launcher, banner and notification bitmap from design/assets/logo.svg.

The PNGs under V2rayNG/app/src/main/res are generated output of this script.
Edit the logo or the colours here and re-run; do not hand-edit the bitmaps.

    pip install cairosvg pillow
    python3 branding/gen_icons.py
"""

from __future__ import annotations

import io
import pathlib
import re

import cairosvg
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "V2rayNG/app/src/main/res"
LOGO = ROOT / "design/assets/logo.svg"

# Foundations.html: "bg" and "logo blue".
BG = "#0870D4"
BG_LIGHT = "#00ACFE"

# The logo's own coordinate space after its transform.
LOGO_W, LOGO_H = 340.0, 330.0

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def logo_group(fill: str) -> str:
    svg = LOGO.read_text()
    match = re.search(r"<g\b[^>]*>(.*)</g>", svg, re.S)
    if match is None:
        raise SystemExit("logo.svg: no <g> element")
    inner = match.group(1)
    return (
        f'<g transform="translate(0,{LOGO_H}) scale(0.1,-0.1)" fill="{fill}">{inner}</g>'
    )


def placed_logo(fill: str, box_w: float, box_h: float, logo_w: float, dy: float = 0.0) -> str:
    """The logo scaled to logo_w wide and centred in a box_w x box_h canvas."""
    scale = logo_w / LOGO_W
    x = (box_w - logo_w) / 2
    y = (box_h - LOGO_H * scale) / 2 + dy
    return f'<g transform="translate({x:.3f},{y:.3f}) scale({scale:.5f})">{logo_group(fill)}</g>'


def gradient(box_h: float) -> str:
    return (
        '<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0" stop-color="{BG_LIGHT}"/><stop offset="1" stop-color="{BG}"/>'
        "</linearGradient></defs>"
    )


def render(svg_body: str, w: float, h: float, px_w: int, px_h: int) -> Image.Image:
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
        f'viewBox="0 0 {w} {h}">{svg_body}</svg>'
    )
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=px_w, output_height=px_h)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def save(img: Image.Image, path: pathlib.Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)
    print(path.relative_to(ROOT), img.size)


def launcher_icons() -> None:
    for density, factor in DENSITIES.items():
        folder = RES / f"mipmap-{density}"
        # Adaptive foreground: 108dp canvas, logo inside the 66dp safe zone.
        fg = placed_logo("#FFFFFF", 108, 108, 60, dy=1)
        save(render(fg, 108, 108, round(108 * factor), round(108 * factor)),
             folder / "ic_launcher_foreground.png")

        # Legacy (API 24-25) square and round icons.
        size = round(48 * factor)
        square = (
            gradient(48)
            + '<rect x="2" y="2" width="44" height="44" rx="10" fill="url(#bg)"/>'
            + placed_logo("#FFFFFF", 48, 48, 30, dy=0.5)
        )
        save(render(square, 48, 48, size, size), folder / "ic_launcher.png")
        round_icon = (
            gradient(48)
            + '<circle cx="24" cy="24" r="22" fill="url(#bg)"/>'
            + placed_logo("#FFFFFF", 48, 48, 28, dy=0.5)
        )
        save(render(round_icon, 48, 48, size, size), folder / "ic_launcher_round.png")


def banner() -> None:
    folder = RES / "mipmap-xhdpi"
    body = gradient(180) + '<rect width="320" height="180" fill="url(#bg)"/>'
    body += placed_logo("#FFFFFF", 320, 180, 110)
    save(render(body, 320, 180, 320, 180), folder / "ic_banner.png")
    save(render(placed_logo("#FFFFFF", 320, 180, 110), 320, 180, 320, 180),
         folder / "ic_banner_foreground.png")


def _globe(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float, w: int) -> None:
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline="white", width=w)
    draw.ellipse((cx - r * 0.45, cy - r, cx + r * 0.45, cy + r), outline="white", width=w)
    draw.line((cx - r, cy, cx + r, cy), fill="white", width=w)


def _arrow(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float, w: int) -> None:
    draw.line((cx - r, cy, cx + r * 0.7, cy), fill="white", width=w)
    draw.line((cx + r * 0.1, cy - r * 0.6, cx + r * 0.8, cy), fill="white", width=w)
    draw.line((cx + r * 0.1, cy + r * 0.6, cx + r * 0.8, cy), fill="white", width=w)


def notification_icons() -> None:
    for density, factor in DENSITIES.items():
        folder = RES / f"drawable-{density}"
        px = round(24 * factor)
        plain = render(placed_logo("#FFFFFF", 24, 24, 22), 24, 24, px, px)
        save(plain, folder / "ic_stat_name.png")
        black = render(placed_logo("#000000", 24, 24, 22), 24, 24, px, px)
        if (folder / "ic_stat_name_black.png").exists():
            save(black, folder / "ic_stat_name_black.png")

        for name, badge in (("ic_stat_proxy", _globe), ("ic_stat_direct", _arrow)):
            base = render(placed_logo("#FFFFFF", 24, 24, 19, dy=-2.5), 24, 24, px, px)
            # Knock a hole where the badge goes so it reads as a separate mark.
            cx, cy, r = px * 0.76, px * 0.76, px * 0.24
            hole = Image.new("L", base.size, 0)
            ImageDraw.Draw(hole).ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
            alpha = base.getchannel("A")
            alpha.paste(0, mask=hole)
            base.putalpha(alpha)
            badge(ImageDraw.Draw(base), cx, cy, r * 0.8, max(1, round(factor * 1.4)))
            save(base, folder / f"{name}.png")


def logo_vector() -> None:
    """res/drawable/ic_geek_logo.xml: the logo as a VectorDrawable for Compose (white, tint it)."""
    svg = LOGO.read_text()
    paths = re.findall(r'<path d="([^"]+)"', svg)
    body = "\n".join(
        f'        <path android:fillColor="#FFFFFFFF" android:pathData="{d}" />' for d in paths
    )
    xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by branding/gen_icons.py from design/assets/logo.svg. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{LOGO_W:g}dp"
    android:height="{LOGO_H:g}dp"
    android:viewportWidth="{LOGO_W:g}"
    android:viewportHeight="{LOGO_H:g}">
    <group
        android:scaleX="0.1"
        android:scaleY="-0.1"
        android:translateY="{LOGO_H:g}">
{body}
    </group>
</vector>
"""
    target = RES / "drawable/ic_geek_logo.xml"
    target.write_text(xml)
    print(target.relative_to(ROOT))


if __name__ == "__main__":
    logo_vector()
    launcher_icons()
    banner()
    notification_icons()
