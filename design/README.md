# GeekVPN design reference

The app follows the "Dark Swiss" canvas (GeekVPN Client — Dark Swiss).

- screens/  exact HTML source of the mobile artboards, Foundations and the logo
  sheets (colours, spacing, radii, inline SVG icons). The Yekan Bakh `@font-face`
  URLs point at the canvas and do not load outside it. The app ships Yekan Bakh
  when CI has its passphrase (docs/geekvpn.md, «فونت»), Vazirmatn otherwise.
- img/      screenshots of those artboards (JPEG, rendered without Yekan Bakh)
- assets/logo.svg  the mark: two lenses joined by a bridge, 240x120
- canvas.json  the full artboard list, including the desktop client's screens
- legacy/   the previous "Sky Lens" design. Only its icons are still used: those the
  Dark Swiss canvas does not draw (branding/gen_icons_kt.py looks there second).
