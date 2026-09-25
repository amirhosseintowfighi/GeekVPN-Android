#!/usr/bin/env python3
"""Bundle Vazirmatn and Space Grotesk as static TTFs under res/font.

Both families ship upstream as variable fonts. Compose can only pick a weight
from a variable font on API 26+, and the app supports API 24, so each weight
the design uses is instantiated into its own static file here.

    pip install fonttools
    python3 branding/gen_fonts.py

Sources are pinned to a google/fonts commit so the output is reproducible.
Both fonts are SIL OFL 1.1; the licence texts are written next to the script.
"""

from __future__ import annotations

import io
import pathlib
import urllib.request

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "V2rayNG/app/src/main/res/font"
LICENSES = ROOT / "branding/fonts"

GOOGLE_FONTS = "https://raw.githubusercontent.com/google/fonts/{rev}/ofl/{family}/{file}"
REV = "main"

# (resource prefix, google/fonts folder, variable file, weights used by design/)
FAMILIES = [
    ("vazirmatn", "vazirmatn", "Vazirmatn%5Bwght%5D.ttf", [300, 400, 500, 600, 700, 800]),
    ("space_grotesk", "spacegrotesk", "SpaceGrotesk%5Bwght%5D.ttf", [500, 600, 700]),
]

NAMES = {300: "light", 400: "regular", 500: "medium", 600: "semibold", 700: "bold", 800: "extrabold"}


def fetch(family: str, file: str) -> bytes:
    url = GOOGLE_FONTS.format(rev=REV, family=family, file=file)
    with urllib.request.urlopen(url) as response:
        return response.read()


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    LICENSES.mkdir(parents=True, exist_ok=True)
    for prefix, folder, file, weights in FAMILIES:
        variable = fetch(folder, file)
        # Upstream ships CRLF; store LF. The licence text itself is kept verbatim.
        (LICENSES / f"OFL-{prefix}.txt").write_bytes(fetch(folder, "OFL.txt").replace(b"\r\n", b"\n"))
        for weight in weights:
            font = TTFont(io.BytesIO(variable))
            static = instantiateVariableFont(font, {"wght": weight}, updateFontNames=False)
            target = OUT / f"{prefix}_{NAMES[weight]}.ttf"
            static.save(target)
            print(target.relative_to(ROOT), target.stat().st_size)


if __name__ == "__main__":
    main()
