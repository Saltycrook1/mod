"""Original Stormward 16px sprites; Python standard library only, reproducible."""
from pathlib import Path
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/stormward"

def png(name, pixels):
    path = ROOT / "textures" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
    raw = b"".join(b"\0" + bytes(sum(row, ())) for row in pixels)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    path.write_bytes(data + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))

def rgba(rgb): return (*rgb, 255)
def blank(): return [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
def put(p, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16: p[y][x] = rgba(color)

# A handheld steel receiver with an antenna, protected teal phosphor display and controls.
p = blank()
for y in range(2, 15):
    for x in range(3, 13):
        color = (47, 66, 78) if x in (3, 12) or y in (2, 14) else (88, 105, 114)
        put(p, x, y, color)
for y in range(0, 3): put(p, 5, y, (169, 193, 194))
for y in range(4, 11):
    for x in range(5, 11): put(p, x, y, (18, 44, 48))
for x, y in [(5, 5), (6, 4), (7, 4), (8, 4), (9, 5), (10, 6), (10, 8), (9, 9), (8, 10), (6, 9), (5, 8)]: put(p, x, y, (58, 126, 113))
for x, y in [(7, 8), (8, 7), (9, 6), (7, 6)]: put(p, x, y, (117, 244, 196))
for x, y in [(5, 12), (6, 12), (9, 12), (10, 12)]: put(p, x, y, (217, 184, 108))
put(p, 4, 3, (173, 191, 193)); put(p, 11, 13, (34, 48, 58))
png("item/storm_receiver.png", p)

# Reinforced storm-shelter panel: interlocking ribs and eight flush rivets.
p = blank()
for y in range(16):
    for x in range(16):
        noise = ((x * 13 + y * 7) % 5) - 2
        color = (69 + noise, 86 + noise, 98 + noise)
        if x in (0, 15) or y in (0, 15): color = (35, 49, 61)
        elif x in (1, 14) or y in (1, 14): color = (115, 131, 140)
        elif abs(x - y) <= 1 or abs((15 - x) - y) <= 1: color = (92, 111, 122)
        put(p, x, y, color)
for x, y in [(3, 3), (12, 3), (3, 12), (12, 12), (7, 3), (3, 7), (12, 8), (8, 12)]:
    put(p, x, y, (174, 191, 194)); put(p, x + 1, y, (41, 59, 68))
png("block/reinforced_shelter.png", p)

# Siren enclosure: amber paint, recessed vents, service screws and status indicator.
for powered in (False, True):
    p = blank()
    for y in range(16):
        for x in range(16):
            color = (162 + ((x + y) % 3) * 3, 126, 52)
            if y < 2 or y > 13 or x < 2 or x > 13: color = (56, 65, 67)
            elif 4 <= y <= 10 and 3 <= x <= 12:
                color = (35, 45, 48) if y % 2 == 0 else (104, 94, 61)
            if y == 12 and x in (6, 7, 8, 9): color = (245, 127, 66) if powered else (50, 83, 74)
            put(p, x, y, color)
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]: put(p, x, y, (186, 193, 175))
    png(f"block/storm_siren{'_active' if powered else ''}.png", p)

def js(name, obj):
    path = ROOT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")

js("models/item/storm_receiver.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "stormward:item/storm_receiver"}})
for name in ("reinforced_shelter", "storm_siren", "storm_siren_active"):
    js(f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"stormward:block/{name}"}})
for name in ("reinforced_shelter", "storm_siren"):
    js(f"models/item/{name}.json", {"parent": f"stormward:block/{name}"})
js("blockstates/reinforced_shelter.json", {"variants": {"": {"model": "stormward:block/reinforced_shelter"}}})
js("blockstates/storm_siren.json", {"variants": {"powered=false": {"model": "stormward:block/storm_siren"}, "powered=true": {"model": "stormward:block/storm_siren_active"}}})
js("lang/en_us.json", {
    "item.stormward.storm_receiver": "Storm Receiver",
    "block.stormward.reinforced_shelter": "Reinforced Shelter Panel",
    "block.stormward.storm_siren": "Storm Siren",
    "itemGroup.stormward": "Stormward",
})
print("Wrote original receiver, shelter and siren textures, models, states and English names.")
