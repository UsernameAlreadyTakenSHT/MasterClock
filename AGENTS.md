# AGENTS.md

Context for AI coding agents working on MasterClock. [README.md](README.md) is the human entry
point; this file holds what an agent needs and a reader does not.

## Layout

Three Gradle modules produce **five** shipped apps:

| module | what it is |
|---|---|
| `core` | Android library. All timer logic, settings, persistence, import/export. No UI. |
| `app` | The phone app, built in four flavors: `complete`, `standard`, `lite`, `mini`. |
| `paper` | A **separate app**, not a flavor: the E-Ink build for Mudita devices. |

`paper` duplicates some UI on purpose — an E-Ink screen needs different components, not a themed
version of the same ones. It depends on `core` like `app` does. A change to shared behaviour usually
belongs in `core` and must be checked in both.

## Build and verify

```sh
./gradlew :app:assembleCompleteRelease :app:assembleStandardRelease \
          :app:assembleLiteRelease :app:assembleMiniRelease \
          :paper:assembleRelease :core:test \
          :app:lintCompleteRelease :paper:lintRelease
```

This is the bar for any non-trivial change, not just releases. Compiling one flavor proves little:
the reduced flavors have their own source set and break independently. Expect `BUILD SUCCESSFUL`,
all `core` tests green, and lint clean apart from the `GradleDependency` notice.

Unit tests live in `core/src/test`. The repository has **no** instrumentation tests, so `core` is
where behaviour gets pinned: put logic there and test it there rather than in a composable.

### When a dependency bump fails verification

`gradle/verification-metadata.xml` pins a SHA-256 for every artifact the build downloads, so a
version bump — including a transitive one that moved on its own — stops the build with
`Dependency verification failed` until the file is regenerated. Locally and on both CIs. That is the
point of it, and it is also its whole maintenance cost.

Regenerate by prefixing the command above:

```sh
./gradlew --write-verification-metadata sha256 \
          :app:assembleCompleteRelease :app:assembleStandardRelease \
          :app:assembleLiteRelease :app:assembleMiniRelease \
          :paper:assembleRelease :core:test \
          :app:lintCompleteRelease :paper:lintRelease
```

Pass the **whole** task list, not just one assemble. Lint and the unit tests resolve configurations
the release builds never touch, and an entry missing from the file fails the build the first time
someone runs the task it belongs to — which is exactly when nobody is expecting it.

Then read the diff before committing. A bump you meant to make shows as a handful of changed
components; anything else changing is the file doing its job.

Check the `<trusted-artifacts>` block at the top survived the regeneration. It trusts `-sources.jar`
and `-javadoc.jar` by pattern, and without it the IDE stops working: Android Studio resolves sources
for code navigation in its own detached configurations, which the build never touches, so
`--write-verification-metadata` never records them and every sync fails on ~74 unverified artifacts.
Trusting them costs nothing — neither kind is ever read by the compiler or reaches the APK.

## How flavors differ

`FlavorConfig` (in `core`) gates features at runtime: `hasMoreTab()`, `hasFullSettingsTabs()`,
`hasPresets()`, `hasArbitre()`, `isEInk()`. Screens check it rather than being conditionally
compiled.

Two source sets matter:

- `app/src/complete/` — screens only Complete can reach (tools, notebook, QR, rules, Bluetooth).
- `app/src/reduced/` — shared by `standard`, `lite` and `mini`, wired in `app/build.gradle.kts`.
  It holds a stub for every Complete-only screen, at the **same signature**, so `MainActivity` can
  register the whole navigation graph unconditionally. A stub navigates straight back.

Keeping the stubs in step is the point: adding a Complete-only screen means adding its stub, or the
three reduced flavors stop compiling. That is deliberate — it fails loudly instead of at runtime.

## Traps that have actually cost time here

- **`kotlin.directories.add("src/reduced/java")`, never `java.srcDir`.** These sources are `.kt`;
  the Java source set does not compile them, and `srcDir` is deprecated in this AGP. Getting this
  wrong yields `Unresolved reference` in exactly the three reduced flavors.
- **`"completeImplementation"(libs.…)` with quotes** in `app/build.gradle.kts`. Kotlin DSL does not
  generate flavor configuration accessors in the same file that declares the flavors.
- **Resource shrinking only drops what nothing references.** The rulebook PDFs leave the reduced
  APKs because no compiled code in those variants mentions them. Adding an unconditional reference
  silently puts megabytes back.
- **Android resource names cannot be Java keywords.** `switch.mp3` is rejected by aapt2; the file is
  `switch_sound.mp3`.
- **Removing code shrinks the install far more than the APK.** AOT artifacts run roughly 4.5× the
  dex size, so dropping a screen is worth several times what the download suggests.

## Language

Code, comments, commit messages, and every user-facing string are **English**. The app ships in
English with `values-XX` files staged for translation — see [TRANSLATING.md](TRANSLATING.md).

Comments explain *why*, not what. The codebase is dense with rationale for decisions that look
arbitrary; match that, and prefer amending a stale comment to leaving it beside changed code.

## Untrusted input

Anything arriving from outside the app is untrusted and already has defences that must not be
weakened:

- Imported settings, logs and QR payloads go through `sanitizeImportedSettings` /
  `sanitizeImportedLog` in `core`. Every import path passes `isImport = true`. Media paths are
  confined to the app sandbox; only `content://` URIs are accepted.
- `ZipBackupManager` checks an entry's name before reading it, bounds each entry and the archive by
  bytes actually decompressed, and never writes a file during extraction.

New import surface needs the same treatment, and `core/src/test` has the tests that pin it.

## Releases and CI

Follow [RELEASING.md](RELEASING.md) — a version bump touches nine files, and missing one produces a
build that looks fine and reports the wrong version. CI publishes the **first** `## ` section of
`CHANGELOG.md` as the release body.

Both GitHub Actions and GitLab CI build and publish, independently, and only on a `v*` tag. A green
run on one forge says nothing about the other.

Two rules in the CI configuration are load-bearing:

- **Workflow actions are pinned to commit SHAs**, with the release in a trailing comment. The job
  holds the signing keystore; a mutable tag can be repointed. Update both the SHA and the comment.
- **`distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` must be bumped with the
  distribution URL.** It is checked at download time only. A stale sum stops the next fresh
  download; deleting the line disables the check silently.
