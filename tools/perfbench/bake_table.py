"""
Tabulates the bakebench self-test (RenderSelfTest.queueBakeBenchScene): one row per cosmetic
structure from its [bakebench] STRUCT log line, sorted by quads.

    python tools/perfbench/bake_table.py <latest.log> [--csv out.csv]
"""
import re
import sys

LINE = re.compile(r"\[bakebench\] STRUCT (.*)$")
LAYER = re.compile(r"\[bakebench\] LAYER (\w+) vertex_bytes=(\d+)")


def parse(path):
    rows, vertex = [], {}
    for line in open(path, encoding="utf-8", errors="replace"):
        m = LAYER.search(line)
        if m:
            vertex[m[1]] = int(m[2])
        m = LINE.search(line)
        if not m:
            continue
        rows.append(dict(kv.split("=", 1) for kv in m[1].split()))
    return rows, vertex


def main():
    rows, vertex = parse(sys.argv[1])
    vb = vertex.get("solid", 28)
    rows.sort(key=lambda r: -int(r["quads"]))
    print(f"vertex bytes per layer: {vertex}")
    print("| structure | kind | parts | tanks | quads | translucent | cutout | bake ms p50 (p10-p90) | vertex KiB |")
    print("|---|---|---|---|---|---|---|---|---|")
    for r in rows:
        q = int(r["quads"])
        print(f"| {r['name']} | {r['kind']} | {r['parts']} | {r['tanks']} | {q} | {r['translucent']} | {r['cutout']} | "
              f"{float(r['bake_ms_p50']):.2f} ({float(r['bake_ms_p10']):.2f}-{float(r['bake_ms_p90']):.2f}) | "
              f"{q * 4 * vb / 1024:.0f} |")
    if "--csv" in sys.argv:
        import csv
        out = sys.argv[sys.argv.index("--csv") + 1]
        with open(out, "w", newline="") as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
            w.writeheader()
            w.writerows(rows)
    # How well does each predictor explain bake time? Least squares on parts and on quads.
    import statistics
    for key in ("parts", "quads"):
        xs = [float(r[key]) for r in rows]
        ys = [float(r["bake_ms_p50"]) for r in rows]
        mx, my = statistics.mean(xs), statistics.mean(ys)
        sxx = sum((x - mx) ** 2 for x in xs)
        sxy = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
        slope = sxy / sxx
        icpt = my - slope * mx
        ss_res = sum((y - (icpt + slope * x)) ** 2 for x, y in zip(xs, ys))
        ss_tot = sum((y - my) ** 2 for y in ys)
        print(f"bake_ms_p50 ~ {icpt:.3f} + {slope * 1000:.4f} per 1000 {key}: R^2 = {1 - ss_res / ss_tot:.3f}")


if __name__ == "__main__":
    main()
