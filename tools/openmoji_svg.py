"""Put OpenMoji's own drawing of each emoji a note offers beside the app, for stickers on a drawing.

    python tools/openmoji_svg.py openmoji-svg-color.zip

openmoji-svg-color.zip is in an OpenMoji release (github.com/hfg-gmuend/openmoji), the same release
as the font tools/openmoji_font.py was run on. It writes app/src/complete/assets/emojis/, one SVG
for each emoji of app/src/complete/res/raw/emojis.txt, and removes any that is no longer listed.

The font is enough for text, where an emoji is the height of a line. On a drawing a sticker is
dragged to any size, and the font holds each emoji as a picture of a fixed number of pixels: it
blurred as it grew. The drawings are the vectors those pictures were made from.

Only the catalog's emojis, fewer than half of what OpenMoji draws: a drawing takes its stickers from the
Insert panel and nowhere else, so no other emoji can be put on one, and the rest would be megabytes
nobody reaches.

Each file is OpenMoji's, byte for byte, under another name. OpenMoji names a file by its emoji's
code points and leaves the variation selector (FE0F) out of some and in others; the app has only
the emoji to go by, so here every name is the code points without FE0F -- emojiDrawingName in
NoteEmoji.kt is the same rule, and NoteEmojiTest checks that every emoji offered finds its file.
"""
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'app/src/complete/res/raw/emojis.txt')
OUT = os.path.join(ROOT, 'app/src/complete/assets/emojis')
VS16 = 0xFE0F


def catalog():
    emojis = []
    for line in open(CATALOG, encoding='utf-8'):
        if not line.startswith(('#', '@')):
            emojis += line.split()
    return emojis


def name(code_points):
    return '-'.join('%04X' % c for c in code_points)


def main(archive_path):
    archive = zipfile.ZipFile(archive_path)
    drawn = {os.path.basename(n)[:-4].upper(): n for n in archive.namelist() if n.lower().endswith('.svg')}
    wanted, missing = {}, []
    for emoji in catalog():
        code_points = [ord(c) for c in emoji]
        bare = name(c for c in code_points if c != VS16)
        source = drawn.get(name(code_points)) or drawn.get(bare)
        if source is None:
            missing.append(emoji)
        elif wanted.setdefault(bare, source) != source:
            sys.exit(f'{bare}: two emojis of the catalog come to this name')
    if missing:
        sys.exit(f'{len(missing)} emojis of the catalog are not in this OpenMoji: {" ".join(missing[:20])}')

    os.makedirs(OUT, exist_ok=True)
    stale = [f for f in os.listdir(OUT) if f.endswith('.svg') and f[:-4] not in wanted]
    for f in stale:
        os.remove(os.path.join(OUT, f))
    total = 0
    for bare, source in wanted.items():
        data = archive.read(source)
        total += len(data)
        with open(os.path.join(OUT, bare + '.svg'), 'wb') as out:
            out.write(data)
    print(f'{len(wanted)} drawings, {total / 1e6:.1f} MB, in {os.path.relpath(OUT, ROOT)}; {len(stale)} removed')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
