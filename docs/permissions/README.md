# Permission requests — bundled rules documents

MasterClock bundles ten rulebooks in `core/src/main/res/raw/` so the "Some rules" screen works
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
| Abalone | Abalone S.A. / FoxMind | 2026-08-02 | — |
| Quoridor | Gigamic | 2026-08-02 | — |
| Nine Men's Morris | Kanare Kato (Kanare_Abstract) | 2026-08-02 | — |
| Tafl | World Tafl Federation (Aage Nielsen) | 2026-08-02 | **Granted** 2026-08-04 — see the conditions below. |
| Hex | David Beckwith | 2026-08-02 | — |
| Chess | FIDE | 2026-08-02 | — |
| Draughts (international) | FMJD | 2026-08-02 | — |
| Draughts (64) | IDF | 2026-08-02 | — |
| Shogi | FESA | 2026-08-02 | — |
| Santorini | Dr. Gordon Hamilton | 2026-08-02 | **Already granted** — the document states it "may be reproduced for non-commercial purposes". A courtesy note only. |

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
