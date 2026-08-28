# Releasing MasterClock

A release touches **nine files across four places**, and nothing warns you when one is missed:
the build still succeeds, the tests still pass, and the wrong thing ships. v0.8.13 was tagged with
`CHANGELOG.md` left untouched, so the GitHub Release published the previous version's notes.

Work through the list. Commit as you go rather than in one lump at the end.

## 1. Version numbers

| File | Change |
|---|---|
| `app/build.gradle.kts` | `versionCode` +1, `versionName = "X.Y.Z"` |
| `paper/build.gradle.kts` | the **same** `versionCode`, `versionName = "X.Y.Z-paper"` |

Both modules share one `versionCode`; it is also the name of the fastlane changelog files below.
The four app flavors add their own `versionNameSuffix` (`-complete`, `-standard`, `-lite`,
`-mini`), so nothing else needs editing for them.

## 2. The changelog, in all seven places

The same notes have to be written seven times, in three different shapes.

- **`CHANGELOG.md`** — a new `## vX.Y.Z — YYYY-MM-DD` section **at the top of the file**.
  This one is load-bearing: `.github/workflows/build-apks.yml` runs
  `awk '/^## /{n++} n==1{print}' CHANGELOG.md` and puts the result in the GitHub Release body.
  It takes the **first** section, so a missing entry silently republishes the previous release's
  notes. Keep the `### Added` / `### Changed` / `### Fixed` headings.
- **`core/src/main/java/com/masterclock/app/logic/AppInfo.kt`** — bump `BUILD_DATE` and prepend a
  `ChangelogEntry`. This is what the in-app changelog dialog shows.
- **`fastlane*/metadata/android/en-US/changelogs/<versionCode>.txt`** — five files, one per
  listing: `fastlane/` (complete), `fastlane-standard/`, `fastlane-lite/`, `fastlane-mini/`,
  `fastlane-paper/`. Plain text, one note per line, no Markdown.

If the release changes stored data in a way users will notice — a renamed serialized enum resets
settings, a schema bump wipes history — say so in the notes rather than letting them find out.
`fallbackToDestructiveMigration(true)` is still set on the Room database.

## 3. Verify before tagging

```sh
./gradlew :app:assembleCompleteRelease :app:assembleStandardRelease \
          :app:assembleLiteRelease :app:assembleMiniRelease \
          :paper:assembleRelease :core:test \
          :app:lintCompleteRelease :paper:lintRelease
```

Expected: `BUILD SUCCESSFUL`, all core tests green, and lint reporting only the known
`GradleDependency` notice (dependency versions are pinned on purpose).

If it stops on `Dependency verification failed`, a dependency moved since
`gradle/verification-metadata.xml` was last written. Regenerate it — the command and the reason it
takes the full task list are in [AGENTS.md](AGENTS.md#when-a-dependency-bump-fails-verification) —
and commit the regenerated file with the bump that caused it, never on its own.

Then install one flavor and read the footer — it shows `versionName` and `BUILD_DATE`, which is the
cheapest way to catch a half-applied version bump.

## 4. Tag and push

```sh
git commit -m "Release vX.Y.Z: ..."
git tag -s vX.Y.Z -m "vX.Y.Z"
git push master HEAD:master
git push master vX.Y.Z
```

The remote is named `master`, not `origin`, and carries two push URLs — one push reaches both
GitHub and GitLab. Never force-push, and never move a tag that has already been pushed: the
workflow builds from the tag, so a moved tag means published APKs that no longer match their
commit.

`-s` signs the tag. `tag.gpgsign` is set for this repository so a bare `git tag` signs too, but
write it out: it is the difference between a tag anyone can forge and one they cannot, and it should
be visible in the command rather than hidden in a config file. Expect a passphrase prompt — a tag
that goes through without one was not signed.

Verify before pushing:

```sh
git tag -v vX.Y.Z
```

Tags up to and including v0.8.23 are unsigned and stay that way. Signing them now would mean
deleting and recreating published tags, which is exactly what the paragraph above forbids.

## 5. What CI does, and what it does not

Pushing a `v*` tag runs `.github/workflows/build-apks.yml`: it builds the five release APKs, signs
them from the repository secrets, uploads them as artifacts, and creates the GitHub Release with
the changelog section from step 2.

It does **not** build AABs, and it does **not** touch GitLab releases. Check the run finishes green,
then open the Release page and confirm the notes are the ones you just wrote.

If the notes are wrong, fix `CHANGELOG.md` and correct the already-published body directly — do not
retag:

```sh
awk '/^## /{n++} n==1{print}' CHANGELOG.md > release_notes.md
gh release edit vX.Y.Z --notes-file release_notes.md
```

## 6. Distribution

Store listings live in the `fastlane*` directories, one per build. `docs/permissions/README.md`
tracks the rights-holder correspondence for the bundled rule documents, and `TRANSLATING.md` is
what contributors follow to add a language.
