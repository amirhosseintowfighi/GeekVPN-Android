#!/usr/bin/env python3
"""Generate GeekIcons.kt (Compose ImageVectors) from the inline SVG icons in design/screens.

The design uses 24x24 stroke icons (round caps and joins). Each one used by the
app is listed in ICONS by the exact SVG body it has in design/, so a redesign
that changes an icon shows up as a KeyError here instead of silently drifting.

    python3 branding/gen_icons_kt.py
"""

from __future__ import annotations

import glob
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCREENS = ROOT / "design/screens"
OUT = ROOT / "V2rayNG/app/src/main/java/com/geekvpn/ui/icons/GeekIcons.kt"

# name -> the first words of text that follow the icon in design/ (for humans) and its SVG body.
ICONS: dict[str, str] = {
    "User": '<circle cx="12" cy="8" r="4"/><path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6"/>',
    "Telegram": '<path d="M21 4 3 11l7 2 2 7z"/><path d="m10 13 5-4"/>',
    "Wallet": '<rect x="3" y="6" width="18" height="14" rx="3"/><path d="M16 13h2"/><path d="M6 6V5a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1"/>',
    "Plus": '<path d="M12 5v14M5 12h14"/>',
    "Refresh": '<path d="M20 11a8 8 0 0 0-14.5-4.5L4 8"/><path d="M4 3v5h5"/><path d="M4 13a8 8 0 0 0 14.5 4.5L20 16"/><path d="M20 21v-5h-5"/>',
    "Globe": '<circle cx="12" cy="12" r="9"/><path d="M3 12h18"/><path d="M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z"/>',
    "ChevronStart": '<path d="m15 6-6 6 6 6"/>',
    "Route": '<circle cx="6" cy="19" r="2"/><circle cx="18" cy="5" r="2"/><path d="M6 17V9a4 4 0 0 1 4-4h6"/><path d="M6 13h8a4 4 0 0 1 4 4v2"/>',
    "Sliders": '<path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>',
    "Folder": '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
    "ThemeAuto": '<circle cx="12" cy="12" r="9"/><path d="M12 3a9 9 0 0 1 0 18z" fill="currentColor"/>',
    "Sun": '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    "Moon": '<path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"/>',
    "Headset": '<path d="M4 15v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="14" width="4" height="6" rx="1.5"/><rect x="17" y="14" width="4" height="6" rx="1.5"/>',
    "Download": '<path d="M12 4v11"/><path d="m7 10 5 5 5-5"/><path d="M5 20h14"/>',
    "Logout": '<path d="M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4"/><path d="M10 16l-4-4 4-4"/><path d="M6 12h10"/>',
    "Home": '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V20h14V9.5"/><path d="M10 20v-6h4v6"/>',
    "Shield": '<path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z"/><path d="m9 12 2 2 4-4"/>',
    "Bag": '<path d="M6 8h12l-1 12H7z"/><path d="M9 8V6a3 3 0 0 1 6 0v2"/>',
    "Bank": '<path d="M3 10 12 4l9 6"/><path d="M5 10v8M9 10v8M15 10v8M19 10v8M3 20h18"/>',
    "Copy": '<rect x="9" y="9" width="11" height="11" rx="2.5"/><path d="M5 15V6a2 2 0 0 1 2-2h8"/>',
    "Clock": '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    "Receipt": '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2z"/><path d="M9 8h6M9 12h6"/>',
    "Unlink": '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7"/><path d="M4 4l16 16"/>',
    "Check": '<path d="m5 12 5 5 9-10"/>',
    "Link": '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
    "Bolt": '<path d="M13 2 4 14h7l-1 8 9-12h-7z"/>',
    "Search": '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
    "Connections": '<circle cx="12" cy="12" r="2.5"/><circle cx="5" cy="5" r="2"/><circle cx="19" cy="5" r="2"/><circle cx="5" cy="19" r="2"/><circle cx="19" cy="19" r="2"/><path d="M7 7l3 3M17 7l-3 3M7 17l3-3M17 17l-3-3"/>',
    "ArrowDown": '<path d="M12 5v14"/><path d="m6 13 6 6 6-6"/>',
    "ArrowUp": '<path d="M12 19V5"/><path d="m6 11 6-6 6 6"/>',
    "Lock": '<rect x="5" y="11" width="14" height="9" rx="2.5"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    "Sparkle": '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/>',
    "Block": '<circle cx="12" cy="12" r="9"/><path d="m5.6 5.6 12.8 12.8"/>',
    "List": '<path d="M8 6h13M8 12h13M8 18h13M3 6h1M3 12h1M3 18h1"/>',
    "ArrowForward": '<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>',
    "Gauge": '<path d="M4 18a8 8 0 1 1 16 0"/><path d="m12 14 4-5"/>',
    "Gift": '<rect x="3" y="8" width="18" height="5" rx="1"/><path d="M5 13v8h14v-8M12 8v13"/><path d="M12 8C10 4 7 4 7 6s3 2 5 2c2 0 5 0 5-2s-3-2-5 2z"/>',
    "Tag": '<path d="M3 12V4h8l10 10-8 8z"/><circle cx="7.5" cy="8.5" r="1.3"/>',
    "Close": '<path d="M6 6l12 12M18 6 6 18"/>',
    "Pencil": '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
    "Card": '<rect x="3" y="5" width="18" height="14" rx="3"/><path d="M3 10h18M7 15h4"/>',
}

