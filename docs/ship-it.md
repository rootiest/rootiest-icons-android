# Shipping this project

Instructions for `/ship-it` and its steps. Each `##` section is read by the step of the same
name; anything left out uses that step's defaults.

## ship-it
- Follow the PR lifecycle in `CONTRIBUTING.md`. `ship-it` does not label: add the `Kind/*` and
  `Area/*` labels as soon as `push-it` opens the PR, and `Reviewed/Fixed` to each closed issue
  after `merge-it` merges.
- A change reaches phones in two PRs: the change itself, then a version bump. `deploy-it` opens
  the bump and publishes the release.

## doc-it
- `docs/README.md` is the documentation root, with `README.md` for installing and building.
- When a change follows one in rootiest-icons (how an icon is built), `scripts/make-fixtures.py`
  must have been run again: the fixtures are generated, never edited by hand.

## push-it
- Branch `<type>/<topic>`, with the commit type: `feat/`, `fix/`, `docs/`, `chore/`. A version
  bump is `chore/release-<version>`.
- Commit scopes in use: the screen or part (`catalogue`, `detail`, `render`, `own`, `settings`,
  `core`, `release`).
- Before pushing, run `./tests/run-tests.sh`. It needs JDK 21 or newer and the Android SDK. The
  pre-push hook (`.githooks/pre-push`) refuses a push to `main` and, when commit signing is on,
  unsigned commits.
- A change to a screen is also tried in an emulator (`docs/README.md`, Tests).
- PR body: the template's `## Summary` (ending with `Closes #N` when it resolves an issue) and
  its `## Verification` checkbox list, checked only for what was actually verified.
- Labels at creation: exactly one `Kind/*` and at least one `Area/*` for each area touched:
  `Area/App`, `Area/Build`, `Area/CI`, `Area/Docs`, `Area/Tests`.

## verify-it
- CI does not run on Gitea and reports nothing on the Gitea PR. It runs on the GitHub mirror
  (`rootiest/rootiest-icons-android`) for every branch the push mirror copies there, usually
  within a minute of the push. Find the run by commit:
  `gh run list -R rootiest/rootiest-icons-android --commit <sha>`, then `gh run watch <id>`.
- One workflow, `CI`: the tests, then the debug APK.

## merge-it
- Merge method: merge commit.
- The maintainer has pre-approved merging in this repository: merge without asking once CI
  passed on the head commit. Stop and ask when a PR changes the release signing, the
  application id or the permissions in the manifest.
- After the merge, label each issue the PR closed `Reviewed/Fixed`.

## sync-it
- No automated commits follow a merge.

## deploy-it
Deploying means publishing a signed release on Gitea, which phones pick up through Obtainium, and
the same version on Google Play's internal and closed testing tracks (`docs/README.md`, Releases). The
maintainer has pre-approved releasing from this repository.

**Nothing to deploy** when nothing in `app/`, `core/` or the Gradle build changed since the
last release tag.

1. **Version bump**, when the merged PR was not one: bump `appVersion` in `build.gradle.kts`
   from the latest `v*` tag: minor for any `feat`, otherwise patch. Ship the bump as its own PR
   through `push-it`, `verify-it`, `merge-it` and `sync-it`: branch `chore/release-<version>`,
   commit and title `chore: version <version>`, labels `Kind/Chore` and `Area/Build`. The first
   release, 0.1.0, needs no bump.
2. **Dry run**, on a clean `main` that matches `origin/main`: `scripts/release.sh --dry-run`. When the
   usual checkout is busy with other work, use a fresh clone (with a copy of `local.properties`)
   rather than moving its `main`.
3. **Release:** `scripts/release.sh`. It runs the tests, builds the APK, refuses one not signed
   with the release key (which only the maintainer's machine holds, through `rootiest.signing`
   in `~/.gradle/gradle.properties`), creates the Gitea release `v<version>`, then releases the
   bundle on Google Play's internal and closed testing tracks (`internal` and `alpha`). If only
   the Play upload fails, run the retry command it prints.
4. **Verify:** the release page it prints lists `rootiest-icons-<version>.apk`, and
   `scripts/play-upload.py --package dev.rootiest.icons --check` lists the new version on both
   `internal` and `alpha`. A version that reached only `internal` goes to closed testing with
   `scripts/play-upload.py --package dev.rootiest.icons --promote internal=alpha`.

**Rollback:** none. A phone cannot install an older version over a newer one, so fix a bad
release with a new patch release. Never delete a published release unless the user asks.
