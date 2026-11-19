#!/usr/bin/env python3
"""Does the mechanical half of a release: RELEASING.md steps 1 and 2, minus the writing.

    python tools/bump_version.py 0.8.41 --fdroid replace
    python tools/bump_version.py 0.8.41 --fdroid append --dry-run

It bumps the version in the two Gradle files and the five F-Droid entries, and lays out the seven
places the notes go -- a section in CHANGELOG.md, an entry in AppInfo.kt, five fastlane files --
each holding nothing but a marker. The notes themselves differ from build to build and are still written
by hand; what this removes is the release that looks fine and reports the wrong version somewhere.

--fdroid has no default on purpose. Until the first submission to fdroiddata the entries hold one
build, rewritten in place ("replace"); from the release after it, Builds: is a history and a
published entry must never be rewritten ("append"). Only the maintainer knows which side of that
line a release is on, and guessing wrong is rejected in review.

Nothing is written unless every file matched what was expected of it: a file whose shape has
changed stops the run before the first write, rather than leaving a version bump half applied --
which is the very thing this script exists to prevent.
"""
import argparse
import datetime
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

FASTLANE_DIRS = ["fastlane", "fastlane-standard", "fastlane-lite", "fastlane-mini", "fastlane-paper"]
APP_INFO = "core/src/main/java/com/masterclock/app/logic/AppInfo.kt"
# What stands where notes are still to be written. Put together here so that this file does not
# contain it, and `git grep` for it comes back empty once the notes are in.
MARK = "RELEASE" + "-NOTES-TODO"


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8", newline="") as f:
        return f.read()


def sub_once(text, pattern, replacement, path):
    """A substitution that must happen exactly once: zero or two means the file is not what we think."""
    new, count = re.subn(pattern, replacement, text, flags=re.M)
    if count != 1:
        sys.exit(f"{path}: expected one match of {pattern!r}, found {count}. Nothing was written.")
    return new


def bump_gradle(path, suffix, old, new, code):
    text = read(path)
    text = sub_once(text, rf"^(\s*versionCode = ){code - 1}$", rf"\g<1>{code}", path)
    return sub_once(text, rf'^(\s*versionName = "){re.escape(old)}{suffix}"$', rf'\g<1>{new}{suffix}"', path)


def bump_fdroid(path, old, new, code, mode):
    text = read(path)
    eol = "\r\n" if "\r\n" in text else "\n"
    # The last entry of Builds:, up to the blank line that ends the list. It is the recipe for
    # this build -- subdir, gradle flavor -- so the new entry is that one with the version changed.
    entries = list(re.finditer(r"^  - versionName: .*?(?=^\s*$)", text, flags=re.M | re.S))
    if not entries:
        sys.exit(f"{path}: no Builds: entry found. Nothing was written.")
    last = entries[-1]
    block = last.group(0)
    fresh = sub_once(block, rf"^(  - versionName: ){re.escape(old)}(-\w+)$", rf"\g<1>{new}\g<2>", path)
    fresh = sub_once(fresh, rf"^(    versionCode: ){code - 1}$", rf"\g<1>{code}", path)
    fresh = sub_once(fresh, rf"^(    commit: v){re.escape(old)}$", rf"\g<1>{new}", path)
    if mode == "append":
        text = text[: last.end()] + eol + fresh + text[last.end():]
    else:
        text = text[: last.start()] + fresh + text[last.end():]
    text = sub_once(text, rf"^(CurrentVersion: ){re.escape(old)}(-\w+)$", rf"\g<1>{new}\g<2>", path)
    return sub_once(text, rf"^(CurrentVersionCode: ){code - 1}$", rf"\g<1>{code}", path)


def bump_changelog(old, new, date):
    path = "CHANGELOG.md"
    text = read(path)
    eol = "\r\n" if "\r\n" in text else "\n"
    # CI publishes the first "## " section as the release body, so the new one goes above all.
    if not text.startswith(f"## v{old} "):
        sys.exit(f"{path}: does not start with the v{old} section. Nothing was written.")
    section = ["## v%s — %s" % (new, date), "", MARK + ": one paragraph on what this release is.", "",
               "### Added", "", "- " + MARK, "", "### Changed", "", "- " + MARK, "", "### Fixed", "", "- " + MARK, "", ""]
    return eol.join(section) + text


