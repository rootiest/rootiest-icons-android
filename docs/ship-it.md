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
- CI does not run on Gitea and reports nothing on the Gitea PR. `.github/workflows/ci.yml` runs
  on a GitHub mirror once the repository has one; until then there is no CI, and the local
  `./tests/run-tests.sh` run from `push-it` is the check.

## merge-it
- Merge method: merge commit.
- The maintainer has pre-approved merging in this repository: merge without asking once the
  tests passed on the head commit. Stop and ask when a PR changes the release signing, the
  application id or the permissions in the manifest.
- After the merge, label each issue the PR closed `Reviewed/Fixed`.

## sync-it
- No automated commits follow a merge.

## deploy-it
Deploying means publishing a signed release on Gitea, which phones pick up through Obtainium
(`docs/README.md`, Releases). The maintainer has pre-approved releasing from this repository.

**Nothing to deploy** when nothing in `app/`, `core/` or the Gradle build changed since the
last release tag.

1. **Version bump**, when the merged PR was not one: bump `appVersion` in `build.gradle.kts`
   from the latest `v*` tag: minor for any `feat`, otherwise patch. Ship the bump as its own PR
   through `push-it`, `verify-it`, `merge-it` and `sync-it`: branch `chore/release-<version>`,
   commit and title `chore: version <version>`, labels `Kind/Chore` and `Area/Build`. The first
   release, 0.1.0, needs no bump.
2. **Dry run**, on a clean `main` that matches `origin/main`: `scripts/release.sh --dry-run`.
3. **Release:** `scripts/release.sh`. It runs the tests, builds the APK, refuses one not signed
   with the release key (which only the maintainer's machine holds, through `rootiest.signing`
   in `~/.gradle/gradle.properties`), and creates the Gitea release `v<version>`.
4. **Verify:** the release page it prints lists `rootiest-icons-<version>.apk`.

Google Play is not used.

**Rollback:** none. A phone cannot install an older version over a newer one, so fix a bad
release with a new patch release. Never delete a published release unless the user asks.
