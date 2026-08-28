# Verifying a release

Two independent things can be checked, and they answer different questions.

The **checksum** tells you the APK you downloaded is the APK that was published. It says nothing
about who published it — anyone who can replace the file can replace the checksum beside it.

The **tag signature** tells you the commit a release was built from is the one its author tagged. It
is the part an attacker cannot forge without the private key.

Check both, in that order, and neither on its own.

## The APK you downloaded

Every release since v0.8.24 ships a `SHA256SUMS.txt` next to the APKs. Download it into the same
directory as the APKs and:

```sh
sha256sum -c SHA256SUMS.txt
```

Each line must read `OK`. Files you did not download are reported as missing, which is expected —
`--ignore-missing` silences that.

## The tag it was built from

Import the signing key, which lives in this repository at [`signing-key.asc`](signing-key.asc):

```sh
gpg --import docs/signing-key.asc
```

Then check the fingerprint against the one published here, character by character. This step is the
whole point: a key you did not verify proves only that *someone* signed the tag.

```
210B 5FD0 0E17 66BB 8797  3EDC A286 94BC BC91 9301
```

Identity: `MasterClock signing <MasterClockKey@proton.me>`. The key carries two revoked identities
from the hour it was created; they are expected, and revoked identities are how OpenPGP retires an
address — they are never removed.

Then verify the tag:

```sh
git tag -v v0.8.24
```

`Good signature` with that fingerprint is what you want.

Tags up to and including **v0.8.23 are unsigned**, and will stay that way. Signing them now would
mean deleting and recreating published tags, which would break the link between the tags and the
APKs already built from them. Their absence of a signature is not a warning sign; it predates the
key.

## Building it yourself

The build is reproducible: the same revision produces byte-identical APKs regardless of the
directory it is built in or which JDK 21 is used. Measured across four builds — two directories, two
JDK vendors — the five APKs matched every time.

```sh
./gradlew :app:assembleCompleteRelease :app:assembleStandardRelease \
          :app:assembleLiteRelease :app:assembleMiniRelease \
          :paper:assembleRelease
```

Without signing credentials the output is unsigned, so it will not match the published APK byte for
byte — the signature block differs. Compare the contents rather than the file digest.
