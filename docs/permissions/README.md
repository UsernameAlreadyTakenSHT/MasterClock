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

| Document | Rights holder | Contact | Sent | Reply |
|---|---|---|---|---|
| Abalone | Abalone S.A. / FoxMind | `info@FoxMind.com` (printed in the PDF) | — | — |
| Quoridor | Gigamic | contact form, gigamic.com | — | — |
| Nine Men's Morris | Kanare Kato (Kanare_Abstract) | kanare-abstract.com | — | — |
| Tafl | World Tafl Federation (Aage Nielsen) | aagenielsen.dk | — | — |
| Hex | David Beckwith | *to find* | — | — |
| Chess | FIDE | fide.com | — | — |
| Draughts (international) | FMJD | fmjd.org | — | — |
| Draughts (64) | IDF | idf64.org | — | — |
| Shogi | FESA | fesashogi.eu | — | — |
| Santorini | Dr. Gordon Hamilton | `gamesbygord@gmail.com` | — | **Already granted** — the document states it "may be reproduced for non-commercial purposes". A courtesy note only. |

Fill in *Sent* and *Reply* as you go, and keep any written permission (email text, date) alongside
this file.

## Template — English

> Subject: Permission to include your rules document in a free chess clock app
>
> Hello,
>
> I develop MasterClock, a free and open-source clock for chess, draughts, shogi and other
> abstract games. It contains no advertising, no tracking and no in-app purchases, and its source
> code is public under the MIT license.
>
> The app has a reference section where players can read the rules of a game away from the board.
> I would like to include **[DOCUMENT]** there, unmodified, so that it can be read without an
> internet connection. It is credited to you inside the app and in the project's README, with a
> link to your website.
>
> May I have your permission to distribute it this way? If you would rather I link to your site
> instead of including the file, or not reference it at all, tell me and I will change it in the
> next release.
>
> Thank you for your time,
> [NAME] — [PROJECT URL]

## Template — French (Gigamic, Abalone S.A.)

> Objet : Autorisation d'inclure votre règle du jeu dans une application gratuite
>
> Bonjour,
>
> Je développe MasterClock, une pendule de jeu gratuite et open source pour les échecs, les dames,
> le shogi et d'autres jeux abstraits. L'application ne contient aucune publicité, aucun traceur et
> aucun achat intégré ; son code source est public sous licence MIT.
>
> Elle propose une rubrique de référence permettant de consulter les règles d'un jeu loin du
> plateau. Je souhaiterais y inclure **[DOCUMENT]**, sans modification, afin qu'elle soit
> consultable hors connexion. Le document vous est crédité dans l'application et dans le README du
> projet, avec un lien vers votre site.
>
> M'autorisez-vous à le diffuser ainsi ? Si vous préférez que je renvoie vers votre site plutôt que
> d'inclure le fichier, ou que je n'y fasse pas référence, dites-le moi et je le modifierai dès la
> prochaine version.
>
> Avec mes remerciements,
> [NOM] — [URL DU PROJET]

## Per-holder notes

- **Abalone S.A. / FoxMind** — the document is marked "All rights reserved" and Abalone is a
  registered trademark (patent DM/012362), so this is the request most worth making. Write in
  French; `info@FoxMind.com` is printed in the rulebook itself.
- **Gigamic** — "© & ® 1997 Gigamic, from a concept of Mirko Marchesi". French company, write in
  French.
- **Kanare Kato** — only the rulebook and the "Stacking Morris" variant are his; Nine Men's Morris
  itself is public domain. Worth saying so in the message, since the request is narrower than it
  looks. Contact via kanare-abstract.com.
- **World Tafl Federation** — a hobbyist federation; the document carries no notice at all, so ask
  rather than assume.
- **David Beckwith** — the document is signed "David Beckwith, June 2021" but names no site.
  Contact still to be found.
- **FIDE, FMJD, IDF, FESA** — these are rule sets published for the world to use, and distributing
  them is normal practice. A short note is still worth sending: cheap, and it puts the four biggest
  documents beyond question.
- **Dr. Gordon Hamilton** — already grants non-commercial reproduction in writing, and MasterClock
  is free with no ads. No permission needed; a thank-you note is the appropriate message.
