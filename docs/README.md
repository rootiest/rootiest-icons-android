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
| **Catalogue** | Every icon, status icon and badge, and emblem, as tiles. The search box filters them and, from three letters, looks through the other sets as well (`cloud`, or `tabler:cloud` for one set). Color, Dark and Light switch the variant; the chips filter by category or status state. |
| **Detail** | One icon or emblem: its preview, its details, the variant, the padded twin, and for an emblem whether it is on the logo or alone. With a logo of your own loaded, Rootiest logo or Your logo. Placement (upper right or badge, size, right, down), the five layouts side by side, and the Render panel. |
| **Render panel** | The colors (a category, a status state, a brand's own, or any hex color, for the whole icon or per part), the layouts, any sizes up to 4096 px, PNG, WebP, ICO and SVG, and a background to flatten onto. Render draws every file; Save writes them to `Downloads/Rootiest Icons`, Share hands one file, or a zip of several, to another app. The command that writes the same files with `render-rootiest-icons` is shown and can be copied. |
| **Your own** | A logo and an emblem of your own, each an SVG from a file or the clipboard, with Keep its colors. They are kept in the app's private storage and never leave the phone. An SVG shared to the app, or opened with it, becomes your emblem. |
| **Settings** | Appearance (the phone's, light or dark), the preview background, refreshing the catalogue, and About with the open-source licenses. |

What differs from the command line tool:

- **Sizes** stop at 4096 px (the tool goes to 16384): a larger bitmap does not fit a phone's memory reliably.
- **Rasters** are drawn by AndroidSVG, not librsvg, so their anti-aliased edges differ slightly. The SVG files are the
  same, byte for byte, for published icons; for an icon built here the emblem's measured box can differ by a
  thousandth of its size, as on the homepage.
- **WebP** is lossless on Android 11 and later; Android 10 writes it at the best lossy quality.

## Where its data comes from

- **The catalogue** is `https://icons.rootiest.fyi/index.json`, which rootiest-icons' `scripts/make-site.py`
  publishes: every icon and emblem, the category colors, each icon's manifest row (to build its emblem on another
  logo), and the generator's logo, canvas and placement presets. The last copy is kept in the app's files, so the
  app opens offline with what it saw last.
- **Published SVGs** are fetched from the same site by path (`color/rootiest-home.svg`, `emblems/home.svg`) the first
  time they are shown, and kept in the HTTP cache (64 MB).
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
| `Colors.kt` | `recolor`, `icon_recolored`, `emblem_recolored`, `shades`, `oklch` |
| `Model.kt` | `index.json` |
| `Builder.kt` | `placements`, `fit_logo`, `knockout_mask`, `compose`, `built_svg`, `emblem_svg` |
| `Own.kt` | `own_text`, the generator's `clean` and `load_emblem`, `slug`, `placement_tag` |
| `Iconify.kt` | `icon_data`, `icon_svg`, `external_name` |
| `Files.kt` | `stem_of`, `planned`, `layouts_of`, `resolve`, `single_layout_svg`, and the ICO and zip writers |

`:app` (`app/src/main/java/dev/rootiest/icons/`):

| File | What it does |
|---|---|
| `data/Repo.kt` | The network: the catalogue, published SVGs, the Iconify API |
| `data/Settings.kt` | The settings, and your own logo and emblem |
| `render/Svg.kt` | Draws SVG text to a bitmap, and measures what a body draws |
| `render/Output.kt` | The files of a render; saving and sharing them |
| `Studio.kt` | Turns a subject (an icon, an emblem, a name from another set, your file) and the screen's choices into the SVG, the file names and the command |
| `IconsViewModel.kt` | The app's state: the catalogue, the search, your files, the stack of screens |
| `ui/` | The screens (`CatalogueScreen`, `DetailScreen`, `RenderPanel`, `OwnScreen`, `SettingsScreen`, `LicensesScreen`), their shared pieces (`Components`) and the theme |

A published icon is shown from its published file. It is built here instead, from its emblem and manifest row, as
soon as it goes on your logo or is placed elsewhere, exactly when `render-rootiest-icons` builds it.

## The builder and its fixtures

`:core` must write the same text as rootiest-icons' `scripts/render-icon.py`. Its tests
(`core/src/test/kotlin/.../FixturesTest.kt`) compare it with `core/src/test/resources/fixtures.json`, which
`scripts/make-fixtures.py` writes by running rootiest-icons' own code: recolorings, shades of hex colors, built icons
(both placements, nudges, your own logos, published rows with two tones), emblems alone, files of your own, Iconify
data, file names, layouts and number formatting. Long expected outputs are stored as SHA-256 hashes.

When rootiest-icons changes how it builds an icon, port the change to `:core`, then run
`scripts/make-fixtures.py` (it reads a rootiest-icons checkout: `$ROOTIEST_ICONS_DIR`, or
`~/projects/rootiest-icons`) and commit the new fixtures with it.

## Drawing

[AndroidSVG](https://bigbadaboom.github.io/androidsvg/) draws the SVGs, including the knockout masks and the CSS
classes that color an icon. Before drawing, `rasterSvg()` keeps one layout (the one a browser would show at that
size) and the dark-background shades, as the command line's rasters have. An emblem is measured by drawing it about
1000 px wide and reading the alpha, as the generator measures with rsvg-convert and ImageMagick.

## Files a render writes

Named as `render-rootiest-icons` names them:

```text
<icon>[-emblem]-<variant>[-<colors>][-padded][-layout-<layout>]-<size>px.<png|webp>
<icon>[-emblem]-<variant>[-<colors>][-padded][-layout-<layout>].<ico|svg>
```

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

The screens are checked by hand in an emulator before a release: the catalogue in each variant, an icon's detail and
a render saved to Downloads, the badge placement, a logo and an emblem of your own picked from a file, a search of
the other sets, both themes, and the licenses screen.

## CI

`.github/workflows/ci.yml` runs `./tests/run-tests.sh` on GitHub Actions and keeps the debug APK. It is GitHub-only:
no Gitea runner can build Android. It runs once the repository has a push mirror on GitHub; until then, run the
tests locally before merging.

## Releases

A release is a signed APK on the
[Gitea releases page](https://git.rootiest.dev/rootiest/rootiest-icons-android/releases), tagged `v<version>`.

1. Bump `appVersion` in `build.gradle.kts` through a pull request: minor for a feature, patch otherwise. The
   `versionCode` follows from it.
2. On a clean `main` that matches `origin/main`: `scripts/release.sh --dry-run`, then `scripts/release.sh`. It runs
   the tests, builds the release APK, refuses one not signed with the release key, and creates the release with the
   merged pull requests as its notes.

The release key is the Rootiest apps' own: a properties file (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) named by the Gradle property `rootiest.signing` in `~/.gradle/gradle.properties`. Without it a release
build gets the debug key, which `scripts/release.sh` refuses to publish.

A phone cannot install an older version over a newer one, so a bad release is fixed with a new patch release.

## Scripts

| Script | What it does |
|---|---|
| `scripts/release.sh` | Builds and publishes a release (above) |
| `scripts/make-fixtures.py` | Writes `:core`'s test fixtures from rootiest-icons' own output |
| `scripts/make-launcher.py` | Writes the launcher icon's drawables from rootiest-icons' `rootiest-icons` icon |
| `scripts/tabler-drawable.py` | Turns Tabler Icons outlines into `ic_tabler_*.xml` drawables |
| `tests/run-tests.sh` | The test suite |
