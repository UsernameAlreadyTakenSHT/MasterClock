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

Both modules set `lint { checkDependencies = true }`, which is what makes these two tasks read
`core` as well as their own sources. Without it AGP defaults `checkDependencies` to `false`, and the
two tasks named above analysed exactly zero of the files in `core` — where the logic, the
persistence and the whole import/export chain live. That is how a call to an API 37 class shipped
inside a `minSdk 24` module and reached a release: the check that exists for precisely that was
pointed somewhere else. There is no separate `:core:lint*` task in the bar because these two now
cover it; do not remove the `lint` blocks without adding one.

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

**An AGP bump needs one entry added by hand.** `aapt2` publishes a separate jar per host OS and
Gradle resolves only the one it runs on, so regenerating on Windows records `-windows.jar` and
leaves the CI — which is Linux — with an artifact it cannot verify. The build then dies in about
40 seconds on `Dependency verification failed ... aapt2-<version>-linux.jar`. The version tracks
AGP, so this recurs on every bump. Fetch the digests and add them beside the generated one:

```sh
v=<aapt2 version, from the failure or the generated windows entry>
for os in linux osx; do
  curl -sLO "https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/$v/aapt2-$v-$os.jar"
  sha256sum "aapt2-$v-$os.jar"
done
```

**Every Maven Central component carries its `.pom`, and regeneration does not put them there.** A
fresh Linux CI runner reads POMs that no regeneration on Windows records. AGP 9.4.0's bump stopped
CI on `Dependency verification failed ... groovy-4.0.29.pom`, and the same thing happens against an
empty `GRADLE_USER_HOME`, which was tried: Windows records `groovy-bom`'s POM either way and never
`groovy`'s. Why the runner differs is not established. So the file holds a POM for every
non-Google component, read from `repo1.maven.org`. An entry Gradle never reads costs nothing; a
missing one fails a release, on a CI nobody watches while it happens.

After any regeneration, fetch the POM of every component outside Google's groups that lacks one:

```sh
awk '/<component group=/{match($0,/group="[^"]*"/);g=substr($0,RSTART+7,RLENGTH-8)
       match($0,/name="[^"]*"/);n=substr($0,RSTART+6,RLENGTH-7)
       match($0,/version="[^"]*"/);v=substr($0,RSTART+9,RLENGTH-10);p=0;next}
     /<artifact name="[^"]*\.pom"/{p=1} /<\/component>/{if(!p)print g,n,v}' \
    gradle/verification-metadata.xml |
while read -r g n v; do
  case "$g" in androidx.*|com.android.*|com.google.android.*|com.google.testing.*) continue;; esac
  curl -sf "https://repo1.maven.org/maven2/${g//.//}/$n/$v/$n-$v.pom" | sha256sum | sed "s|-\$|$g $n $v|"
done
```

and add each as `<artifact name="<n>-<v>.pom">` with `origin="repo1.maven.org"`, last in its
component. A component Central does not serve (`foojay-resolver` comes from the plugin portal) has
never needed one. Google-hosted ones (`androidx.*`, `com.android.*`) record `.jar` plus `.module`
and no POM, and never have needed one either.

**To prune it**, which nothing else does — regeneration only ever adds, so every bump leaves the old
versions behind — empty `<components>` and regenerate with `--rerun-tasks`, so that every task
resolves what it resolves on a runner with no build directory. Then the two hand additions: the
aapt2 jars above, and the POMs. Validate on an empty `GRADLE_USER_HOME` before committing; a
`--` inside one of the file's XML comments makes it unreadable, and that is how it shows.

Check the `<trusted-artifacts>` block at the top survived the regeneration. It trusts `-sources.jar`
and `-javadoc.jar` by pattern, and without it the IDE stops working: Android Studio resolves sources
for code navigation in its own detached configurations, which the build never touches, so
`--write-verification-metadata` never records them and every sync fails on ~74 unverified artifacts.
Trusting them costs nothing — neither kind is ever read by the compiler or reaches the APK.

The third rule there trusts `gradle-<version>-src.zip`, Gradle's own source distribution, for the
same reason. Android Studio fetches it the first time it sees a Gradle version, so the first sync
after every Gradle upgrade failed on it — the move to 9.8.1 is how it surfaced. It is a pattern, not
a digest, precisely so that the next upgrade does not break the sync again.

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
  bytes actually decompressed, and never writes a file during extraction. Notebook media is
  restored by a second pass, `restoreMedia`, only once extraction has accepted the whole archive:
  an entry's name only selects a destination that `sanitizeImportedMediaPath` derived from an
  imported note, files are staged and moved into place at the end, and nothing is overwritten.

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
