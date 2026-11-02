#!/usr/bin/env python3
"""生成模组图标 assets/vmping/icon.png（128x128）。

不依赖 Pillow：纯 zlib + struct 手写 PNG，CI / 无图形环境也能重新生成。

图形：深色圆角底 + 绿色标点图钉（圆头 + 尖脚 + 内孔）+ 右上两道"ping"涟漪弧线。
配色取自 core/PingKind 的 DEFAULT（0x6FD08C），与游戏内标点颜色一致。

用法：python3 tools/dev/make-icon.py
"""
import struct
import zlib
from pathlib import Path

SIZE = 128
BG_TOP = (0x22, 0x2B, 0x33)
BG_BOTTOM = (0x14, 0x1A, 0x20)
BORDER = (0x3A, 0x4A, 0x55)
PIN = (0x6F, 0xD0, 0x8C)
PIN_HI = (0xA8, 0xF0, 0xBE)
PIN_HOLE = (0x14, 0x1A, 0x20)
RIPPLE = (0x8A, 0xE0, 0xA8)

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/vmping/icon.png"


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rounded_mask(x, y, radius):
    r = radius
    cx = min(max(x, r), SIZE - 1 - r)
    cy = min(max(y, r), SIZE - 1 - r)
    dx, dy = x - cx, y - cy
    return dx * dx + dy * dy <= r * r


def in_triangle(px, py, a, b, c):
    def sign(p1, p2, p3):
        return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])

    d1 = sign((px, py), a, b)
    d2 = sign((px, py), b, c)
    d3 = sign((px, py), c, a)
    has_neg = d1 < 0 or d2 < 0 or d3 < 0
    has_pos = d1 > 0 or d2 > 0 or d3 > 0
    return not (has_neg and has_pos)


def blend(dst, src, alpha):
    """把 src 以 alpha（0~1）叠到 dst 上。"""
    return tuple(round(dst[i] * (1 - alpha) + src[i] * alpha) for i in range(3))


def build_pixels():
    rows = []
    pin_cx, pin_cy, pin_r = 60, 52, 24
    hole_r = 9
    # 涟漪圆心与图钉同心
    for y in range(SIZE):
        row = bytearray()
        for x in range(SIZE):
            if not rounded_mask(x, y, 18):
                row.extend((0, 0, 0, 0))
                continue

            color = lerp(BG_TOP, BG_BOTTOM, y / (SIZE - 1))

            # 边框（贴着圆角外沿 3px）
            if not rounded_mask(x, y, 15):
                color = blend(color, BORDER, 0.85)

            dx, dy = x - pin_cx, y - pin_cy
            dist = (dx * dx + dy * dy) ** 0.5

            # 图钉：圆头 + 三角脚
            in_head = dist <= pin_r
            in_tail = in_triangle(x, y, (pin_cx - 15, pin_cy + 10), (pin_cx + 15, pin_cy + 10),
                                  (pin_cx, pin_cy + 52))
            if in_head or in_tail:
                # 左上高光：按到"光源方向"的距离做渐变
                light = max(0.0, 1.0 - ((dx + 10) ** 2 + (dy + 12) ** 2) ** 0.5 / 46.0)
                color = blend(PIN, PIN_HI, min(0.75, light * 0.9))

            # 内孔（挖空成背景色，让图钉看起来是"环"）
            if dist <= hole_r:
                color = blend(PIN_HOLE, color, 0.15)

            # 涟漪：右上方向的两道弧（只画 10°~80° 的扇区，避开图钉本体）
            if dist > pin_r + 2:
                angle = None
                if dx != 0 or dy != 0:
                    import math
                    angle = math.degrees(math.atan2(-dy, dx))  # 0 = 右，90 = 上
                if angle is not None and -80 <= angle <= 10:
                    for ring_r, thickness in ((pin_r + 10, 3.0), (pin_r + 22, 2.5)):
                        if abs(dist - ring_r) <= thickness:
                            # 越远越淡
                            alpha = 0.95 if ring_r == pin_r + 10 else 0.6
                            color = blend(color, RIPPLE, alpha)

            row.extend((color[0], color[1], color[2], 255))
        rows.append(bytes(row))
    return rows


def write_png(path: Path, rows):
    raw = b"".join(b"\x00" + row for row in rows)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        c += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return c

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path} ({len(png)} bytes)")


if __name__ == "__main__":
    write_png(OUT, build_pixels())
