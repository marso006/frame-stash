"""Turns the raw screenshots in docs/raw into the edited images in docs/images.

Run from the project root after `./gradlew :fabric:runClientGameTest`:
    python docs/tools/make_images.py
Needs Pillow. Uses Windows fonts (Bahnschrift / Segoe UI); falls back to Pillow's default font.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "docs" / "raw"
OUT = ROOT / "docs" / "images"
OUT.mkdir(parents=True, exist_ok=True)

W, H = 1600, 900
GOLD = (255, 190, 70)
WHITE = (255, 255, 255)
SOFT = (225, 225, 230)
DARK = (18, 16, 22)

# Panel positions in the 1920x1080 raw shots (GUI scale 3, vanilla 176px-wide screens).
INVENTORY = (696, 291, 696 + 528, 291 + 498)
CHEST_3 = (696, 291, 696 + 528, 291 + 498)
CHEST_6 = (696, 207, 696 + 528, 207 + 666)


def font(size, bold=True):
    names = (["bahnschrift.ttf", "segoeuib.ttf", "arialbd.ttf"] if bold
             else ["segoeui.ttf", "arial.ttf"])
    for name in names:
        try:
            f = ImageFont.truetype(name, size)
            if bold and name == "bahnschrift.ttf":
                f.set_variation_by_name("Bold")
            return f
        except (OSError, ValueError):
            continue
    return ImageFont.load_default()


def raw(name):
    return Image.open(RAW / f"{name}.png").convert("RGB")


def fit(img, size=(W, H)):
    """Scale and centre-crop to fill size."""
    iw, ih = img.size
    scale = max(size[0] / iw, size[1] / ih)
    img = img.resize((round(iw * scale), round(ih * scale)), Image.LANCZOS)
    x = (img.width - size[0]) // 2
    y = (img.height - size[1]) // 2
    return img.crop((x, y, x + size[0], y + size[1]))


def backdrop(name):
    """A blurred, darkened version of a shot to sit panels on."""
    img = fit(raw(name)).filter(ImageFilter.GaussianBlur(10))
    return Image.blend(img, Image.new("RGB", img.size, DARK), 0.55)


def panel(name, box, scale):
    crop = raw(name).crop(box)
    return crop.resize((round(crop.width * scale), round(crop.height * scale)), Image.NEAREST)


def chest_rows(img, rows, scale):
    """Keeps the title and the container rows of a chest screen, cut cleanly under the last row."""
    h = round((18 + rows * 18 + 2) * 3 * scale)
    out = img.crop((0, 0, img.width, h))
    # Redraw the panel's bottom edge so the cut looks intentional.
    d = ImageDraw.Draw(out)
    edge = round(3 * scale)
    d.rectangle((0, h - edge * 2, out.width, h), fill=(198, 198, 198))
    d.rectangle((0, h - edge, out.width, h), fill=(85, 85, 85))
    return out


def shadowed_paste(canvas, img, pos, radius=18):
    shadow = Image.new("RGBA", (img.width + radius * 4, img.height + radius * 4), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rectangle((radius * 2, radius * 2, radius * 2 + img.width, radius * 2 + img.height),
                                     fill=(0, 0, 0, 170))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius))
    canvas.paste(shadow, (pos[0] - radius * 2 + 6, pos[1] - radius * 2 + 10), shadow)
    canvas.paste(img, pos)


def title_bar(canvas, title, subtitle=None, step=None):
    """Gradient band at the top with a gold accent, a title and an optional subtitle."""
    band_h = 150 if subtitle else 112
    band = Image.new("RGBA", (canvas.width, band_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(band)
    for y in range(band_h):
        a = int(215 * (1 - (y / band_h) ** 2.2))
        d.line((0, y, canvas.width, y), fill=(*DARK, a))
    canvas.paste(band, (0, 0), band)
    d = ImageDraw.Draw(canvas)
    x = 56
    if step is not None:
        d.ellipse((x, 30, x + 56, 86), fill=GOLD)
        f = font(36)
        tw = d.textlength(str(step), font=f)
        d.text((x + 28 - tw / 2, 58), str(step), font=f, fill=DARK, anchor="lm")
        x += 76
    d.rectangle((x, 34, x + 6, 82), fill=GOLD)
    d.text((x + 22, 58), title, font=font(50), fill=WHITE, anchor="lm",
           stroke_width=2, stroke_fill=(0, 0, 0))
    if subtitle:
        d.text((x + 24, 112), subtitle, font=font(28, bold=False), fill=SOFT, anchor="lm",
               stroke_width=1, stroke_fill=(0, 0, 0))


def badge(canvas, text, center, color=GOLD, size=30):
    d = ImageDraw.Draw(canvas)
    f = font(size)
    tw = d.textlength(text, font=f)
    x0, y0 = center[0] - tw / 2 - 20, center[1] - size * 0.75
    x1, y1 = center[0] + tw / 2 + 20, center[1] + size * 0.75
    d.rounded_rectangle((x0, y0, x1, y1), radius=int(size * 0.75), fill=color)
    d.text(center, text, font=f, fill=DARK, anchor="mm")


def arrow(canvas, x0, x1, y, label=None):
    d = ImageDraw.Draw(canvas)
    d.line((x0, y, x1 - 26, y), fill=GOLD, width=12)
    d.polygon([(x1, y), (x1 - 40, y - 30), (x1 - 40, y + 30)], fill=GOLD)
    if label:
        f = font(34)
        d.text(((x0 + x1) / 2, y - 52), label, font=f, fill=WHITE, anchor="mm",
               stroke_width=2, stroke_fill=(0, 0, 0))


def watermark(canvas):
    d = ImageDraw.Draw(canvas)
    d.text((canvas.width - 28, canvas.height - 22), "Frame Stash", font=font(24), fill=(255, 255, 255, 160),
           anchor="rs", stroke_width=1, stroke_fill=(0, 0, 0))


def save(img, name):
    img.save(OUT / name, optimize=True)
    print("wrote", OUT / name)


# --- images --------------------------------------------------------------------------------------

def banner():
    img = fit(raw("01_wall"), (1600, 600))
    shade = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(shade)
    for y in range(img.height):
        a = int(150 * (abs(y - img.height * 0.45) / (img.height * 0.55)) ** 1.4)
        d.line((0, y, img.width, y), fill=(*DARK, min(200, a + 60)))
    img.paste(shade, (0, 0), shade)
    d = ImageDraw.Draw(img)
    d.text((800, 220), "FRAME STASH", font=font(120), fill=WHITE, anchor="mm", stroke_width=5, stroke_fill=(0, 0, 0))
    # Subtitle on a dark panel so it stays readable over the busy frame wall.
    sub = "Label chests with item frames. Press G to put everything away."
    f = font(38)
    tw = d.textlength(sub, font=f)
    panel_img = Image.new("RGBA", (int(tw) + 70, 66), (0, 0, 0, 0))
    ImageDraw.Draw(panel_img).rounded_rectangle((0, 0, panel_img.width - 1, 65), radius=18, fill=(*DARK, 225))
    img.paste(panel_img, (800 - panel_img.width // 2, 318 - 33), panel_img)
    d.text((800, 318), sub, font=f, fill=WHITE, anchor="mm")
    badge(img, "Fabric & NeoForge  ·  G to stash  ·  H to restock", (800, 420), size=28)
    save(img, "banner.png")


def wall():
    img = fit(raw("01_wall"))
    title_bar(img, "Label any chest, barrel or shulker box",
              "The item in the frame is the label. That's the whole setup.", step=1)
    badge(img, "Normal frame = home for that item", (470, 820), size=28)
    badge(img, "Glow frame = overflow  ·  Hopper = catch-all", (1130, 820), size=28)
    watermark(img)
    save(img, "1_labelled_wall.png")


def before_after():
    img = backdrop("02_inventory_before")
    scale = 1.12
    a = panel("02_inventory_before", INVENTORY, scale)
    b = panel("04_inventory_after", INVENTORY, scale)
    y = 200
    shadowed_paste(img, a, (70, y))
    shadowed_paste(img, b, (W - 70 - b.width, y))
    arrow(img, 70 + a.width + 28, W - 70 - b.width - 28, y + a.height // 2, "Press G")
    badge(img, "Before", (70 + a.width // 2, y + a.height + 45), color=(235, 235, 235))
    badge(img, "After: 705 items put away", (W - 70 - b.width // 2, y + b.height + 45))
    title_bar(img, "One key empties your inventory",
              "Hotbar, armour and offhand stay put. Only items with a home move.", step=2)
    watermark(img)
    save(img, "2_before_after.png")


def action_bar():
    img = raw("03_action_bar")
    # Highlight the action bar text (centred, just above the hotbar).
    box = (660, 852, 1260, 898)
    inset = img.crop(box)
    inset = inset.resize((inset.width * 2, inset.height * 2), Image.NEAREST)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((box[0] - 8, box[1] - 6, box[2] + 8, box[3] + 6), radius=14, outline=GOLD, width=5)
    img = fit(img)
    # Enlarged copy of the message, bottom right.
    ix, iy = W - inset.width - 40, 560
    frame = Image.new("RGB", (inset.width + 16, inset.height + 16), GOLD)
    frame.paste(inset, (8, 8))
    shadowed_paste(img, frame, (ix, iy))
    badge(img, "Action bar, zoomed in", (ix + frame.width // 2, iy - 36), size=24)
    title_bar(img, "Instant feedback",
              "A summary on the action bar, a particle trail to each container and a soft sound.", step=3)
    watermark(img)
    save(img, "3_feedback.png")


def overflow():
    img = backdrop("05_primary_full")
    scale = 1.25
    a = chest_rows(panel("05_primary_full", CHEST_3, scale), 3, scale)
    b = chest_rows(panel("06_overflow", CHEST_3, scale), 3, scale)
    y = 330
    shadowed_paste(img, a, (80, y))
    shadowed_paste(img, b, (W - 80 - b.width, y))
    arrow(img, 80 + a.width + 28, W - 80 - b.width - 28, y + a.height // 2, "full")
    badge(img, "Stone chest (item frame): full", (80 + a.width // 2, y + a.height + 55), color=(235, 235, 235))
    badge(img, "Overflow chest (glow frame)", (W - 80 - b.width // 2, y + b.height + 55))
    title_bar(img, "Overflow chains",
              "When every normal-frame container is full, items continue into glow-frame ones.", step=4)
    watermark(img)
    save(img, "4_overflow.png")


def menu():
    img = backdrop("07_menu")
    a = panel("07_menu", CHEST_3, 1.2)
    b = panel("08_nearby", CHEST_6, 1.0)
    a = chest_rows(a, 3, 1.2)
    b = chest_rows(b, 6, 1.0)
    shadowed_paste(img, a, (110, 260))
    shadowed_paste(img, b, (W - 110 - b.width, 230))
    badge(img, "/framestash", (110 + a.width // 2, 260 + a.height + 50))
    badge(img, "Every container nearby, with its labels", (W - 110 - b.width // 2, 230 + b.height + 50))
    d = ImageDraw.Draw(img)
    f = font(28, bold=False)
    lines = ["Stash and Restock buttons", "Your own hotbar, sound and particle toggles",
             "Click a container to get a trail to it"]
    for i, line in enumerate(lines):
        d.text((110, 640 + i * 44), "•  " + line, font=f, fill=SOFT, anchor="lm")
    title_bar(img, "A menu that vanilla clients can use",
              "Built from a normal chest screen, so nobody needs to install anything.", step=5)
    watermark(img)
    save(img, "5_menu.png")


def how_it_works():
    img = fit(raw("09_closeup"))
    dim = Image.new("RGBA", img.size, (*DARK, 70))
    img.paste(dim, (0, 0), dim)
    title_bar(img, "How it works")
    steps = [("1", "Hang a frame", "on a chest, barrel or shulker box"),
             ("2", "Put an item in it", "that item now lives there"),
             ("3", "Press G", "everything goes home (or type /stash)")]
    d = ImageDraw.Draw(img)
    card_w, gap = 440, 40
    x = (W - (card_w * 3 + gap * 2)) // 2
    for num, head, sub in steps:
        card = Image.new("RGBA", (card_w, 170), (*DARK, 215))
        img.paste(card, (x, 680), card)
        d.ellipse((x + 22, 702, x + 78, 758), fill=GOLD)
        d.text((x + 50, 730), num, font=font(34), fill=DARK, anchor="mm")
        d.text((x + 96, 730), head, font=font(36), fill=WHITE, anchor="lm")
        d.text((x + 26, 800), sub, font=font(26, bold=False), fill=SOFT, anchor="lm")
        x += card_w + gap
    watermark(img)
    save(img, "0_how_it_works.png")


def gif():
    size = (800, 450)
    frames, durations = [], []

    def caption(img, text):
        img = img.copy()
        d = ImageDraw.Draw(img)
        d.rectangle((0, size[1] - 56, size[0], size[1]), fill=DARK)
        d.text((size[0] / 2, size[1] - 28), text, font=font(26), fill=WHITE, anchor="mm")
        return img

    before = fit(raw("02_inventory_before"), size)
    frames.append(caption(before, "A full inventory after a mining trip...")); durations.append(1600)
    for i in range(30):
        f = RAW / "gif" / f"gif_{i:02d}.png"
        if f.exists():
            img = fit(Image.open(f).convert("RGB"), size)
            frames.append(caption(img, "Press G")); durations.append(90)
    durations[-1] = 1400
    after = fit(raw("04_inventory_after"), size)
    frames.append(caption(after, "...everything is back in its labelled chest")); durations.append(2000)

    palette_frames = [f.quantize(colors=192, method=Image.MEDIANCUT, dither=Image.NONE) for f in frames]
    palette_frames[0].save(OUT / "demo.gif", save_all=True, append_images=palette_frames[1:],
                           duration=durations, loop=0, optimize=True, disposal=1)
    print("wrote", OUT / "demo.gif")


def icon():
    """Original 32x32 pixel-art icon: a wooden frame holding a small chest, upscaled to 512 px."""
    px = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(px)
    # Rounded dark tile background.
    d.rounded_rectangle((0, 0, 31, 31), radius=6, fill=(38, 34, 48, 255))
    # Frame: outer dark wood, inner light wood, leather backing.
    d.rectangle((4, 4, 27, 27), fill=(96, 62, 34, 255))
    d.rectangle((5, 5, 26, 26), fill=(171, 121, 71, 255))
    d.rectangle((7, 7, 24, 24), fill=(120, 52, 36, 255))
    d.rectangle((8, 8, 23, 23), fill=(150, 72, 48, 255))
    # Chest inside the frame.
    d.rectangle((10, 12, 21, 21), fill=(112, 70, 30, 255))
    d.rectangle((11, 13, 20, 16), fill=(170, 110, 48, 255))
    d.rectangle((11, 17, 20, 20), fill=(150, 95, 40, 255))
    d.line((10, 16, 21, 16), fill=(70, 42, 18, 255))
    d.rectangle((15, 15, 16, 18), fill=(220, 220, 225, 255))
    # Gold arrow pointing down-left into the chest: the "stash" motion.
    gold, shade = (255, 190, 70, 255), (196, 128, 30, 255)
    for i in range(6):
        px.putpixel((28 - i, 2 + i), gold)
        px.putpixel((29 - i, 2 + i), shade)
    for (x, y) in [(22, 4), (22, 5), (22, 6), (22, 7), (22, 8), (23, 8), (24, 8), (25, 8), (26, 8)]:
        px.putpixel((x, y), gold)
    # Frame highlights.
    d.line((5, 5, 26, 5), fill=(205, 155, 98, 255))
    d.line((5, 5, 5, 26), fill=(205, 155, 98, 255))
    big = px.resize((512, 512), Image.NEAREST)
    big.save(OUT / "icon.png")
    px.resize((128, 128), Image.NEAREST).save(ROOT / "common" / "src" / "main" / "resources" / "assets"
                                              / "frame_stash" / "icon.png")
    print("wrote", OUT / "icon.png")


if __name__ == "__main__":
    (ROOT / "common" / "src" / "main" / "resources" / "assets" / "frame_stash").mkdir(parents=True, exist_ok=True)
    banner()
    how_it_works()
    wall()
    before_after()
    action_bar()
    overflow()
    menu()
    gif()
    icon()
