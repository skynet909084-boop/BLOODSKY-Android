"""Small dependency-free PNG fixture and screenshot color probe for Android UI smoke.

Covers non-interlaced RGB/RGBA 8-bit screenshots from adb screencap.
Validates chunk CRCs so corrupt fixture bytes cannot silently pass visual tests.
"""
import struct
import zlib

_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def _chunk(kind, payload):
    return (struct.pack(">I", len(payload)) + kind + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xffffffff))


def solid_rgba_png(width, height, color):
    if not (1 <= width <= 512 and 1 <= height <= 512):
        raise ValueError("PNG fixture dimensions are invalid")
    if len(color) != 4 or any(not isinstance(c, int) or c < 0 or c > 255 for c in color):
        raise ValueError("RGBA color must contain four bytes")
    row = b"\x00" + bytes(color) * width
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (_SIGNATURE + _chunk(b"IHDR", header)
            + _chunk(b"IDAT", zlib.compress(row * height))
            + _chunk(b"IEND", b""))


def _paeth(a, b, c):
    prediction = a + b - c
    distances = (abs(prediction - a), abs(prediction - b), abs(prediction - c))
    if distances[0] <= distances[1] and distances[0] <= distances[2]:
        return a
    if distances[1] <= distances[2]:
        return b
    return c


def count_matching_png_pixels(data, rgb, tolerance=8):
    """Count visible RGB pixels near a distinctive expected color."""
    if not data.startswith(_SIGNATURE):
        raise ValueError("Not a PNG screenshot")
    if len(rgb) != 3 or tolerance < 0 or tolerance > 255:
        raise ValueError("Invalid color probe")
    position = len(_SIGNATURE)
    width = height = channels = None
    compressed = bytearray()
    has_end = False
    while position + 12 <= len(data):
        length = struct.unpack_from(">I", data, position)[0]
        position += 4
        if length > 32 * 1024 * 1024 or position + length + 8 > len(data):
            raise ValueError("Invalid PNG chunk length")
        kind = data[position:position + 4]
        payload = data[position + 4:position + 4 + length]
        expected_crc = struct.unpack_from(">I", data, position + 4 + length)[0]
        if (zlib.crc32(kind + payload) & 0xffffffff) != expected_crc:
            raise ValueError("Corrupt PNG chunk checksum: " + repr(kind))
        position += length + 8
        if kind == b"IHDR":
            if width is not None or length != 13:
                raise ValueError("Invalid PNG IHDR")
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            if (depth != 8 or color not in (2, 6) or compression != 0
                    or filtering != 0 or interlace != 0 or width <= 0 or height <= 0
                    or width * height > 10000000):
                raise ValueError("Unsupported PNG screenshot format")
            channels = 3 if color == 2 else 4
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            has_end = True
            break
    if not has_end or channels is None or not compressed:
        raise ValueError("Incomplete PNG screenshot")
    stride = width * channels
    raw = zlib.decompress(compressed)
    if len(raw) != height * (stride + 1):
        raise ValueError("Invalid PNG scanline length")
    previous = bytearray(stride)
    match = 0
    position = 0
    for _ in range(height):
        filtering = raw[position]
        position += 1
        line = bytearray(raw[position:position + stride])
        position += stride
        if filtering not in (0, 1, 2, 3, 4):
            raise ValueError("Unsupported PNG filter")
        if filtering:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                above = previous[i]
                upperleft = previous[i - channels] if i >= channels else 0
                if filtering == 1:
                    predictor = left
                elif filtering == 2:
                    predictor = above
                elif filtering == 3:
                    predictor = (left + above) // 2
                else:
                    predictor = _paeth(left, above, upperleft)
                line[i] = (line[i] + predictor) & 255
        for i in range(0, stride, channels):
            if (all(abs(line[i + j] - rgb[j]) <= tolerance for j in range(3))
                    and (channels == 3 or line[i + 3] >= 240)):
                match += 1
        previous = line
    return match
