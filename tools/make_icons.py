"""生成 AURORA 幻彩灯控的启动图标（无外部素材）。"""
from PIL import Image, ImageDraw, ImageFilter
import math
import os
import random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
ASSETS = os.path.join(ROOT, "assets")
os.makedirs(ASSETS, exist_ok=True)

S = 1024


def vertical_gradient(size, top, bottom):
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        color = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(size[0]):
            px[x, y] = color
    return img


base = vertical_gradient((S, S), (5, 7, 15), (10, 15, 31)).convert("RGBA")

blob_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
blob_draw = ImageDraw.Draw(blob_layer)
blobs = [
    ((80, 40, 760, 720), (91, 240, 208, 130)),
    ((520, 120, 1180, 780), (139, 123, 255, 125)),
    ((180, 560, 980, 1200), (255, 122, 198, 95)),
    ((700, 460, 1250, 1000), (89, 194, 255, 70)),
]
for box, color in blobs:
    blob_draw.ellipse(box, fill=color)
blob_layer = blob_layer.filter(ImageFilter.GaussianBlur(140))
base = Image.alpha_composite(base, blob_layer)

random.seed(20261108)
stars = Image.new("RGBA", (S, S), (0, 0, 0, 0))
star_draw = ImageDraw.Draw(stars)
for _ in range(70):
    x = random.randint(50, S - 50)
    y = random.randint(40, int(S * 0.8))
    r = random.uniform(1.2, 3.8)
    a = random.randint(35, 130)
    star_draw.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, a))
base = Image.alpha_composite(base, stars)

# 中心电源符号
glyph = Image.new("L", (S, S), 0)
gd = ImageDraw.Draw(glyph)
cx, cy = S // 2, int(S * 0.53)
radius = 246
stroke = 78
gd.ellipse((cx - radius, cy - radius, cx + radius, cy + radius),
           outline=255, width=stroke)
# 擦出顶部缺口
gd.rectangle((cx - 52, cy - radius - 30, cx + 52, cy - radius + 92), fill=0)
# 竖向电源线（圆头）
gd.rounded_rectangle((cx - stroke // 2, cy - radius + 18, cx + stroke // 2, cy - 34),
                     radius=stroke // 2, fill=255)

# 彩色辉光
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
glow_color = Image.new("RGBA", (S, S), (91, 240, 208, 0))
glow_color.putalpha(glyph)
glow = Image.composite(glow_color, glow, glyph)
glow = glow.filter(ImageFilter.GaussianBlur(46))
base = Image.alpha_composite(base, glow)

# 外圈轨道
orbit = Image.new("RGBA", (S, S), (0, 0, 0, 0))
od = ImageDraw.Draw(orbit)
od.ellipse((cx - 330, cy - 330, cx + 330, cy + 330),
           outline=(91, 240, 208, 95), width=5)

# 四角星芒
def sparkle(draw, x, y, r, color):
    draw.polygon([
        (x, y - r), (x + r * 0.28, y - r * 0.28),
        (x + r, y), (x + r * 0.28, y + r * 0.28),
        (x, y + r), (x - r * 0.28, y + r * 0.28),
        (x - r, y), (x - r * 0.28, y - r * 0.28),
    ], fill=color)


sparkle(od, 230, 250, 44, (255, 255, 255, 150))
sparkle(od, 795, 285, 30, (255, 255, 255, 120))
sparkle(od, 748, 720, 38, (255, 255, 255, 105))
base = Image.alpha_composite(base, orbit)

white = Image.new("RGBA", (S, S), (255, 255, 255, 0))
white.putalpha(glyph)
base = Image.alpha_composite(base, white)

# 略收边，让图标更聚焦
ring_glow = base.filter(ImageFilter.GaussianBlur(2))
master = Image.alpha_composite(ring_glow, base)

os.makedirs(os.path.join(ASSETS), exist_ok=True)
master.save(os.path.join(ASSETS, "icon-master.png"))

# 圆形版本
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).ellipse((18, 18, S - 18, S - 18), fill=255)
round_icon = master.copy()
round_icon.putalpha(mask)
round_icon.save(os.path.join(ASSETS, "icon-round-1024.png"))

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
for folder, size in sizes.items():
    out_dir = os.path.join(RES, folder)
    os.makedirs(out_dir, exist_ok=True)
    master.resize((size, size), Image.LANCZOS).save(
        os.path.join(out_dir, "ic_launcher.png"), optimize=True)
    round_icon.resize((size, size), Image.LANCZOS).save(
        os.path.join(out_dir, "ic_launcher_round.png"), optimize=True)

print("icons written")
