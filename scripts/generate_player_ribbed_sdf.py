"""Generate the fixed ribbed pane's RG16 signed-distance field using only Python stdlib.

Run from N-Zik: python scripts/generate_player_ribbed_sdf.py
The exact polygon remains the clip. The 256px field supplies its optical distance
and finite-difference normals without a 360-edge loop in every GPU fragment.
"""
import math
from pathlib import Path
import re
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'ComposeN-Zik/src/androidMain/kotlin/app/n_zik/android/components/player/PlayerTransportRibbedShape.kt'
OUTPUT = ROOT / 'ComposeN-Zik/src/androidMain/res/drawable-nodpi/player_ribbed_sdf.png'
SIZE = 256


def contour():
    return [(float(x), float(y)) for x, y in re.findall(
        r'(?:moveTo|lineTo)\(size.width \* ([\d.]+)f, size.height \* ([\d.]+)f\)', SOURCE.read_text())]


def signed_distance(x, y, points):
    distance_squared = float('inf')
    inside = False
    for (ax, ay), (bx, by) in zip(points, points[1:] + points[:1]):
        ex, ey = bx - ax, by - ay
        vx, vy = x - ax, y - ay
        t = max(0.0, min(1.0, (vx * ex + vy * ey) / (ex * ex + ey * ey)))
        distance_squared = min(distance_squared, (vx - t * ex) ** 2 + (vy - t * ey) ** 2)
        if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
            inside = not inside
    return math.sqrt(distance_squared) * (-1.0 if inside else 1.0)


def chunk(kind, data):
    return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data))


def generate():
    points = contour()
    assert len(points) == 360
    rows = bytearray()
    for y in range(SIZE):
        rows.append(0)  # PNG no-filter row
        for x in range(SIZE):
            sd = signed_distance((x + .5) / SIZE, (y + .5) / SIZE, points)
            packed = round(max(0.0, min(1.0, .5 + sd * .5)) * 65535)
            # Opaque RG16; no premultiplied-alpha or angle-wrap interpolation artifacts.
            rows.extend((packed >> 8, packed & 255, 0, 255))
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('!2I5B', SIZE, SIZE, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b'')
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(png)
    print(f'{OUTPUT.name}: {len(png)} bytes; {len(points)} edges; {SIZE}x{SIZE} RG16')


if __name__ == '__main__':
    generate()
