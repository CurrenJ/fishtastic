"""Turn a save into a compact release-smoke template world.

Copies SRC to DST, keeping only region chunks within --radius chunks of any chunk that holds a
fishtastic block (missing flat-world chunks regenerate on load), and drops the files a template
must not carry (session.lock, level.dat_old, fishtastic backups).

  python tools/release-smoke/make_template.py fabric/run/saves/fishtastic_render_selftest \
      tools/release-smoke/worlds/26.1.2
"""
import shutil
import struct
import sys
import zlib
from pathlib import Path

MARK = b"fishtastic:"
SECTOR = 4096


def read_chunks(path: Path):
    data = path.read_bytes()
    if len(data) < 2 * SECTOR:
        return {}
    chunks = {}
    for i in range(1024):
        off, = struct.unpack(">I", b"\0" + data[i * 4:i * 4 + 3])
        if off == 0:
            continue
        length, ctype = struct.unpack(">IB", data[off * SECTOR:off * SECTOR + 5])
        stamp = data[SECTOR + i * 4:SECTOR + i * 4 + 4]
        chunks[i] = (stamp, data[off * SECTOR:off * SECTOR + 4 + length], ctype)
    return chunks


def payload(raw: bytes, ctype: int) -> bytes:
    body = raw[5:]
    if ctype == 2:
        return zlib.decompress(body)
    if ctype == 1:
        import gzip
        return gzip.decompress(body)
    return body


def write_chunks(path: Path, chunks) -> None:
    loc, stamps, body = bytearray(SECTOR), bytearray(SECTOR), bytearray()
    sector = 2
    for i, (stamp, raw, _) in sorted(chunks.items()):
        n = (len(raw) + SECTOR - 1) // SECTOR
        loc[i * 4:i * 4 + 4] = struct.pack(">I", sector)[1:] + bytes([n])
        stamps[i * 4:i * 4 + 4] = stamp
        body += raw + b"\0" * (n * SECTOR - len(raw))
        sector += n
    path.write_bytes(bytes(loc) + bytes(stamps) + bytes(body))


def coords(region: Path, i: int):
    _, rx, rz, _ = region.name.split(".")
    return int(rx) * 32 + i % 32, int(rz) * 32 + i // 32


def main(src: str, dst: str, radius: int = 3) -> None:
    src, dst = Path(src), Path(dst)
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst, ignore=shutil.ignore_patterns("session.lock", "level.dat_old", "fishtastic_backups"))
    regions = list(dst.glob("dimensions/*/*/region/*.mca")) + list(dst.glob("region/*.mca"))
    loaded = {r: read_chunks(r) for r in regions}
    marked = {coords(r, i) for r, cs in loaded.items() for i, (_, raw, t) in cs.items() if MARK in payload(raw, t)}
    if not marked:
        sys.exit("no fishtastic blocks found in any region file")
    kept = 0
    for r, cs in loaded.items():
        keep = {i: c for i, c in cs.items()
                if any(abs(coords(r, i)[0] - x) <= radius and abs(coords(r, i)[1] - z) <= radius for x, z in marked)}
        kept += len(keep)
        # Entity and POI regions follow the same chunk filter as the terrain they belong to.
        for sib in ("entities", "poi"):
            s = r.parent.parent / sib / r.name
            if s.exists():
                sc = read_chunks(s)
                sk = {i: c for i, c in sc.items() if i in keep}
                if sk:
                    write_chunks(s, sk)
                else:
                    s.unlink()
        if keep:
            write_chunks(r, keep)
        else:
            r.unlink()
    print(f"fishtastic chunks {sorted(marked)}; kept {kept} chunks (radius {radius})")


if __name__ == "__main__":
    main(*sys.argv[1:3], *(int(x) for x in sys.argv[3:4]))
