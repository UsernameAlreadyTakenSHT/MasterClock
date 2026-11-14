"""Build the emoji font and catalog Complete's text notes use, from OpenMoji and Unicode's list.

    pip install fonttools uharfbuzz
    python tools/openmoji_font.py OpenMoji-color-cbdt.ttf emoji-test.txt

OpenMoji-color-cbdt.ttf is in openmoji-font.zip of an OpenMoji release (github.com/hfg-gmuend/openmoji);
emoji-test.txt is Unicode's, https://www.unicode.org/Public/<version>/emoji/emoji-test.txt. It writes
app/src/complete/res/font/openmoji.ttf and app/src/complete/res/raw/emojis.txt.

CBDT, bitmaps, because every Android since 7 draws it. Three things are done to OpenMoji's font:

1. It is cut down to Unicode's emojis, skin tones included. OpenMoji's own extras sit in the private
   use area, where no keyboard reaches them.
2. It declares, in a cmap format 14 subtable, every "X + FE0F" it draws. Without it Android took the
   phone's emoji font for those: a hand drawn in another style, a rainbow flag in two pieces.
3. Each ligature with FE0F in it gets a twin without: once the variation is declared, the shaper takes
   X + FE0F as X's glyph and FE0F is gone before ligatures are looked for.

Then it checks, with HarfBuzz, that every emoji is drawn as one glyph, and fails if not.
"""
import os
import sys

import uharfbuzz as hb
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables import otTables
from fontTools.ttLib.tables._c_m_a_p import CmapSubtable

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT_OUT = os.path.join(ROOT, 'app/src/complete/res/font/openmoji.ttf')
CATALOG_OUT = os.path.join(ROOT, 'app/src/complete/res/raw/emojis.txt')
VS16 = 0xFE0F
SKIN_TONES = range(0x1F3FB, 0x1F400)


def read_emoji_test(path):
    """(group, emoji, code points) for each fully-qualified emoji, and every sequence's code points."""
    emojis, sequences, group = [], [], None
    for line in open(path, encoding='utf-8'):
        if line.startswith('# group:'):
            group = line.split(':', 1)[1].strip()
        if not line.strip() or line.startswith('#'):
            continue
        field, rest = line.split(';', 1)
        cps = [int(c, 16) for c in field.split()]
        sequences.append(cps)
        if rest.strip().startswith('fully-qualified'):
            emojis.append((group, ''.join(map(chr, cps)), cps))
    return emojis, sequences


def declare_variations(font, sequences):
    cmap = font.getBestCmap()
    bases = sorted({a for cps in sequences for a, b in zip(cps, cps[1:]) if b == VS16 and a in cmap})
    table = font['cmap']
    table.tables = [t for t in table.tables if t.format != 14]
    uvs = CmapSubtable.newSubtable(14)
    uvs.platformID, uvs.platEncID, uvs.language = 0, 5, 0xFF
    uvs.cmap = {}
    # None: the base character's own glyph, as Noto Color Emoji declares it.
    uvs.uvsDict = {VS16: [(cp, None) for cp in bases]}
    table.tables.append(uvs)

    vs = cmap[VS16]
    for lookup in font['GSUB'].table.LookupList.Lookup:
        for sub in lookup.SubTable:
            if lookup.LookupType == 7:
                sub = sub.ExtSubTable
            if not isinstance(sub, otTables.LigatureSubst):
                continue
            for ligs in sub.ligatures.values():
                have = {tuple(l.Component) for l in ligs}
                for lig in list(ligs):
                    twin = tuple(c for c in lig.Component if c != vs)
                    if vs in lig.Component and twin not in have:
                        n = otTables.Ligature()
                        n.Component, n.CompCount, n.LigGlyph = list(twin), len(twin) + 1, lig.LigGlyph
                        ligs.append(n)
                        have.add(twin)
                # Longest first, so that a shorter sequence never takes a longer one's start.
                ligs.sort(key=lambda l: -len(l.Component))


def drawn_whole(font_path):
    hbfont = hb.Font(hb.Face(hb.Blob.from_file_path(font_path)))
    vs_glyph = TTFont(font_path).getBestCmap().get(VS16)

    def whole(text):
        buf = hb.Buffer()
        buf.add_str(text)
        buf.guess_segment_properties()
        hb.shape(hbfont, buf, {})
        visible = [g.codepoint for g in buf.glyph_infos if g.codepoint != vs_glyph]
        return len(visible) == 1 and visible[0] != 0
    return whole


def main(source, emoji_test):
    emojis, sequences = read_emoji_test(emoji_test)
    whole_in_source = drawn_whole(source)
    kept = [e for e in emojis if whole_in_source(e[1]) or whole_in_source(e[1].replace(chr(VS16), ''))]

    options = subset.Options()
    options.layout_features = ['*']
    options.notdef_outline = True
    font = TTFont(source)
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=''.join(e[1] for e in kept))
    subsetter.subset(font)
    declare_variations(font, sequences)
    font.save(FONT_OUT)

    whole = drawn_whole(FONT_OUT)
    broken = [e[1] for e in kept if not whole(e[1])]
    if broken:
        sys.exit(f'{len(broken)} emojis drawn in pieces: {" ".join(broken[:40])}')

    groups = {}
    for group, text, cps in kept:
        if group != 'Component' and not any(cp in SKIN_TONES for cp in cps):
            groups.setdefault(group, []).append(text)
    with open(CATALOG_OUT, 'w', encoding='utf-8', newline='\n') as out:
        out.write('# Emojis offered in a note, by group: OpenMoji draws each as one glyph.\n')
        out.write('# Generated by tools/openmoji_font.py; skin tones are left to the keyboard.\n')
        for group, items in groups.items():
            out.write(f'@{group}\n{" ".join(items)}\n')
    print(f'{len(kept)} emojis kept, {sum(map(len, groups.values()))} offered; font {os.path.getsize(FONT_OUT)} bytes')
    if len(kept) < len(emojis):
        print(f'{len(emojis) - len(kept)} emojis of this Unicode version are not in this OpenMoji')


if __name__ == '__main__':
    main(*sys.argv[1:3])
