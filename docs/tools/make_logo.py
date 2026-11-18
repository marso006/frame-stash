"""Draws the Frame Stash logo: original pixel art (item frame holding a chest) on a smooth tile,
with a G keycap. Writes docs/images/logo.png (512), docs/images/logo_400.png (CurseForge) and the
128 px in-game mod icon. Run: python docs/tools/make_logo.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "images"
MOD_ICON = ROOT / "common" / "src" / "main" / "resources" / "assets" / "frame_stash" / "icon.png"

# --- pixel art (24 x 24) --------------------------------------------------------------------------
P = {
    "K": (34, 22, 16),      # outline
    "d": (92, 58, 30),      # frame shadow
    "w": (150, 104, 56),    # frame
    "l": (196, 148, 88),    # frame highlight
    "s": (230, 190, 128),   # frame specular
    "B": (70, 30, 22),      # backing shadow
    "b": (110, 48, 34),     # backing
    "r": (136, 62, 44),     # backing light
    "n": (52, 32, 14),      # chest outline / band
    "c": (104, 66, 28),     # chest dark
    "C": (150, 96, 40),     # chest
    "H": (190, 128, 58),    # chest highlight
    "M": (236, 236, 240),   # latch
    "m": (150, 150, 160),   # latch shade
    "g": (255, 196, 64),    # gold
    "G": (255, 232, 150),   # gold highlight
}


def pixel_art():
    n = 24
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()

    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[x, y] = P[c] + (255,)

    # Frame: outline, wood with light from the top-left, leather backing.
    rect(0, 0, 23, 23, "K")
    rect(1, 1, 22, 22, "w")
    rect(1, 1, 22, 1, "l"); rect(1, 1, 1, 22, "l")          # top/left highlight
    rect(1, 22, 22, 22, "d"); rect(22, 1, 22, 22, "d")      # bottom/right shadow
    rect(2, 2, 21, 2, "s"); rect(2, 2, 2, 20, "l")
    rect(21, 3, 21, 21, "d"); rect(3, 21, 21, 21, "d")
    for (x, y) in [(6, 1), (14, 1), (1, 9), (1, 16), (22, 6), (22, 14), (9, 22), (17, 22)]:
        px[x, y] = P["w"] + (255,)                          # wood grain breaks
    rect(4, 4, 19, 19, "K")                                 # inner edge
    rect(5, 5, 18, 18, "b")
    rect(5, 5, 18, 5, "B"); rect(5, 5, 5, 18, "B")          # inset shadow
    for (x, y) in [(8, 8), (15, 7), (11, 16), (16, 15), (7, 14)]:
        px[x, y] = P["r"] + (255,)                          # leather texture

    # Chest, centred in the frame.
    rect(6, 8, 17, 17, "n")
    rect(7, 9, 16, 11, "C")                                 # lid
    rect(7, 9, 16, 9, "H")
    rect(7, 12, 16, 12, "n")                                # seam
    rect(7, 13, 16, 16, "c")                                # body
    rect(7, 13, 16, 13, "C")
    rect(7, 9, 7, 16, "H")                                  # left edge light
    rect(11, 11, 12, 14, "M")                               # latch
    rect(11, 14, 12, 14, "m")

    # Gold sparkles: the "put away" moment.
    for (x, y, c) in [(19, 6, "g"), (20, 6, "G"), (21, 6, "g"), (20, 5, "g"), (20, 7, "g"),
                      (17, 4, "G"), (7, 19, "g"), (6, 19, "G")]:
        if 0 <= x < n and 0 <= y < n:
            px[x, y] = P[c] + (255,)
    return img


def font(size):
    for name in ["bahnschrift.ttf", "segoeuib.ttf", "arialbd.ttf"]:
        try:
            f = ImageFont.truetype(name, size)
            if name == "bahnschrift.ttf":
                f.set_variation_by_name("Bold")
            return f
        except (OSError, ValueError):
            continue
    return ImageFont.load_default()


def logo(size=512):
    s = size / 512
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # Rounded tile with a vertical gradient.
    tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    grad = Image.new("RGBA", (size, size))
    gd = ImageDraw.Draw(grad)
    top, bottom = (58, 46, 92), (22, 18, 36)
    for y in range(size):
        t = y / size
        gd.line((0, y, size, y), fill=tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3)) + (255,))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=int(96 * s), fill=255)
    tile.paste(grad, (0, 0), mask)
    canvas.alpha_composite(tile)

    # Soft gold glow behind the frame.
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((int(96 * s), int(80 * s), int(416 * s), int(400 * s)), fill=(255, 190, 70, 90))
    glow = glow.filter(ImageFilter.GaussianBlur(int(60 * s)))
    canvas.alpha_composite(Image.composite(glow, Image.new("RGBA", glow.size), mask))

    # Pixel art, scaled with hard edges, with a drop shadow.
    art = pixel_art().resize((int(336 * s), int(336 * s)), Image.NEAREST)
    pos = (int(72 * s), int(62 * s))
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 150), (pos[0] + int(10 * s), pos[1] + int(14 * s)), art.split()[3])
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(int(10 * s))))
    canvas.alpha_composite(art, pos)

    # "G" keycap, bottom right.
    kx0, ky0, kx1, ky1 = int(318 * s), int(318 * s), int(470 * s), int(470 * s)
    cap_shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(cap_shadow).rounded_rectangle((kx0 + 6 * s, ky0 + 12 * s, kx1 + 6 * s, ky1 + 12 * s),
                                                 radius=int(30 * s), fill=(0, 0, 0, 160))
    canvas.alpha_composite(cap_shadow.filter(ImageFilter.GaussianBlur(int(10 * s))))
    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle((kx0, ky0 + 10 * s, kx1, ky1 + 10 * s), radius=int(30 * s), fill=(150, 112, 30))  # side
    d.rounded_rectangle((kx0, ky0, kx1, ky1), radius=int(30 * s), fill=(255, 196, 64))                  # top
    d.rounded_rectangle((kx0 + 12 * s, ky0 + 8 * s, kx1 - 12 * s, ky1 - 22 * s), radius=int(22 * s),
                        fill=(255, 214, 110))
    d.text(((kx0 + kx1) / 2, (ky0 + ky1) / 2 - 6 * s), "G", font=font(int(104 * s)), fill=(40, 26, 10),
           anchor="mm")
    return canvas


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    big = logo(1024).resize((512, 512), Image.LANCZOS)   # supersampled for smooth edges
    big.save(OUT / "logo.png")
    big.resize((400, 400), Image.LANCZOS).save(OUT / "logo_400.png")
    MOD_ICON.parent.mkdir(parents=True, exist_ok=True)
    big.resize((128, 128), Image.LANCZOS).save(MOD_ICON)
    pixel_art().resize((240, 240), Image.NEAREST).save(OUT / "logo_pixel.png")
    print("wrote", OUT / "logo.png", OUT / "logo_400.png", MOD_ICON)
