# Permission requests — bundled rules documents

MasterClock bundles six rulebooks in `core/src/main/res/raw/` so the "Some rules" screen works
offline. Every one is credited in-app (version footer → Credits → Rules documents) and in the
README.

Game *rules* are not copyrightable — only the particular document expressing them is. So each of
these PDFs belongs to its author or publisher, and bundling it is a redistribution question
regardless of how accurately we credit it. This directory tracks asking each rights holder for
explicit permission, so distribution rests on a grant rather than on tolerance. It also settles the
question F-Droid asks about non-free bundled assets.

Nothing blocks on the replies: the documents ship today, credited, and any holder who says no gets
their document removed in the next release.

## Status

| Document | Rights holder | Sent | Reply |
|---|---|---|---|
| Nine Men's Morris | Kanare Kato (Kanare_Abstract) | 2026-08-02, again 2026-11-16 | — |
| Tafl | World Tafl Federation (Aage Nielsen) | 2026-08-02 | **Granted** 2026-08-04 — see the conditions below. |
| Chess | FIDE | 2026-08-02, again 2026-11-16 | — |
| Draughts (international) | FMJD | 2026-08-02, again 2026-11-16 | — |
| Draughts (64) | IDF | 2026-08-02, again 2026-11-16 | — |
| Shogi | FESA | 2026-08-02, again 2026-11-16 | — |

The five without a reply were asked a second time on 2026-11-16, ahead of the F-Droid submission.

## Removed: Quoridor, Abalone, Hex, Santorini

These four rulebooks were bundled until 2026-11-16 and have been taken out of the app, its credits
and the README. Each is published under its rights holder's own licence -- Gigamic for Quoridor,
Abalone S.A. / FoxMind for Abalone, David Beckwith for Hex, Dr. Gordon Hamilton for Santorini --
and the requests for permission to bundle them, sent on 2026-08-02, had no reply. Santorini's
document allows reproduction for non-commercial purposes only, which is not a licence a store such
as F-Droid can pass on.

A grant from one of these holders would be the reason to bring its document back; nothing else is.

## Tafl — the terms we are held to

Aage Nielsen granted permission on 2026-08-04. His preference is that the app link to the rules on
the federation's website rather than bundle a copy, so that readers always get the current version.
Bundling is allowed as a second choice, and only on conditions:

- the copy must be **unmodified**,
- **proper credit** must be given,
- a **link to aagenielsen.dk** must appear **both in the app and in this project's README**.

We bundle, because reading the rules offline is the point of the "Some rules" screen. That keeps us
inside the grant rather than the preference, so the three conditions are binding on us and outlive
whoever remembers this email. Anything that touches the Tafl document, the credits screen or the
README's rules section has to keep all three true.

If the federation ever revises the document, the bundled copy silently becomes stale — the reason
they would rather we linked. Worth re-downloading it when the rules screen is next revisited.
