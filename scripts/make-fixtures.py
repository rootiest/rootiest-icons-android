#!/usr/bin/env python3
"""make-fixtures.py: write core/src/test/resources/fixtures.json, the cases :core's tests compare its builder against.

Every expected value comes from rootiest-icons' own code (scripts/render-icon.py and the generator), so the tests fail
when the Kotlin port stops writing the same text. Run it again when rootiest-icons changes how it builds an icon.

  scripts/make-fixtures.py [ICONS_CHECKOUT]     default: $ROOTIEST_ICONS_DIR, then ~/projects/rootiest-icons

Needs that checkout's requirements (rsvg-convert, ImageMagick) and Python 3. The output is deterministic.
"""
import hashlib
import importlib.util
import json
import os
import random
import re
import sys
import tempfile
from types import SimpleNamespace

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICONS = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else
                           os.environ.get("ROOTIEST_ICONS_DIR") or "~/projects/rootiest-icons")


def load(name, path):
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main():
    site = load("make_site", os.path.join(ICONS, "scripts", "make-site.py"))
    ri = site.load_renderer()
    gen = ri.GEN
    colors, cfg = site.colors(ri), site.compose_data(ri)
    rng = random.Random(7)
    read = lambda *p: open(os.path.join(ICONS, *p)).read()
    spec_js = lambda s: None if s is None else {"kind": s[0], "value": s[1], "label": s[-1]}
    # Long texts are kept once, by a short key, and long expected outputs as their SHA-256: the file stays small.
    texts = {}
    sha = lambda t: hashlib.sha256(t.encode()).hexdigest()

    def key(text):
        k = None if text is None else "t" + sha(text)[:12]
        if k:
            texts[k] = text
        return k

    hexes = ["#ff8800", "#00b12f", "#7f7f7f", "#000000", "#ffffff"] + ["#%06x" % rng.randrange(0x1000000) for _ in range(4)]
    specs = [("cat", c, c) for c in ri.CATEGORY_NAMES] + [("cat", ri.STATE_NAMES[s], s) for s in ri.STATE_NAMES] + \
            [("hex", x) for x in hexes]

    # Recoloring: a few icons and emblems in every category, state and some hex colors, and the per-part options.
    recolor = []

    def add(text, is_emblem, **o):
        a = SimpleNamespace(icon="x", emblem=is_emblem, color=o.get("color"), logo_color=o.get("logo"),
                            emblem_color=o.get("emblem"), tone2_color=o.get("tone2"))
        want = ri.emblem_recolored(text, a) if is_emblem else ri.icon_recolored(text, a)
        recolor.append({"text": key(text), "emblem": is_emblem, "o": {k: spec_js(o.get(k)) for k in ("color", "logo", "emblem", "tone2")},
                        "want": sha(want)})

    for name in ("rootiest-gitea", "rootiest-vault", "logo", "icon-ok-circle", "rootiest-photos-header"):
        text = read("icons", "color", f"{name}.svg")
        for i, s in enumerate(specs):
            add(text, False, color=s)
            if name != "logo":
                add(text, False, logo=s, emblem=specs[(i * 7 + 3) % len(specs)])
        if name == "rootiest-gitea":
            for x in hexes:
                add(text, False, color=("cat", "dev", "dev"), tone2=("hex", x))
    for name in ("gitea", "home", "status"):
        text = read("icons", "emblems", f"{name}.svg")
        for s in specs:
            add(text, True, color=s)
        add(text, True, tone2=("hex", "#123456"))
    mono_cases = [{"text": key(read("icons", "emblems", "gitea.svg")), "main": m, "tone2": t,
             "want": sha(ri.recolor(read("icons", "emblems", "gitea.svg"), m, t))} for m, t in ri.MONO.values()]

    # The auto variant: an emblem's dark and light versions in one file (render-icon.py source_text), published ones
    # with class colors, in hex (gitea), two-toned and kept; and auto_svg() on its own, for the odd shapes it handles
    # and the pairs it refuses (want null).
    def auto_of(dark, light):
        try:
            return ri.auto_svg(dark, light)
        except ri.Failure:
            return None
    autos = []
    for name in ("id", "gitea", "home", "status", "gitea-mono", "immich-color", "files"):
        text = read("icons", "emblems", f"{name}.svg")
        autos.append({"text": key(text), "want": sha(auto_of(*(ri.recolor(text, *ri.MONO[v]) for v in ("dark", "light"))))})
    pairs = [
        ('<svg viewBox="0 0 1 1"><defs id="d"><path id="p"/></defs><path fill="#fff" stroke=\'#000\' class=\'a b\' d="M0 0"/>'
         '<g style="fill:#fff;stroke:#123456;stroke-width:2"><stop stop-color="#fff"/></g><rect fill="#fff"/></svg>',
         '<svg viewBox="0 0 1 1"><defs id="d"><path id="p"/></defs><path fill="#000" stroke=\'#000\' class=\'a b\' d="M0 0"/>'
         '<g style="fill:#000;stroke:#654321;stroke-width:2"><stop stop-color="#000"/></g><rect fill="#000"/></svg>'),
        ('<svg><defs>\n<style id="current-color-scheme">\n .ColorScheme-Accent{color:#fff}.x { fill:#fff; opacity:.5 }\n</style>'
         '<style>.y{stroke:#abc}.z{stroke:#abc}</style></defs><circle class="" fill="#fff"/><circle fill="#fff" /></svg>',
         '<svg><defs>\n<style id="current-color-scheme">\n .ColorScheme-Accent{color:#000}.x { fill:#000; opacity:.5 }\n</style>'
         '<style>.y{stroke:#abc}.z{stroke:#cba}</style></defs><circle class="" fill="#000"/><circle fill="#000" /></svg>'),
        ('<svg><defs><style>.a{fill:#fff}</style></defs><path fill="#fff"/></svg>', '<svg><defs><style>.a{fill:#fff}</style></defs><path fill="#000"/></svg>'),
        ('<svg><path fill="#fff"/></svg>', '<svg><path fill="#fff"/></svg>'),
        ('<svg><path fill="#fff"/></svg>', '<svg><path fill="#000"/><path/></svg>'),
        ('<svg><path fill="#fff" d="M1 1"/></svg>', '<svg><path fill="#000" d="M2 2"/></svg>'),
        ('<svg><style>@media (prefers-color-scheme: light){.a{fill:#fff}}</style></svg>',
         '<svg><style>@media (prefers-color-scheme: light){.a{fill:#000}}</style></svg>'),
        ('<svg>#fff<path/></svg>', '<svg>#000<path/></svg>'),
    ]
    for dark, light in pairs:
        got = auto_of(dark, light)
        autos.append({"dark": dark, "light": light, "want": got})

    shade_hexes = ["#%06x" % rng.randrange(0x1000000) for _ in range(400)] + hexes
    shades = [{"hex": x, "want": {k: list(v) for k, v in ri.shades(("hex", x)).items()}} for x in shade_hexes]

    # Built icons: as scripts/check-site-colors.py in rootiest-icons builds its cases.
    built, alone = [], []

    def emblem(body, bbox, about, color="@rootiest", color2=None, grey2=None, nudge=(1.0, 0.0, 0.0), mono=None):
        py = SimpleNamespace(body=body, mono=mono, bbox=tuple(bbox), about=about, nudge=nudge, color=color,
                             color2=color2, grey2=grey2, two_tone="ColorScheme-Emblem2" in body)
        js = {"body": key(body), "mono": key(mono), "bbox": list(bbox), "about": about, "nudge": list(nudge),
              "colors": site.colors_table(ri, color, color2, grey2)}
        return py, js

    def add_built(e, logo, variant, padded, place, thick=None):
        epy, ejs = e if e else (None, None)
        if epy and thick:
            epy = SimpleNamespace(**{**vars(epy), "body": ri.thicken(epy.body, thick)})
        built.append({"e": ejs, "thick": thick, "logo": logo and logo[1], "variant": variant, "padded": padded,
                      "place": {"preset": place[0], "scale": place[1], "dx": place[2], "dy": place[3]},
                      "want": sha(ri.built_svg(epy, logo and logo[0], variant, padded, place))})

    def own_logo(raw, keep, bbox, about):
        with tempfile.TemporaryDirectory() as t:
            def body_of(kept):
                path = os.path.join(t, f"logo-{kept}.svg")
                open(path, "w").write(ri.own_text(raw, kept))
                return gen.load_emblem(path)[1]
            body, mono = body_of(keep), body_of(False) if keep else None
        tr = gen.fit_logo(bbox)
        return (SimpleNamespace(body=body, mono=mono, transform=tr, about=about),
                {"body": key(body), "mono": key(mono), "transform": tr, "about": about})

    hexagon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><path fill="#333" d="M50 4 92 27v46L50 96 8 73V27z"/></svg>'
    styled = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 120"><defs><style>.a{fill:#e30613;}.b,.c{fill:#1d71b8;'
              'stroke:#000}</style></defs><!-- x --><rect class="a" width="120" height="120" rx="20"/>'
              '<circle class="b" style="stroke-width:4" cx="150" cy="60" r="48"/></svg>')
    logos = [None, own_logo(hexagon, False, (8.0, 4.0, 84.0, 92.0), "acme.svg"),
             own_logo(styled, True, (0.0, 0.0, 198.5, 120.0), "Two--Tone.svg"),
             own_logo(styled, False, (5.0, -3.0, 30.0, 90.0), "tall.svg")]
    places = [("rootiest", 1.0, 0.0, 0.0), ("badge", 1.0, 0.0, 0.0), ("rootiest", 1.35, -166.07, 99.642),
              ("badge", 0.5, 33.214, -332.14), ("badge", 3.0, 0.0, 0.0)]
    for src, thick in (("emblems/src/status.svg", 1.3), ("emblems/src/status.svg", 1.75), ("emblems/gitea.svg", None),
                       ("emblems/home.svg", None)):
        vb, body = gen.load_emblem(src)
        tb = ri.thicken(body, thick) if thick else body
        for k, bbox in enumerate([ri.measured(vb, tb), (0.5, 2.25, 23.0, 4.5), (6.0, 0.0, 3.0, 24.0), (-3.0, -1.5, 30.0, 30.0)]):
            e = emblem(body, bbox, "tabler:x from Tabler Icons by A--B, MIT, via Iconify")
            for i, (variant, padded) in enumerate((("color", False), ("dark", True), ("light", False), ("auto", k % 2 == 1))):
                add_built(e, logos[(i + k) % len(logos)], variant, padded, places[(i + 2 * k) % len(places)], thick)
                add_built(e, None, variant, padded, places[0], thick)
            for keep in (False, True):
                alone.append({"body": key(body), "thick": thick, "bbox": list(bbox), "keep": keep, "about": e[0].about,
                              "want": sha(ri.emblem_svg(tb, tuple(bbox), keep, e[0].about))})
    rows = ri.manifest_rows()
    for name in ("rootiest-gitea", "rootiest-files", "rootiest-id-day", "icon-ok-circle", "rootiest-photos-header", "rootiest-nvim"):
        row = rows[name]
        path = os.path.join(ri.ICONS, "emblems", f"{row.emblem}.svg")
        vb, body = gen.load_emblem(path)
        mono = re.sub(r'\sfill="(?!none")[^"]*"', "", body) if "data-keep-fill" in open(path).read() else None
        e = emblem(body, ri.measured(vb, body), f"the {row.emblem} emblem", row.color, row.color2, row.grey2, row.nudge, mono)
        for i, variant in enumerate(("color",) if row.color_only else ("color", "dark", "light", "auto")):
            add_built(e, logos[1 + i % 3], variant, i == 1, places[i % len(places)])
            add_built(e, None, variant, False, places[(i + 1) % len(places)])
    vb, body = gen.load_emblem(os.path.join(ri.ICONS, "emblems", "gitea.svg"))
    for color, color2, grey2 in (("@storage", None, "mid"), ("#123456", "#fedcba", None), ("@infra/warn", "#ffffff", "mid")):
        e = emblem(body, ri.measured(vb, body), "the gitea emblem", color, color2, grey2, (1.1, -20.0, 35.5))
        for i, variant in enumerate(("color", "dark", "light", "auto")):
            add_built(e, logos[i % len(logos)], variant, i == 2, places[(i + 3) % len(places)])
    for logo in logos[1:]:
        for variant, padded in (("color", False), ("dark", True), ("light", False), ("auto", False), ("auto", True)):
            add_built(None, logo, variant, padded, places[0])

    # Files of your own: fitting, preparing the text, cleaning, names.
    fits = [(0.0, 0.0, 100.0, 100.0), (8.0, 4.0, 84.0, 92.0), (0.0, 0.0, 198.5, 120.0), (5.0, -3.0, 30.0, 90.0),
            (-12.25, 7.5, 1024.0, 3.0), (0.03, 0.01, 0.5, 0.75)]
    raws = [hexagon, styled,
            '<svg viewBox="0 0 24 24"><path d="M1 1h4" stroke="#123" style="stroke: red;stroke-width:2;fill:#abc"/>'
            "<g class='c' stroke='none'><circle r='2' style='fill: none'/></g><polygon points='1 2 3 4'/>"
            '<line x1="0" stroke="currentColor"/></svg>',
            '<svg xmlns="http://www.w3.org/2000/svg"><style><![CDATA[ /* c */ .x , .y{fill:#fff} .z:hover{fill:red} ]]></style>'
            '<path class="x z" d="M1 1"/><rect class="y" style="fill:#000"/></svg>']
    cleans = []
    with tempfile.TemporaryDirectory() as t:
        sources = [(f"emblems/{n}.svg", read("emblems", f"{n}.svg")) for n in ("gitea", "home", "immich-color", "files", "id")] + \
                  [(f"emblems/src/{n}.svg", read("emblems", "src", f"{n}.svg")) for n in ("status", "lock", "badge-ok-circle")] + \
                  [(f"own-{i}-{k}", ri.own_text(r, k)) for i, r in enumerate(raws[:2]) for k in (False, True)] + \
                  [("odd", '<svg xmlns="http://www.w3.org/2000/svg" xmlns:x="urn:x" width="10" height="20"><title>t</title>'
                           '<g id="a" x:y="1" data-tone="2"><path d="M1. 2.5l3. 4" fill="#fff" style="color:red;fill:#00f"/>'
                           '<text>no</text><g><rect width="1" height="1" fill="none" data-note="a&amp;b &lt; &quot;c&quot;"/></g></g>'
                           '<a><path d="M0 0"/></a><circle r="1" data-keep-fill="" fill="#abc"/></svg>')]
        for name, raw in sources:
            path = os.path.join(t, "c.svg")
            open(path, "w").write(raw)
            vb, body = gen.load_emblem(path)
            cleans.append({"name": name, "raw": key(raw), "viewBox": vb, "body": sha(body)})
    own = {
        "fits": [{"bbox": list(b), "want": gen.fit_logo(b)} for b in fits],
        "texts": [{"raw": r, "keep": k, "want": ri.own_text(r, k)} for r in raws for k in (False, True)],
        "slugs": [{"name": n, "want": ri.slug(n)} for n in ("My Logo.svg", "acme.svg", "~/art/Two--Tone Logo.SVG", ".svg", "a.b.svg", "plain")],
        "tags": [{"preset": p, "scale": sc, "x": x, "y": y,
                  "want": ri.placement_tag(SimpleNamespace(placement=p, emblem_scale=sc, emblem_x=x, emblem_y=y))}
                 for p, sc, x, y in (("rootiest", None, 0, 0), ("badge", None, 0, 0), ("badge", 1.2, 5, -3),
                                     ("rootiest", 0.25, 0, 12.5), ("rootiest", 1, -50, 0))],
        "cleans": cleans,
    }

    # Icons drawn from the Iconify API's data: flips, rotations, offset boxes, aliases.
    body = '<path fill="currentColor" d="M1 1h14v14H1z"/>'
    square = {"width": 24, "height": 24, "icons": {
        "plain": {"body": body}, "boxed": {"body": body, "left": 0.5, "top": -1.25, "width": 23, "height": 26.5},
        "wide": {"body": body, "width": 1792, "height": 1408}, "tall": {"body": body, "left": 3, "top": 7, "height": 41.7},
        **{f"r{r}{h}{v}": {"body": body, "left": 2, "top": 1.5, "width": 20, "height": 31, "rotate": r, "hFlip": bool(h),
                           "vFlip": bool(v)} for r in range(4) for h in (0, 1) for v in (0, 1)}},
        "aliases": {"flipped": {"parent": "wide", "hFlip": True}, "back": {"parent": "r010", "hFlip": True},
                    "turned": {"parent": "r310", "rotate": 1}, "upside": {"parent": "r101", "vFlip": True, "rotate": 3},
                    "chain": {"parent": "flipped", "vFlip": True, "width": 30}, "chain2": {"parent": "chain", "rotate": 2},
                    "loop": {"parent": "loop2"}, "loop2": {"parent": "loop"}, "lost": {"parent": "nowhere"}}}
    sixteen = {"icons": {"odd": {"body": body}, "offset": {"body": body, "left": -4, "top": 0.1, "width": 3.3}}}
    icondata = [{"data": d, "icon": i, "want": ri.icon_svg(x) if (x := ri.icon_data(d, i)) else None}
                for d, names in ((square, [*square["icons"], *square["aliases"], "missing"]), (sixteen, ["odd", "offset", "missing"]))
                for i in names]

    # File names, against render-icon.py's planned().
    plans = []
    for i, (stem, is_emblem) in enumerate((("rootiest-gitea", False), ("gitea", True), ("acme-tabler-star-badge", False))):
        for variant, padded in (("color", False), ("dark", True), ("light", False)):
            for o in ({}, {"color": specs[i]}, {"logo": ("cat", "dev", "dev"), "emblem": ("hex", "#ff8800"), "tone2": ("hex", "#ffffff")},
                      {"color": ("hex", "#18bcf2", "brand")}):
                layouts = [["auto"], ["auto", "full", 16], [48, 24]][(i + len(o)) % 3]
                sizes, formats = [[256], [16, 32, 48], [1024, 64]][i % 3], [["png"], ["png", "webp", "ico", "svg"], ["svg", "ico"]][len(o) % 3]
                animate, loop, duration = [("none", False, None), ("draw", False, None), ("draw", True, None), ("draw", True, 6.5),
                                           ("heartbeat", False, None), ("heartbeat", False, 1.25), ("morph", True, 3.5),
                                           ("morph", False, None)][(i + len(o) + len(variant)) % 8]
                morph_to = "rootiest:tabler:heart" if animate == "morph" else None
                formats = formats + ["gif"] if animate != "none" and "webp" in formats else formats
                a = SimpleNamespace(icon=stem, stem_name=stem, emblem=is_emblem, variant=variant, padded=padded and not is_emblem,
                                    color=o.get("color"), logo_color=o.get("logo"), emblem_color=o.get("emblem"),
                                    tone2_color=o.get("tone2"), layouts=layouts, sizes=sizes, formats=formats, out="",
                                    animate=animate, loop=loop, duration=duration, morph_to=morph_to)
                plans.append({"stem": stem, "emblem": is_emblem, "variant": variant, "padded": a.padded,
                              "o": {k: spec_js(o.get(k)) for k in ("color", "logo", "emblem", "tone2")},
                              "layouts": [str(v) for v in layouts], "sizes": sizes, "formats": formats,
                              "animate": animate, "loop": loop, "duration": duration, "morphTo": morph_to, "want": ri.planned(a)})

    # Layouts: one kept, as the CLI's --layout N --format svg.
    icon = read("icons", "color", "rootiest-gitea.svg")
    small = ri.layouts_of(icon)
    layouts = {"text": key(icon), "small": small,
               "single": {str(l): sha(ri.single_layout_svg(icon, l, small)) for l in ["full", *small]},
               "resolve": [{"layout": str(l), "size": s, "want": str(ri.resolve(l, s, small))}
                           for l in ("auto", "full", 24) for s in (8, 16, 17, 24, 48, 49, 96, 97, 4096)]}

    # Animations (--animate draw and heartbeat): published icons and emblems, a few built ones, one layout alone, odd
    # shapes and groups, each with its default timing and others.
    single_layout = lambda t, lay: ri.single_layout_svg(t, lay, ri.layouts_of(t))
    built_emblems = []
    for src, thick in (("emblems/src/status.svg", 1.3), ("emblems/gitea.svg", None)):
        vb, body = gen.load_emblem(src)
        tb = ri.thicken(body, thick) if thick else body
        built_emblems.append(emblem(tb, ri.measured(vb, tb), "an emblem for the animations")[0])
    timings = [("draw", None, False), ("heartbeat", None, False), ("draw", 4.0, True), ("heartbeat", 0.75, False),
               ("pulse", None, False), ("spin", 3.5, False), ("shake", None, False), ("pop", 0.25, False),
               ("shine", None, False), ("draw", 0.2, False), ("draw", None, True), ("pop", None, False),
               ("shine", 1.75, False)]
    sources = [read("icons", v, f"{n}.svg") for v in ("color", "dark") for n in ("rootiest-gitea", "rootiest-status", "logo")] + \
              [read("icons", "color", "icon-ok-circle.svg"), single_layout(read("icons", "color", "rootiest-vault.svg"), 24)] + \
              [read("icons", "emblems", f"{n}.svg") for n in ("gitea", "status", "immich-color", "home")] + \
              [ri.built_svg(epy, None, "color", False, places[0]) for epy in built_emblems[:2]] + [
        '<svg viewBox="0,0,10,20"><path d="M1 1"/><g transform="matrix(2 0 0 3 1 1) rotate(9)" style="fill:none">'
        '<rect width="2" stroke="red"/><circle r="1" style="fill: #abc ;stroke:none"/></g><defs><path d="M0 0"/></defs>'
        '<line x2="4" stroke="currentColor" /><mask id="m"><rect fill="white"/></mask><polyline fill="none" points="0 0 1 1"/></svg>',
        '<svg><g fill="url(#g)" transform="scale(.5) translate(3 4) scale(-2,4)"><ellipse rx="2" ry="1" class="a b"/></g>'
        '<svg:path d="M2 2"/></svg>',
        '<svg viewBox="0 0 9 9"><g class="ColorScheme-Emblem x"><g><path d="M1 1"/></g><g/></g><path d="M2 2"/>'
        '<g class="ColorScheme-Emblem2"><path d="M3 3"/></g><g class="a ColorScheme-Emblem"><rect/></g></svg>',
        '<svg viewBox="0 0 1 1"><defs><mask id="m"/></defs><circle r="1"/></svg>', "no svg here"]
    anims = [{"text": key(t), "animate": an, "duration": d, "loop": lp, "want": sha(ri.animate_svg(t, an, d, lp))}
             for t in sources for an, d, lp in timings[:9]] + \
            [{"text": key(t), "animate": an, "duration": d, "loop": lp, "want": sha(ri.animate_svg(t, an, d, lp))}
             for t in sources[:3] for an, d, lp in timings[9:]]
    anim_tags = [{"animate": an, "loop": lp, "duration": d, "morphTo": "rootiest:tabler:heart" if an == "morph" else None,
                  "want": ri.animation_tag(SimpleNamespace(animate=an, loop=lp, duration=d,
                                                           morph_to="rootiest:tabler:heart" if an == "morph" else None)),
                  # the animated rasters' frames: how many, the last moment, and whether it plays once
                  "frames": (lambda t: [len(t[0]), t[0][-1], t[1]])(ri.frame_times(SimpleNamespace(animate=an, loop=lp, duration=d)))}
                 for an in ri.ANIMATIONS if an != "none" for lp in (False, True) for d in (None, 1.0, 0.25, 12.5, 2.5, 0.7)]
    # Morphs, as rootiest-icons' check-site-colors.py makes them: published icons, padded dark, badges, one layout,
    # emblems in their own units, the logo both ways, second tones, built icons; once and there and back.
    morph_sets = [(read("icons", "color", "rootiest-gitea.svg"), read("icons", "color", "rootiest-home.svg")),
                  (read("icons", "color", "rootiest-notes.svg"), read("icons", "color", "rootiest-chat.svg")),
                  (read("icons", "dark", "padded", "rootiest-lock.svg"), read("icons", "dark", "padded", "rootiest-id.svg")),
                  (read("icons", "color", "icon-ok-circle.svg"), read("icons", "color", "icon-bad-triangle.svg")),
                  (read("icons", "emblems", "heart.svg"), read("icons", "emblems", "status.svg")),
                  (read("icons", "emblems", "sync.svg"), read("icons", "emblems", "gitea.svg")),
                  (read("icons", "color", "logo.svg"), read("icons", "color", "rootiest-files.svg")),
                  (single_layout(read("icons", "color", "rootiest-vault.svg"), 24), single_layout(read("icons", "color", "rootiest-mail.svg"), 24)),
                  # second tones: on both sides, on one, in dark and switching with the viewer's mode
                  (read("icons", "color", "rootiest-gitea.svg"), read("icons", "color", "rootiest-gitea-ssh.svg")),
                  (read("icons", "color", "rootiest-github.svg"), read("icons", "color", "rootiest-gitea.svg")),
                  (read("icons", "dark", "padded", "rootiest-gitea-ssh.svg"), read("icons", "dark", "padded", "rootiest-home.svg")),
                  (read("icons", "auto", "rootiest-gitea.svg"), read("icons", "auto", "rootiest-github.svg")),
                  (read("icons", "emblems", "gitea-ssh-mono.svg"), read("icons", "emblems", "gitea-mono.svg"))]
    morph_sets += [(ri.built_svg(built_emblems[0], None, "color", False, places[0]),
                    ri.built_svg(built_emblems[1], None, "color", False, places[1]))]
    anims += [{"text": key(a), "target": key(b), "animate": "morph", "duration": d, "loop": lp,
               "want": sha(ri.animate_svg(a, "morph", d, lp, b))}
              for i, (a, b) in enumerate(morph_sets) for lp, d in ((False, None), (True, 2.5 + i % 3))]

    # Number formatting: Python's own.
    values = [0.5, 1952.8125, 1952.8135, 0.125, -0.125, -0.0005, 2.5, 1.005, 12345.6789, 1e-7, 123456789.0, 0.0001234, 100.0,
              3321.4, -86.7, 1.3, 2.6, 0.00001] + [rng.uniform(-5000, 5000) for _ in range(200)] + \
             [rng.randrange(-80000, 80000) / 16 for _ in range(200)]
    numbers = [{"x": v, "f3": f"{v:.3f}", "f4": f"{v:.4f}", "g": f"{v:g}", "g4": f"{v:.4g}", "js": ri.js_num(v)} for v in values]

    out = {"colors": colors, "cfg": cfg, "recolor": recolor, "mono": mono_cases, "autos": autos, "shades": shades, "built": built, "alone": alone,
           "own": own, "texts": texts, "icondata": icondata, "plans": plans, "layouts": layouts, "numbers": numbers,
           "anims": anims, "animTags": anim_tags}
    path = os.path.join(HERE, "core", "src", "test", "resources", "fixtures.json")
    with open(path, "w") as f:
        json.dump(out, f, separators=(",", ":"), sort_keys=True)
    print(f"{os.path.relpath(path, HERE)}: {len(recolor)} recolorings, {len(shades)} shades, {len(built)} built icons, "
          f"{len(alone)} emblems, {len(cleans)} cleaned files, {len(icondata)} Iconify icons, {len(plans)} plans, "
          f"{len(anims)} animations, "
          f"{len(numbers)} numbers, {os.path.getsize(path) // 1024} KB")


if __name__ == "__main__":
    main()