def bump_app_info(old, new, date):
    text = read(APP_INFO)
    eol = "\r\n" if "\r\n" in text else "\n"
    text = sub_once(text, r'^(\s*const val BUILD_DATE = ")\d{4}-\d{2}-\d{2}"$', rf'\g<1>{date}"', APP_INFO)
    head = rf'^(    val CHANGELOG = listOf\(\r?\n)(        ChangelogEntry\(\r?\n            version = "{re.escape(old)}",)'
    entry = eol.join([
        "        ChangelogEntry(",
        f'            version = "{new}",',
        f'            date = "{date}",',
        "            notes = listOf(",
        f'                Note("{MARK}"),',
        "            ),",
        "        ),",
        "",
    ])
    return sub_once(text, head, lambda m: m.group(1) + entry + m.group(2), APP_INFO)


def main():
    parser = argparse.ArgumentParser(description="Bump MasterClock's version everywhere RELEASING.md lists.")
    parser.add_argument("version", help="the new version, X.Y.Z, without the leading v")
    parser.add_argument("--fdroid", required=True, choices=["replace", "append"],
                        help="replace the single Builds: entry (before the first fdroiddata submission) or append one (after)")
    parser.add_argument("--date", default=datetime.date.today().isoformat(), help="release date, YYYY-MM-DD (default: today)")
    parser.add_argument("--dry-run", action="store_true", help="say what would change and write nothing")
    args = parser.parse_args()

    new = args.version
    if not re.fullmatch(r"\d+\.\d+\.\d+", new):
        sys.exit(f"{new!r} is not X.Y.Z.")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", args.date):
        sys.exit(f"{args.date!r} is not YYYY-MM-DD.")

    gradle = read("app/build.gradle.kts")
    old = re.search(r'^\s*versionName = "(\d+\.\d+\.\d+)"$', gradle, flags=re.M)
    old_code = re.search(r"^\s*versionCode = (\d+)$", gradle, flags=re.M)
    if not old or not old_code:
        sys.exit("app/build.gradle.kts: versionName or versionCode not found. Nothing was written.")
    old, code = old.group(1), int(old_code.group(1)) + 1
    if tuple(map(int, new.split("."))) <= tuple(map(int, old.split("."))):
        sys.exit(f"{new} is not above the current {old}.")

    fdroid = sorted(glob.glob(os.path.join(ROOT, "fdroid", "metadata", "*.yml")))
    if len(fdroid) != 5:
        sys.exit(f"fdroid/metadata: expected 5 entries, found {len(fdroid)}. Nothing was written.")

    # Everything is worked out first and written last: see the module docstring.
    changes = {
        "app/build.gradle.kts": bump_gradle("app/build.gradle.kts", "", old, new, code),
        "paper/build.gradle.kts": bump_gradle("paper/build.gradle.kts", "-paper", old, new, code),
        "CHANGELOG.md": bump_changelog(old, new, args.date),
        APP_INFO: bump_app_info(old, new, args.date),
    }
    for full in fdroid:
        path = os.path.relpath(full, ROOT).replace(os.sep, "/")
        changes[path] = bump_fdroid(path, old, new, code, args.fdroid)
    notes = [f"{d}/metadata/android/en-US/changelogs/{code}.txt" for d in FASTLANE_DIRS]
    for path in notes:
        if os.path.exists(os.path.join(ROOT, path)):
            sys.exit(f"{path} already exists: this versionCode has been started. Nothing was written.")
        changes[path] = MARK + "\n"

    # Every file's directory, before the first is written: a listing with no changelogs/ stopped
    # the script on its notes, the last to be written, with every other file already bumped.
    for path in changes:
        if not os.path.isdir(os.path.dirname(os.path.join(ROOT, path))):
            sys.exit(f"{os.path.dirname(path)}/ does not exist, for {path}. Nothing was written.")

    verb = "Would write" if args.dry_run else "Wrote"
    for path, text in changes.items():
        if not args.dry_run:
            with open(os.path.join(ROOT, path), "w", encoding="utf-8", newline="") as f:
                f.write(text)
        print(f"{verb} {path}")

    print(f"\n{old} -> {new}, versionCode {code - 1} -> {code}, F-Droid entries: {args.fdroid}.")
    print(f"Left to write by hand, each marked {MARK}:")
    print("  CHANGELOG.md, the first section")
    print(f"  {APP_INFO}, the first ChangelogEntry")
    for path in notes:
        print(f"  {path}")
    print(f"Then RELEASING.md from step 3. `git grep -n {MARK}` must come back empty before the tag.")


if __name__ == "__main__":
    main()
