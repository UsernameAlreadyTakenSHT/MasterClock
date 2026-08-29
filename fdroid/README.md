# F-Droid metadata

The five files in `metadata/` are the app entries for a submission to
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). They are kept here so they can be reviewed and
edited alongside the code they describe; F-Droid itself never reads this directory. To submit, copy
them into a fdroiddata checkout under its own `metadata/`, keeping the file names — F-Droid keys
every app on its application ID, and each flavor has a distinct one.

One file per flavor, because F-Droid indexes them as five separate apps, each with its own listing,
its own version history and its own users.

## Why the descriptions are inlined

F-Droid discovers Fastlane metadata only at `fastlane/metadata/android/` inside the build's
`subdir`. This repository keeps one directory per flavor at the root — `fastlane-lite/`,
`fastlane-mini/` and so on — which is not a layout F-Droid knows how to find. The text therefore
lives in `Summary` and `Description` here, and the two copies have to be kept in step by hand.

## Two things that are easy to get wrong

**`versionNameSuffix` is invisible to fdroidserver.** It parses `applicationIdSuffix`
(`common.py:2113`) but has no equivalent for the version name, so `fdroid checkupdates` reads
`0.8.24` from the Gradle files while the APK is actually stamped `0.8.24-complete`. `fdroid build`
compares the two and fails the build on a mismatch (`build.py:834`). That is why every entry pins
the suffixed name and sets `AutoUpdateMode: None` — an automatic update would propose the
unsuffixed one and break the next build. Each release needs its `Builds` entry written by hand.

**Categories are not the ones you would guess.** There is no `Games` and no `Time`; the list in
fdroiddata's `config/categories.yml` is far more granular. A clock for board games is a
`Game Helper` and a `Timer`.

## Linting these locally

`fdroid lint` validates categories and anti-features against fdroiddata's own configuration, so it
needs a copy of it. Two files are enough, minus their `icon:` lines, which point at PNGs that are
not fetched here:

```sh
mkdir -p config
for f in categories antiFeatures; do
  curl -s "https://gitlab.com/fdroid/fdroiddata/-/raw/master/config/$f.yml" \
    | sed '/^  icon: /d' > "config/$f.yml"
done
fdroid lint && fdroid rewritemeta
```

`rewritemeta` must leave the files unchanged; fdroiddata's CI rejects anything that is not in its
canonical form. Note that it cannot write over a `/mnt/c` path from WSL — copy the directory into
the Linux filesystem first.
