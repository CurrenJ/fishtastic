"""
Analyses the scalebench self-test (RenderSelfTest.queueScaleBenchScene). Each scene was measured
in paired rounds, arm A then B or B then A (the order alternates), so every round gives one
difference B - A in mean frame, CPU and GPU time. Reported per scene: the A level, and the mean
difference with a 95% confidence interval from the round-to-round spread (Student t, n - 1 df).
A difference whose interval spans zero is not distinguishable from noise at this sample size.

Then, across the scenes of one run, the B - A cost is fitted against the scene's cosmetic quads
(mesh runs) or its floor-structure parts (scan runs), with the slope's 95% interval.

    python tools/perfbench/scale_analyze.py <bench dir> <game log> [--arms none,mesh | scan... ]
"""
import csv
import glob
import math
import os
import re
import sys
from collections import defaultdict

NAME = re.compile(r"scale_(?P<loader>[a-z]+)_(?P<label>[a-z0-9]+)_(?P<name>[a-z0-9_]+?)_(?P<count>\d+)_(?P<arm>none|mesh|scan|noscan)_r(?P<round>\d+)\.csv$")
RESULT = re.compile(r"\[scale\] RESULT scale_(?P<loader>[a-z]+)_(?P<label>[a-z0-9]+)_(?P<name>[a-z0-9_]+?)_(?P<count>\d+)_(?P<arm>[a-z]+)_r\d+ tanks=(?P<tanks>\d+) scene_quads=(?P<quads>\d+)")

# Student t, two-sided 95%, by degrees of freedom.
T95 = {1: 12.706, 2: 4.303, 3: 3.182, 4: 2.776, 5: 2.571, 6: 2.447, 7: 2.365, 8: 2.306, 9: 2.262, 10: 2.228,
       15: 2.131, 20: 2.086, 30: 2.042}


def t95(df):
    if df in T95:
        return T95[df]
    keys = sorted(k for k in T95 if k <= df)
    return T95[keys[-1]] if keys else 12.706


def mean(v):
    return sum(v) / len(v)


def ci(diffs):
    n = len(diffs)
    m = mean(diffs)
    if n < 2:
        return m, float("nan")
    sd = math.sqrt(sum((d - m) ** 2 for d in diffs) / (n - 1))
    return m, t95(n - 1) * sd / math.sqrt(n)


def load(bench_dir):
    rounds = defaultdict(lambda: defaultdict(dict))   # scene -> round -> arm -> (frame, cpu, gpu)
    for path in glob.glob(os.path.join(bench_dir, "scale_*.csv")):
        m = NAME.search(os.path.basename(path))
        if not m:
            continue
        fr, cpu, gpu = [], [], []
        with open(path) as f:
            for row in csv.DictReader(f):
                fr.append(int(row["frame_ns"]) / 1e6)
                cpu.append(int(row["cpu_ns"]) / 1e6)
                g = int(row["gpu_ns"])
                if g > 0:
                    gpu.append(g / 1e6)
        scene = (m["loader"], m["label"], m["name"], int(m["count"]))
        rounds[scene][int(m["round"])][m["arm"]] = (mean(fr), mean(cpu), mean(gpu) if gpu else float("nan"))
    return rounds


def scene_info(log):
    info = {}
    for line in open(log, encoding="utf-8", errors="replace"):
        m = RESULT.search(line)
        if m:
            info[(m["loader"], m["label"], m["name"], int(m["count"]))] = (int(m["tanks"]), int(m["quads"]))
    return info


def fit(xs, ys):
    n = len(xs)
    mx, my = mean(xs), mean(ys)
    sxx = sum((x - mx) ** 2 for x in xs)
    slope = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sxx
    icpt = my - slope * mx
    resid = [y - (icpt + slope * x) for x, y in zip(xs, ys)]
    s2 = sum(r * r for r in resid) / (n - 2) if n > 2 else float("nan")
    se = math.sqrt(s2 / sxx) if n > 2 else float("nan")
    ss_tot = sum((y - my) ** 2 for y in ys)
    r2 = 1 - sum(r * r for r in resid) / ss_tot if ss_tot else float("nan")
    return slope, t95(n - 2) * se if n > 2 else float("nan"), icpt, r2


def main():
    bench_dir, log = sys.argv[1], sys.argv[2]
    rounds = load(bench_dir)
    info = scene_info(log)
    by_run = defaultdict(list)
    for scene in sorted(rounds, key=lambda s: (s[0], s[1], s[2], s[3])):
        arms = set()
        for r in rounds[scene].values():
            arms |= set(r)
        a, b = ("none", "mesh") if "mesh" in arms else ("noscan", "scan")
        pairs = [(r[a], r[b]) for r in rounds[scene].values() if a in r and b in r]
        if not pairs:
            continue
        tanks, quads = info.get(scene, (0, 0))
        row = {"scene": scene, "a": a, "b": b, "n": len(pairs), "tanks": tanks, "quads": quads}
        for k, label in enumerate(("frame", "cpu", "gpu")):
            row[label + "_a"] = mean([p[0][k] for p in pairs])
            row[label + "_d"], row[label + "_ci"] = ci([p[1][k] - p[0][k] for p in pairs])
            diffs = sorted(p[1][k] - p[0][k] for p in pairs)
            row[label + "_med"] = diffs[len(diffs) // 2] if len(diffs) % 2 else (diffs[len(diffs) // 2 - 1] + diffs[len(diffs) // 2]) / 2
            row[label + "_neg"] = sum(1 for d in diffs if d < 0)
        by_run[(scene[0], scene[1])].append(row)

    for (loader, label), rows in by_run.items():
        a, b = rows[0]["a"], rows[0]["b"]
        print(f"\n### {loader} / {label}: {b} - {a}, mean of per-round differences, 95% CI (ms)\n")
        print(f"| structure | copies | tanks | scene quads | rounds | frame {a} | frame {b}-{a} | CPU {a} | CPU {b}-{a} | GPU {a} | GPU {b}-{a} |")
        print("|---|---|---|---|---|---|---|---|---|---|---|")
        for r in rows:
            def cell(k):
                return f"{r[k + '_d']:+.3f} ± {r[k + '_ci']:.3f}"
            print(f"| {r['scene'][2]} | {r['scene'][3]} | {r['tanks']} | {r['quads']} | {r['n']} | {r['frame_a']:.3f} | {cell('frame')} | "
                  f"{r['cpu_a']:.3f} | {cell('cpu')} | {r['gpu_a']:.3f} | {cell('gpu')} |")
        print()
        print(f"GPU {b}-{a}, robust: median of per-round differences, and rounds where {b} was faster")
        for r in rows:
            print(f"  {r['scene'][2]} x{r['scene'][3]}: median {r['gpu_med']:+.3f} ms, {r['gpu_neg']}/{r['n']} rounds faster")
        if a == "none":
            xs = [r["quads"] / 1000 for r in rows]
            for k in ("frame", "cpu", "gpu"):
                ys = [r[k + "_d"] for r in rows]
                if len(xs) > 2 and len(set(xs)) > 1:
                    s, sci, icpt, r2 = fit(xs, ys)
                    print(f"\n{k}: {b}-{a} ~ {icpt:+.4f} ms + ({s * 1000:+.3f} ± {sci * 1000:.3f}) µs per 1000 quads, R² {r2:.2f}")


if __name__ == "__main__":
    main()
