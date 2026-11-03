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

# Dark Swiss (Logo-AppIcon.dc.html): the near-black ground and its blue light.
BG = "#02040E"
GLOW = "#1A3CFF"
GLOW_CORE = "#7FB2FF"

# The logo's viewBox: two lenses joined by a bridge, 2:1.
LOGO_W, LOGO_H = 240.0, 120.0

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def logo_paths() -> list[str]:
    paths = re.findall(r'<path d="([^"]+)"', LOGO.read_text())
    if not paths:
        raise SystemExit("logo.svg: no <path>")
    return paths


def logo_group(fill: str) -> str:
    return "".join(f'<path d="{d}" fill="{fill}" fill-rule="evenodd"/>' for d in logo_paths())


def placed_logo(fill: str, box_w: float, box_h: float, logo_w: float, dy: float = 0.0) -> str:
    """The logo scaled to logo_w wide and centred in a box_w x box_h canvas."""
    scale = logo_w / LOGO_W
    x = (box_w - logo_w) / 2
    y = (box_h - LOGO_H * scale) / 2 + dy
    return f'<g transform="translate({x:.3f},{y:.3f}) scale({scale:.5f})">{logo_group(fill)}</g>'


def gradient(box_h: float) -> str:
    """The app icon's light: a blue glow right of centre on the dark ground (cairosvg has no blur)."""
    return (
        '<defs><radialGradient id="bg" cx="0.62" cy="0.5" r="0.62">'
        f'<stop offset="0" stop-color="{GLOW_CORE}"/><stop offset="0.3" stop-color="{GLOW}"/>'
        f'<stop offset="0.75" stop-color="{BG}"/><stop offset="1" stop-color="{BG}"/>'
        "</radialGradient></defs>"
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
        fg = placed_logo("#FFFFFF", 108, 108, 62)
        save(render(fg, 108, 108, round(108 * factor), round(108 * factor)),
             folder / "ic_launcher_foreground.png")

        # Legacy (API 24-25) square and round icons.
        size = round(48 * factor)
        square = (
            gradient(48)
            + '<rect x="2" y="2" width="44" height="44" rx="10" fill="url(#bg)"/>'
            + placed_logo("#FFFFFF", 48, 48, 32)
        )
        save(render(square, 48, 48, size, size), folder / "ic_launcher.png")
        round_icon = (
            gradient(48)
            + '<circle cx="24" cy="24" r="22" fill="url(#bg)"/>'
            + placed_logo("#FFFFFF", 48, 48, 30)
        )
        save(render(round_icon, 48, 48, size, size), folder / "ic_launcher_round.png")


def banner() -> None:
    folder = RES / "mipmap-xhdpi"
    body = gradient(180) + '<rect width="320" height="180" fill="url(#bg)"/>'
    body += placed_logo("#FFFFFF", 320, 180, 150)
    save(render(body, 320, 180, 320, 180), folder / "ic_banner.png")
    save(render(placed_logo("#FFFFFF", 320, 180, 150), 320, 180, 320, 180),
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
        plain = render(placed_logo("#FFFFFF", 24, 24, 23), 24, 24, px, px)
        save(plain, folder / "ic_stat_name.png")
        black = render(placed_logo("#000000", 24, 24, 23), 24, 24, px, px)
        if (folder / "ic_stat_name_black.png").exists():
            save(black, folder / "ic_stat_name_black.png")

        for name, badge in (("ic_stat_proxy", _globe), ("ic_stat_direct", _arrow)):
            base = render(placed_logo("#FFFFFF", 24, 24, 21, dy=-4), 24, 24, px, px)
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
    body = "\n".join(
        f'    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{d}" />'
        for d in logo_paths()
    )
    xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by branding/gen_icons.py from design/assets/logo.svg. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{LOGO_W:g}dp"
    android:height="{LOGO_H:g}dp"
    android:viewportWidth="{LOGO_W:g}"
    android:viewportHeight="{LOGO_H:g}">
{body}
</vector>
"""
    target = RES / "drawable/ic_geek_logo.xml"
    target.write_text(xml)
    print(target.relative_to(ROOT))


def launcher_background() -> None:
    """res/drawable/ic_launcher_background_geek.xml: the adaptive icon's ground with its blue light."""
    xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by branding/gen_icons.py (Logo-AppIcon.dc.html's light). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="67"
                android:centerY="54"
                android:gradientRadius="67">
                <item android:offset="0" android:color="#FF{GLOW_CORE[1:]}" />
                <item android:offset="0.3" android:color="#FF{GLOW[1:]}" />
                <item android:offset="0.75" android:color="#FF{BG[1:]}" />
                <item android:offset="1" android:color="#FF{BG[1:]}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""
    target = RES / "drawable/ic_launcher_background_geek.xml"
    target.write_text(xml)
    print(target.relative_to(ROOT))


if __name__ == "__main__":
    logo_vector()
    launcher_background()
    launcher_icons()
    banner()
    notification_icons()
