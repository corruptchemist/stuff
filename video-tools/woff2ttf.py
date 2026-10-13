"""Convert a fontsource .woff2 to .ttf, fixing family names inherited from variable-font instancing."""
import sys
from fontTools.ttLib import TTFont

src, dst = sys.argv[1:3]
font = TTFont(src)
font.flavor = None
weight = font["OS/2"].usWeightClass
for rec in font["name"].names:
    if rec.nameID in (1, 4, 16) and weight != 100:
        name = rec.toUnicode()
        if " Thin" in name:
            rec.string = name.replace(" Thin", "")
font.save(dst)
