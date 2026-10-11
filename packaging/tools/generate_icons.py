#!/usr/bin/env python3
"""Generate shared Crewcast icons (PNG + Windows ICO) without extra dependencies."""
from __future__ import annotations

import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACCENT = (0x5B, 0x8C, 0xFF)
ON_ACCENT = (0x0B, 0x10, 0x20)
SIZE = 512


def _inside_rounded_rect(x: int, y: int, size: int, radius: float) -> bool:
    inset = size / 16.0
    left, top = inset, inset
    right, bottom = size - inset, size - inset
    if x < left or x >= right or y < top or y >= bottom:
        return False
    r = radius
    corners = (
        (left + r, top + r),
        (right - r, top + r),
        (left + r, bottom - r),
        (right - r, bottom - r),
    )
    for cx, cy in corners:
        if (x < left + r or x >= right - r) and (y < top + r or y >= bottom - r):
            if (x - cx) ** 2 + (y - cy) ** 2 > r * r:
                return False
    return True


def _inside_play(x: int, y: int, size: int) -> bool:
    s = float(size)
    ax, ay = s * 0.38, s * 0.28
    bx, by = s * 0.38, s * 0.72
    cx, cy = s * 0.72, s * 0.50
    denom = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(denom) < 1e-6:
        return False
    w1 = ((by - cy) * (x - cx) + (cx - bx) * (y - cy)) / denom
    w2 = ((cy - ay) * (x - cx) + (ax - cx) * (y - cy)) / denom
    w3 = 1.0 - w1 - w2
    return w1 >= 0 and w2 >= 0 and w3 >= 0


def render_rgba(size: int) -> bytes:
    radius = size / 5.0
    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            if _inside_play(x, y, size):
                row.extend((*ON_ACCENT, 255))
            elif _inside_rounded_rect(x, y, size, radius):
                row.extend((*ACCENT, 255))
            else:
                row.extend((0, 0, 0, 0))
        rows.append(bytes(row))
    return b"".join(rows)


def write_png(path: Path, size: int) -> None:
    raw = render_rgba(size)
    # RGBA -> filter 0 each row
    filtered = b"".join(b"\x00" + raw[y * size * 4 : (y + 1) * size * 4] for y in range(size))
    compressed = zlib.compress(filtered, 9)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", compressed) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def write_ico(path: Path, sizes: tuple[int, ...]) -> None:
    images = []
    for dim in sizes:
        rgba = render_rgba(dim)
        # 32-bit BGRA bottom-up BMP for ICO
        row_bytes = dim * 4
        and_row = ((dim + 31) // 32) * 4
        pixel_data = bytearray()
        for y in range(dim - 1, -1, -1):
            row = rgba[y * dim * 4 : (y + 1) * dim * 4]
            bgra = bytearray()
            for i in range(0, len(row), 4):
                r, g, b, a = row[i : i + 4]
                bgra.extend((b, g, r, a))
            pixel_data.extend(bgra)
            pixel_data.extend(b"\x00" * and_row)
        header = struct.pack("<IIIHH", 40, dim, dim * 2, 1, 32)
        images.append(header + bytes(pixel_data))

    offset = 6 + 16 * len(images)
    out = bytearray(struct.pack("<HHH", 0, 1, len(images)))
    for dim, data in zip(sizes, images):
        # ICO: width/height 0 means 256 pixels.
        w = 0 if dim >= 256 else dim
        h = 0 if dim >= 256 else dim
        out.extend(struct.pack("<BBBBHHII", w, h, 0, 0, 1, 32, len(data), offset))
        offset += len(data)
    for data in images:
        out.extend(data)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(out)


def main() -> None:
    shared = ROOT / "packaging" / "shared" / "icon-512.png"
    linux_png = ROOT / "packaging" / "linux" / "crewcast.png"
    win_ico = ROOT / "packaging" / "windows" / "Crewcast.ico"

    if not shared.exists():
        write_png(shared, SIZE)
        print(f"Wrote {shared}")
    if not linux_png.exists():
        write_png(linux_png, SIZE)
        print(f"Wrote {linux_png}")
    if not win_ico.exists():
        write_ico(win_ico, (16, 32, 48, 256))
        print(f"Wrote {win_ico}")


if __name__ == "__main__":
    main()
