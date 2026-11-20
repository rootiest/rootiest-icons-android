# rootiest-icons-android documentation

Rootiest Icons for Android: browse the Rootiest icons and render any of them, or your own, at any size.

- [Policy: docs are the single source of truth](#policy-docs-are-the-single-source-of-truth)
- [What the app does](#what-the-app-does)
- [Where its data comes from](#where-its-data-comes-from)
- [Architecture](#architecture)
- [The builder and its fixtures](#the-builder-and-its-fixtures)
- [Drawing](#drawing)
- [Files a render writes](#files-a-render-writes)
- [Look](#look)
- [Tests](#tests)
- [CI](#ci)
- [Releases](#releases)
- [Scripts](#scripts)

## Policy: docs are the single source of truth

Everything about this project is documented here. A change is not done until the docs
under this directory describe it. Generated documents are never edited by hand: edit
the source and regenerate.

## What the app does

The app is [icons.rootiest.fyi](https://icons.rootiest.fyi) for a phone, with the same features and the same results.

| Screen | What it holds |
|---|---|
| **Catalogue** | Every icon, status icon and badge, and emblem, as tiles, with the animated status badges between the status icons and the emblems. The search box filters them and, from three letters, looks through the other sets as well (`cloud`, or `tabler:cloud` for one set). A tap on a tile's name copies the name (`rootiest-gitea`, `tabler:cloud`), for the command line or the morph's "by name". Color, Auto, Dark and Light switch the variant; the chips filter by category or status state. The search box, the variant, the light/dark toggle and the chips stay at the top while the tiles scroll. A card at the top (with an empty search) and a button in the top bar open **Drawn with Math**. |
| **Animated status badges** | As on the homepage, and hidden as there while searching or filtering: badges whose mark morphs from one state to another (`color/animated/` in `index.json`'s `animated` list). Circle, Square, Triangle and Heartbeat choose the shape; the shape's cycle, every state in turn, plays above a matrix of its changes, from (rows) to (columns), the heartbeat's loops (each state beating) down its diagonal. Each plays as an animated WebP of its frames, worked out from the file's own timing (its CSS animations' and SMIL animates' durations and iteration counts: a loop loops, a change plays once and holds its end). To keep the matrix quick, a change shows its end, the new state, until it is tapped; it is then drawn once and kept for the session. A tap plays a badge again and chooses it, and the row below saves its SVG to `Downloads/Rootiest Icons`, shares it, or opens its detail screen: the badge playing, its layouts, the padded twin, and the Render panel, which renders it as `render-rootiest-icons <badge>` does (its SVG as it is or one layout, its WebP and GIF animated by its own timing, its PNG and ICO its first frame; no colors or animation, as those are part of the badge), starting on an animated GIF; an animation chosen there plays after the badge, from the state it ends on. They follow the light or dark preview toggle. |
| **Light and dark previews** | Auto shows each icon's `auto/` file: the dark and light versions in one SVG that switches with the viewer's mode (an icon without one shows its color version, faded; an emblem shows in white or black, the way its auto version shows in that mode). A file that switches by itself (`auto/`, or an `-auto` icon such as `rootiest-id-auto`) has a small auto-brightness badge on its tile. The sun or moon button next to the variant previews every SVG that switches (the auto files, and the color icons, whose emblem takes a deeper shade in light mode) in light or dark mode, with a light or dark background to match; it starts as the app's appearance and keeps its choice for the session. |
| **Detail** | One icon or emblem: its preview, its details, the variant, the padded twin, and for an emblem whether it is on the logo or alone. With a logo of your own loaded, Rootiest logo or Your logo. Placement (upper right or badge, size, right, down), the five layouts side by side, and the Render panel. The details say how it takes light or dark mode (it follows it, its emblem takes a deeper shade, or the same in both), and a preview that switches has the light/dark toggle in its corner. Auto, the dark and light versions in one SVG that follows the viewer's mode, is a variant of every icon with an auto file, every emblem, every icon from another set and your own files (not of the color-only icons): a published icon in it is its `auto/` file, and one on your logo or placed elsewhere is built here, as `render-rootiest-icons --variant auto` builds it. Since a raster cannot switch, the Render panel makes the SVG only in Auto (no sizes, formats or background). The custom colors work in Auto too, as `--variant auto` takes them: one color is the color variant's, the logo and emblem drawn a little lighter in dark mode and deeper in light mode (`neutral`, which already switches, as it is), and a dark/light pair is used exactly. An `-auto` icon says it is SVG only and points to its day and night versions for rasters. |
| **Render panel** | The colors (a category, a status state, a brand's own, or any hex color, for the whole icon or per part, in Color and Auto; in Auto also a **Dark/light pair**, one hex color for dark backgrounds and one for light ones, `#cdd6f4` and `#4c4f69` to start, shown in the command as `--color cdd6f4/4c4f69` and in the file names as `cdd6f4-4c4f69`; leaving Auto drops a pair), the layouts, any sizes up to 4096 px, PNG, WebP, ICO, SVG and GIF, an animation for the SVG, WebP and GIF (Draw: it draws itself, once or looping; Heartbeat: a heart monitor's sweep across the emblem; Pulse, Spin, Shake: the emblem breathes, turns or wobbles; Pop: it bounces in, once; Shine: a glint crosses it; Morph: it turns into another icon of the same kind (published, from another set by name, or your emblem file, built on the same logo and placement), once or there and back (**Then another animation…** under Render continues the render with another, from the still it ends on, in one SVG, as `render-rootiest-icons <that file> --animate …` does: a line names the steps so far, with Undo; Loop loops the whole chain; the command shows each run, every SVG feeding the next), its emblem in the icon's colors, as published (its own) or another, and its second tone, when it has one, another, as `--morph-color` and `--morph-tone2-color` give them; and a duration), and a background to flatten onto. The preview above plays the chosen animation (in light or dark mode, as the preview is); a tap plays it again. Render draws every file, and the tiles of animated files play; Save writes them to `Downloads/Rootiest Icons`, Share hands one file, or a zip of several, to another app. The command that writes the same files with `render-rootiest-icons` is shown and can be copied. |
| **Your own** | A logo and an emblem of your own, each an SVG from a file or the clipboard, with Keep its colors. They are kept in the app's private storage and never leave the phone. An SVG shared to the app, or opened with it, becomes your emblem. |
| **Drawn with Math** | The site's interactive article on why SVG ([icons.rootiest.fyi/svg/](https://icons.rootiest.fyi/svg/)), in the app: the page in a WebView, light or dark as the app's appearance is. Its builder's downloads are saved to `Downloads/Rootiest Icons` and its Copy code goes to the clipboard. Back steps back through the page, then to the app; its links home return to the catalogue, other links open in the browser, and the top bar opens it in the browser too. |
| **Settings** | Appearance (the phone's, light or dark), the preview background, refreshing the catalogue, and About: Drawn with Math, the version, links to the site, the command line tool, the status page (`status.rootiest.dev`), the source code and the privacy policy (`rootiest.com/privacy/icons-app`, the page the Google Play listing links), **Report an issue**, which opens a new support desk ticket (`support.rootiest.dev/new`, the desk's Android app topic) filled in with the app, Android and phone versions for the person to finish and send, and the open-source licenses. |

What differs from the command line tool:

- **Sizes** stop at 4096 px (the tool goes to 16384): a larger bitmap does not fit a phone's memory reliably.
- **Rasters** are drawn by AndroidSVG, not librsvg, so their anti-aliased edges differ slightly. The SVG files are the
  same, byte for byte, for published icons; for an icon built here the emblem's measured box can differ by a
  thousandth of its size, as on the homepage.
- **WebP** is lossless on Android 11 and later; Android 10 writes it at the best lossy quality.
- **Animations** are written into the SVG, byte for byte as the tool writes them. AndroidSVG draws an SVG still, so
  the app plays one as an animated WebP of its frames: the preview a moment after a choice changes (the still until
  then), and the SVG's tile. The animated WebP and GIF hold the same frames as the tool's (25 a second, a run of
  identical frames kept as one), each drawn by AndroidSVG and put together here rather than by ImageMagick, so the
  files' bytes differ. A GIF has a palette of its own per frame and no partial transparency, and each frame keeps only
  the box of pixels that changed (`:core`'s `GifEncoder`, as the homepage writes them), which is lossless.
- **Morph** works on every icon the app makes, published or built (on your logo, placed elsewhere, from another set
  or your file), into a published icon or emblem, an icon from another set by name, or your emblem file, built as the
  tool's `--morph-to` builds it, in Auto too (into a published icon that has an auto file).

## Where its data comes from

- **The catalogue** is `https://icons.rootiest.fyi/index.json`, which rootiest-icons' `scripts/make-site.py`
  publishes: every icon and emblem, the category colors, each icon's manifest row (to build its emblem on another
  logo), and the generator's logo, canvas and placement presets. The last copy is kept in the app's files, so the
  app opens offline with what it saw last.
- **Published SVGs** are fetched from the same site by path (`color/rootiest-home.svg`, `emblems/home.svg`) the first
  time they are shown, and kept in the HTTP cache (64 MB).
- **Drawn with Math** is the live page `https://icons.rootiest.fyi/svg/`, loaded by the WebView (and kept in its
  cache), so it is always the site's current version.
- **Icons from other sets** come from the Iconify API's data endpoint (`/<set>.json?icons=`), its search and its list
  of sets, trying the mirrors `index.json` names. Brand colors come from Simple Icons' data.
- **Your own files** are read through Android's file picker, a share, or the clipboard.

The app needs no account and sends nothing but those requests.

## Architecture

Two Gradle modules:

| Module | What it is |
|---|---|
| `:core` | The builder: plain Kotlin with no Android in it. Everything that turns icon data into SVG text and file names. |
| `:app` | The Android app: Jetpack Compose, one activity. |

`:core` (`core/src/main/kotlin/dev/rootiest/icons/core/`):

| File | Ports |
|---|---|
| `Format.kt` | Python's and JavaScript's number formatting (`%.3f`, `%g`, JS numbers), so the text is the same |
| `Colors.kt` | `color_spec`, `recolor`, `icon_recolored`, `emblem_recolored` (both for the color and auto variants), `shades`, `oklch`, and auto's `shifted`, `mode_pair`, `auto_shades` (`index.json`'s `colors.hex.autoShift`, 0.07 when it has none) |
| `Model.kt` | `index.json` |
| `Builder.kt` | `placements`, `fit_logo`, `knockout_mask`, `compose`, `built_svg`, `emblem_svg` |
| `Own.kt` | `own_text`, the generator's `clean` and `load_emblem`, `slug`, `placement_tag` |
| `Iconify.kt` | `icon_data`, `icon_svg`, `external_name` |
| `Files.kt` | `stem_of`, `planned`, `layouts_of`, `resolve`, `single_layout_svg`, the ICO and zip writers, and `schemeSvg`/`switches` (an SVG that switches with light or dark mode, fixed to one) |
| `Animate.kt` | `animate_svg` (every `--animate`), `animation_tag` |
| `Morph.kt` | `morph_svg` (`--animate morph`): the emblems' shapes as polygons (each tone apart), paired, aligned and morphed, the rest cross-faded |
| `Frames.kt` | `frame_svg` and `frame_times`: an animated SVG's values at one moment, as a still SVG for a frame; and `playTime`/`playFrames`, how long a published animated file plays and whether it loops, read from its own text |
| `Gif.kt` | `GifEncoder`: an animated GIF as the homepage's `GifWriter` writes one, each frame only the box that changed (lossless), a palette per frame in a table just large enough |
| `Chain.kt` | `chain_svg` and its helpers: animations one after another in one SVG (each step baked onto the chain's clock, scoped, shown in its slot), a chain's steps and frames, a badge frozen at its end, and the end recipe every animated SVG carries |

`:app` (`app/src/main/java/dev/rootiest/icons/`):

| File | What it does |
|---|---|
| `data/Repo.kt` | The network: the catalogue, published SVGs, the Iconify API |
| `data/Settings.kt` | The settings, and your own logo and emblem |
| `render/Svg.kt` | Draws SVG text to a bitmap, and measures what a body draws |
| `render/Output.kt` | The files of a render, an animation's preview and a published animated file's; saving and sharing them |
| `render/Animated.kt` | Animated WebP (frames in a VP8X/ANIM container) and GIF (by `:core`'s `GifEncoder`), from frames taken one at a time |
| `Studio.kt` | Turns a subject (an icon, an emblem, a name from another set, your file) and the screen's choices into the SVG, the file names and the command |
| `IconsViewModel.kt` | The app's state: the catalogue, the search, your files, the stack of screens |
| `ui/` | The screens (`CatalogueScreen` with `AnimatedBadges`, `DetailScreen`, `RenderPanel`, `OwnScreen`, `SettingsScreen`, `LicensesScreen`, `ArticleScreen`), their shared pieces (`Components`, and `Playing`: animations played), and the theme |

A published icon is shown from its published file. It is built here instead, from its emblem and manifest row, as
soon as it goes on your logo or is placed elsewhere, exactly when `render-rootiest-icons` builds it.

## The builder and its fixtures

`:core` must write the same text as rootiest-icons' `scripts/render-icon.py`. Its tests
(`core/src/test/kotlin/.../FixturesTest.kt`) compare it with `core/src/test/resources/fixtures.json`, which
`scripts/make-fixtures.py` writes by running rootiest-icons' own code: color options as typed (pairs among them), recolorings (in the color variant, and in auto: `auto/` icons and emblems alone, in one color, a pair, per part and with a second tone), shades of hex colors, built icons
(both placements, nudges, your own logos, published rows with two tones), emblems alone, files of your own, Iconify
data, file names, layouts, number formatting, every animation and morph, and the animated rasters' frame counts. Long expected outputs are stored as SHA-256 hashes.

When rootiest-icons changes how it builds an icon, port the change to `:core`, then run
`scripts/make-fixtures.py` (it reads a rootiest-icons checkout: `$ROOTIEST_ICONS_DIR`, or
`~/projects/rootiest-icons`) and commit the new fixtures with it.

`AnimatedTest.kt` reads copies of published animated status badges (`core/src/test/resources/animated/`, from
rootiest-icons' `icons/color/animated/`: a badge's change and cycle, and the heartbeat's loop, change and cycle) for
how long each plays, whether it loops, and its frames. Copy them again when those files change how they animate.

## Drawing

[AndroidSVG](https://bigbadaboom.github.io/androidsvg/) draws the SVGs, including the knockout masks and the CSS
classes that color an icon. Before drawing, `rasterSvg()` keeps one layout (the one a browser would show at that
size) and the dark-background shades, as the command line's rasters have. A preview first goes through
`schemeSvg()`, which fixes an SVG that switches with light or dark mode (its `@media (prefers-color-scheme: light)`
block) to the preview's mode: for light, the block's rules are written over the base rules they replace, so the
drawing does not depend on how a renderer orders the cascade. An emblem is measured by drawing it about
1000 px wide and reading the alpha, as the generator measures with rsvg-convert and ImageMagick.

## Files a render writes

Named as `render-rootiest-icons` names them:

```text
<icon>[-emblem]-<variant>[-<colors>][-padded][-layout-<layout>]-<size>px.<png|webp|gif>
<icon>[-emblem]-<variant>[-<colors>][-padded][-layout-<layout>].<ico|svg>
<icon>[-emblem]-<variant>[-<colors>][-padded][-layout-<layout>]-<animation>[-<duration>s].svg
<icon>[-emblem]-<variant>[-<colors>][-padded][-layout-<layout>]-<animation>[-<duration>s]-<size>px.<webp|gif>
```

where `<animation>` is `draw[-loop]`, `heartbeat`, `pulse`, `spin`, `shake`, `pop`, `shine` or
`morph-<target>[-loop]`. With an animation the WebP and GIF are animated, and PNG and ICO stay still.

Save puts them in `Downloads/Rootiest Icons` through MediaStore, which needs no permission. Share writes them to the
app's cache and hands them over through a FileProvider (`<applicationId>.files`).

## Look

Catppuccin, as the other Rootiest apps and icons.rootiest.fyi: Latte when light, Mocha when dark, with the Rootiest
accent (green on Mocha, flamingo on Latte). `ui/theme/Theme.kt` is the palette shared with rootiest-status-android.
Buttons and settings rows use Tabler Icons outlines (`res/drawable/ic_tabler_*.xml`, made by
`scripts/tabler-drawable.py` and credited in `app/aboutlibraries/`). The launcher icon is the `rootiest-icons` icon,
made by `scripts/make-launcher.py`.

## Tests

`./tests/run-tests.sh` runs `:core`'s JVM tests and builds the debug app; it needs no device.

The screens are checked by hand in an emulator before a release: the catalogue in each variant, the animated status badges (each shape, a change tapped, light and dark), an icon's detail and
a render saved to Downloads, the badge placement, a logo and an emblem of your own picked from a file, a search of
the other sets, both themes, and the licenses screen.

## CI

`.github/workflows/ci.yml` runs `./tests/run-tests.sh` on GitHub Actions and keeps the debug APK as the `app-debug`
artifact. It is GitHub-only: no Gitea runner can build Android. It runs on every push the Gitea push mirror copies to
GitHub (`rootiest/rootiest-icons-android`), and on demand. Results appear on GitHub, not on the Gitea pull request.

## Releases

A release is a signed APK on the
[Gitea releases page](https://git.rootiest.dev/rootiest/rootiest-icons-android/releases), tagged `v<version>`, and the
same version on Google Play's internal and closed testing tracks.

1. Bump `appVersion` in `build.gradle.kts` through a pull request: minor for a feature, patch otherwise. The
   `versionCode` follows from it.
2. On a clean `main` that matches `origin/main`: `scripts/release.sh --dry-run`, then `scripts/release.sh`. It runs
   the tests, builds the release APK and app bundle, refuses either if not signed with the release key, creates the
   release with the merged pull requests as its notes, then releases the bundle on Google Play (below).

The release key is the Rootiest apps' own: a properties file (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) named by the Gradle property `rootiest.signing` in `~/.gradle/gradle.properties`. Without it a release
build gets the debug key, which `scripts/release.sh` refuses to publish.

A phone cannot install an older version over a newer one, so a bad release is fixed with a new patch release.

### Google Play

- `scripts/release.sh` leaves `rootiest-icons-<version>.aab` in `build/play/` and, after the Gitea release, releases
  it to the `internal` (internal testing) and `alpha` (closed testing) tracks with `scripts/play-upload.py`, uploaded
  once, in one Play edit. If the upload fails, the Gitea release is already out, and `release.sh` prints the command
  to retry it. `--dry-run` signs in and lists the tracks instead (`play-upload.py --check`).
- A version already on internal testing goes to closed testing on its own, with its notes, with
  `scripts/play-upload.py --package dev.rootiest.icons --promote internal=alpha` (nothing is uploaded).
- Its release notes list only the pull requests people notice in the app: `feat`, `fix` and `perf`, but not those
  scoped to `build`, `ci`, `deps` or `release`. They drop the type and number, spell names such as Iconify and SVG
  properly (the `play_names` list in `release.sh`), and are cut to Play's 500 characters.
- Play signs with the release key itself (Play App Signing set to the existing key): a key Google generates would be
  a different certificate, so a Play update could not replace an installed Obtainium build, or the reverse.
- It signs in with the Rootiest apps' Play service account, whose JSON key is at
  `~/.local/share/android/play-publisher.json` (mode 600, or `$PLAY_KEY`), on the machine that holds the release key.
  The account needs release rights on this app in Play Console's Users and permissions. Without the file,
  `release.sh` leaves the bundle for Play Console, by hand.
- `play-upload.py` needs only Python's standard library and `openssl`, which signs the sign-in. Play cannot create an
  app or take its very first bundle through the API: the app is created in Play Console and the first bundle uploaded
  there by hand. If Play refuses to send the edit for review because the app has other unfinished changes, it commits
  without sending and says so: check Publishing overview.

## Scripts

| Script | What it does |
|---|---|
| `scripts/release.sh` | Builds and publishes a release (above) |
| `scripts/play-upload.py` | Uploads app bundles to Google Play and releases them on testing tracks, or promotes a release from one track to another |
| `scripts/make-fixtures.py` | Writes `:core`'s test fixtures from rootiest-icons' own output |
| `scripts/make-launcher.py` | Writes the launcher icon's drawables from rootiest-icons' `rootiest-icons` icon |
| `scripts/tabler-drawable.py` | Turns Tabler Icons outlines into `ic_tabler_*.xml` drawables |
| `tests/run-tests.sh` | The test suite |
