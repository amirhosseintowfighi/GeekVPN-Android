#!/usr/bin/env python3
"""Accessibility check over uiautomator dumps (spec phase 9).

For every clickable, checkable or focusable-for-input node of the app:
  - the touch target is at least MIN_DP x MIN_DP,
  - it has a label: its own text or content-desc, or a descendant's
    (Compose merges a button's children into one node for TalkBack).

usage: a11y-check.py <density_dpi> <package> <dump.xml>...
Prints one line per problem; exits 1 if any.
"""
import re
import sys
import xml.etree.ElementTree as ET

MIN_DP = 44
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def label(node):
    for n in node.iter():
        if (n.get("text") or "").strip() or (n.get("content-desc") or "").strip():
            return True
    return False


def interactive(node):
    return node.get("clickable") == "true" or node.get("checkable") == "true" or node.get("long-clickable") == "true"


def check(path, px_per_dp, package):
    problems = []
    with open(path, encoding="utf-8") as f:
        raw = f.read()
    # `uiautomator dump /dev/tty` appends "UI hierchary dumped to: /dev/tty".
    raw = raw[: raw.rfind("</hierarchy>") + len("</hierarchy>")]
    if not raw.strip():
        return []
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as e:
        return [f"{path}: unreadable dump ({e})"]
    parents = {child: parent for parent in root.iter() for child in parent}
    for node in root.iter("node"):
        if node.get("package") != package or not interactive(node) or node.get("enabled") != "true":
            continue
        m = BOUNDS.fullmatch(node.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        w, h = (x2 - x1) / px_per_dp, (y2 - y1) / px_per_dp
        # A node cut by the screen edge (a scrolled list) is not too small.
        clipped = y1 <= 0 or x1 <= 0 or y2 >= root_height(root) or x2 >= root_width(root) \
            or cut_by_scroller(node, parents, (x1, y1, x2, y2)) \
            or covered_below(root, node, parents, (x1, y1, x2, y2), px_per_dp)
        what = describe(node)
        if not clipped and w < MIN_DP - 0.5 or not clipped and h < MIN_DP - 0.5:
            problems.append(f"{path}: {what} is {w:.0f}x{h:.0f}dp, under {MIN_DP}dp")
        small = w < MIN_DP - 0.5 or h < MIN_DP - 0.5
        # A cut node's label may be the part scrolled away.
        if not (clipped and small) and not label(node):
            problems.append(f"{path}: {what} has no text or content description")
    return problems


def cut_by_scroller(node, parents, box):
    """The node sits on an edge of a scrolling ancestor, so part of it is scrolled away."""
    p = parents.get(node)
    while p is not None:
        if p.get("scrollable") == "true":
            m = BOUNDS.fullmatch(p.get("bounds", ""))
            if m:
                px1, py1, px2, py2 = map(int, m.groups())
                x1, y1, x2, y2 = box
                return y1 <= py1 or y2 >= py2 or x1 <= px1 or x2 >= px2
        p = parents.get(p)
    return False


def covered_below(root, node, parents, box, px_per_dp):
    """A wide overlay (the bottom navigation) starts at the node's lower edge: it is drawn over it."""
    x1, y1, x2, y2 = box
    width = root_width(root)
    lineage = set()
    p = node
    while p is not None:
        lineage.add(p)
        p = parents.get(p)
    for other in root.iter("node"):
        if other in lineage or node in parents_of(other, parents):
            continue
        m = BOUNDS.fullmatch(other.get("bounds", ""))
        if not m:
            continue
        ox1, oy1, ox2, oy2 = map(int, m.groups())
        if ox2 - ox1 >= 0.8 * width and y1 < oy1 <= y2 + 8 * px_per_dp and oy2 - oy1 > 40 * px_per_dp:
            return True
    return False


def parents_of(node, parents):
    out = []
    p = parents.get(node)
    while p is not None:
        out.append(p)
        p = parents.get(p)
    return out


def root_height(root):
    return int(BOUNDS.fullmatch(root.find("node").get("bounds")).group(4))


def root_width(root):
    return int(BOUNDS.fullmatch(root.find("node").get("bounds")).group(3))


def describe(node):
    text = (node.get("text") or node.get("content-desc") or "").strip()
    for n in node.iter():
        text = text or (n.get("text") or n.get("content-desc") or "").strip()
    return f"{node.get('class', '?').split('.')[-1]} '{text[:30]}' at {node.get('bounds')}"


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        return 2
    px_per_dp = int(sys.argv[1]) / 160
    package = sys.argv[2]
    problems = []
    for path in sys.argv[3:]:
        problems += check(path, px_per_dp, package)
    for p in problems:
        print(p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
