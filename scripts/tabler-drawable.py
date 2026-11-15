#!/usr/bin/env python3
"""tabler-drawable.py: turn Tabler Icons (outline) into Android vector drawables, ic_tabler_<name>.xml.

  scripts/tabler-drawable.py NAME...        e.g. sun-moon info-circle

Reads $TABLER_ICONS/outline/<name>.svg (default ~/Pictures/tabler-icons) and writes
app/src/main/res/drawable/ic_tabler_<name with _>.xml: white strokes, tinted where they are drawn. Credit new icons
in app/aboutlibraries (Settings > About > Open-source licenses lists Tabler Icons from there).
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TABLER = os.path.expanduser(os.environ.get("TABLER_ICONS") or "~/Pictures/tabler-icons")
VERSION = "3.51.0"


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for name in sys.argv[1:]:
        svg = open(os.path.join(TABLER, "outline", f"{name}.svg")).read()
        paths = [d for d in re.findall(r'<path[^>]*\sd="([^"]+)"', svg) if d != "M0 0h24v24H0z"]
        if not paths or re.search(r"<(circle|rect|line|polyline|polygon|ellipse)\b", svg):
            sys.exit(f"{name}: not only paths; convert it by hand")
        body = "".join(f"""
    <path
        android:pathData="{d}"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />""" for d in paths)
        out = os.path.join(HERE, "app", "src", "main", "res", "drawable", f"ic_tabler_{name.replace('-', '_')}.xml")
        open(out, "w").write(f"""<!-- Tabler Icons {VERSION}, {name} (outline). MIT License, Copyright (c) 2020-2026 Paweł Kuna. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">{body}
</vector>
""")
        print(os.path.relpath(out, HERE))


if __name__ == "__main__":
    main()
