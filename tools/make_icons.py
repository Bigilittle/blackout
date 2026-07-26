#!/usr/bin/env python3
"""Генератор иконки запуска: чёрный диск в янтарной короне — затмение.

Иконка нарисована кодом, а не положена бинарником, чтобы её можно было
прочитать и поправить, а не открывать в редакторе. Только stdlib.

    python3 tools/make_icons.py
"""
import os
import struct
import zlib

SS = 4  # суперсэмплинг: считаем SS*SS точек на пиксель ради сглаживания краёв

DISC = (0.09, 0.09, 0.10)   # почти чёрный диск
CORONA = (1.00, 0.70, 0.11)  # янтарное кольцо вокруг него

R_DISC = 0.34      # радиусы в долях половины стороны
R_RING_IN = 0.36
R_RING_OUT = 0.43

BUCKETS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def sample(x, y, half):
    """Цвет и альфа в точке; координаты — от центра холста."""
    d = (x * x + y * y) ** 0.5 / half
    if d <= R_DISC:
        return DISC + (1.0,)
    if R_RING_IN <= d <= R_RING_OUT:
        return CORONA + (1.0,)
    return (0.0, 0.0, 0.0, 0.0)


def render(size):
    """Пиксели RGBA, построчно."""
    half = size * SS / 2.0
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            r = g = b = a = 0.0
            for sy in range(SS):
                for sx in range(SS):
                    sr, sg, sb, sa = sample(
                        (px * SS + sx + 0.5) - half,
                        (py * SS + sy + 0.5) - half,
                        half,
                    )
                    r += sr * sa
                    g += sg * sa
                    b += sb * sa
                    a += sa
            if a > 0:
                # цвет усредняем по покрытым подпикселям, альфу — по всем
                row += bytes((
                    round(r / a * 255),
                    round(g / a * 255),
                    round(b / a * 255),
                    round(a / (SS * SS) * 255),
                ))
            else:
                row += b"\x00\x00\x00\x00"
        rows.append(bytes(row))
    return rows


def write_png(path, size):
    raw = b"".join(b"\x00" + row for row in render(size))  # 0 = фильтр None

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("%s (%dx%d)" % (path, size, size))


if __name__ == "__main__":
    res = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "app", "src", "main", "res",
    )
    for bucket, size in BUCKETS.items():
        write_png(os.path.join(res, "mipmap-" + bucket, "ic_launcher.png"), size)