# The design is drawn right-to-left, so directional icons (ChevronStart,
# ArrowForward) already point the RTL way. Compose's autoMirror would flip them
# in RTL, the wrong way round, so a screen that uses one must flip it itself
# when the layout is left-to-right.


def _num(value: str) -> float:
    return float(value)


def _fmt(value: float) -> str:
    text = f"{value:.3f}".rstrip("0").rstrip(".")
    return text if text not in ("-0", "") else "0"


def element_to_path(tag: str, attrs: dict[str, str]) -> str:
    if tag == "path":
        return attrs["d"]
    if tag == "circle":
        cx, cy, r = (_num(attrs[k]) for k in ("cx", "cy", "r"))
        return (f"M{_fmt(cx - r)} {_fmt(cy)}a{_fmt(r)} {_fmt(r)} 0 1 0 {_fmt(2 * r)} 0"
                f"a{_fmt(r)} {_fmt(r)} 0 1 0 {_fmt(-2 * r)} 0z")
    if tag == "rect":
        x, y, w, h = (_num(attrs[k]) for k in ("x", "y", "width", "height"))
        r = _num(attrs.get("rx", "0"))
        if r == 0:
            return f"M{_fmt(x)} {_fmt(y)}h{_fmt(w)}v{_fmt(h)}h{_fmt(-w)}z"
        return (f"M{_fmt(x + r)} {_fmt(y)}h{_fmt(w - 2 * r)}a{_fmt(r)} {_fmt(r)} 0 0 1 {_fmt(r)} {_fmt(r)}"
                f"v{_fmt(h - 2 * r)}a{_fmt(r)} {_fmt(r)} 0 0 1 {_fmt(-r)} {_fmt(r)}"
                f"h{_fmt(-(w - 2 * r))}a{_fmt(r)} {_fmt(r)} 0 0 1 {_fmt(-r)} {_fmt(-r)}"
                f"v{_fmt(-(h - 2 * r))}a{_fmt(r)} {_fmt(r)} 0 0 1 {_fmt(r)} {_fmt(-r)}z")
    raise ValueError(f"unsupported SVG element <{tag}>")


def parse(body: str) -> list[tuple[str, bool]]:
    """(path data, filled) for every element of one icon."""
    out = []
    for match in re.finditer(r"<(\w+)((?:\s+[\w-]+=\"[^\"]*\")*)\s*/>", body):
        tag = match.group(1)
        attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', match.group(2)))
        out.append((element_to_path(tag, attrs), attrs.get("fill") == "currentColor"))
    return out


def design_bodies() -> set[str]:
    bodies = set()
    for f in glob.glob(str(SCREENS / "*.html")):
        text = pathlib.Path(f).read_text()
        bodies.update(re.findall(r'viewBox="0 0 24 24"[^>]*>(.*?)</svg>', text, re.S))
    return bodies


def main() -> None:
    present = design_bodies()
    missing = [name for name, body in ICONS.items() if body not in present]
    if missing:
        raise SystemExit(f"icons no longer in design/: {missing}")

    lines = [
        "// Generated by branding/gen_icons_kt.py from design/screens. Do not edit by hand.",
        "package com.geekvpn.ui.icons",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.graphics.SolidColor",
        "import androidx.compose.ui.graphics.StrokeCap",
        "import androidx.compose.ui.graphics.StrokeJoin",
        "import androidx.compose.ui.graphics.vector.ImageVector",
        "import androidx.compose.ui.graphics.vector.addPathNodes",
        "import androidx.compose.ui.unit.dp",
        "",
        "/** The design's 24dp stroke icons. Tint them with Icon(tint = ...); the black here is a mask. */",
        "object GeekIcons {",
    ]
    for name, body in ICONS.items():
        lines.append(f"    val {name}: ImageVector by lazy {{")
        lines.append(f'        icon("{name}") {{')
        for data, filled in parse(body):
            lines.append(f'            {"fill" if filled else "stroke"}("{data}")')
        lines.append("        }")
        lines.append("    }")
        lines.append("")
    lines += [
        "    private class Paths(val builder: ImageVector.Builder) {",
        "        fun stroke(data: String) {",
        "            builder.addPath(",
        "                pathData = addPathNodes(data),",
        "                stroke = SolidColor(Color.Black),",
        "                strokeLineWidth = 2f,",
        "                strokeLineCap = StrokeCap.Round,",
        "                strokeLineJoin = StrokeJoin.Round,",
        "            )",
        "        }",
        "",
        "        fun fill(data: String) {",
        "            builder.addPath(pathData = addPathNodes(data), fill = SolidColor(Color.Black))",
        "        }",
        "    }",
        "",
        "    private fun icon(name: String, block: Paths.() -> Unit): ImageVector {",
        "        val builder = ImageVector.Builder(",
        '            name = "Geek.$name",',
        "            defaultWidth = 24.dp,",
        "            defaultHeight = 24.dp,",
        "            viewportWidth = 24f,",
        "            viewportHeight = 24f,",
        "        )",
        "        Paths(builder).block()",
        "        return builder.build()",
        "    }",
        "}",
        "",
    ]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines))
    print(OUT.relative_to(ROOT), len(ICONS), "icons")


if __name__ == "__main__":
    main()
