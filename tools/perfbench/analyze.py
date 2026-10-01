"""
Summarises the perfbench self-test (RenderSelfTest.queuePerfBenchScene): reads every
<run>/fishtastic_bench/<loader>_<label>_<scene>_<mode>_r<N>.csv, pools rounds, and prints per
(loader, label, scene, mode): mean / p50 / p95 / p99 frame time, mean CPU and GPU frame time, and
the cost above the no-cosmetics baseline. With --plot, also writes frame-time CDFs per scene.

    python tools/perfbench/analyze.py fabric/run/fishtastic_bench [neoforge/run/fishtastic_bench ...] [--plot out_dir]
"""
import csv
import glob
import os
import re
import sys
from collections import defaultdict

NAME = re.compile(r"(?P<loader>[a-z]+)_(?P<label>[a-z0-9]+)_(?P<scene>[a-z]+)_(?P<mode>mesh|per_frame|none)_r(?P<round>\d+)\.csv$")


def load(dirs):
    data = defaultdict(lambda: {"frame": [], "cpu": [], "gpu": [], "rounds": []})
    for d in dirs:
        for path in glob.glob(os.path.join(d, "*.csv")):
            m = NAME.search(os.path.basename(path))
            if not m:
                continue
            key = (m["loader"], m["label"], m["scene"], m["mode"])
            frames = []
            with open(path) as f:
                for row in csv.DictReader(f):
                    fr, cpu, gpu = int(row["frame_ns"]), int(row["cpu_ns"]), int(row["gpu_ns"])
                    data[key]["frame"].append(fr / 1e6)
                    data[key]["cpu"].append(cpu / 1e6)
                    if gpu > 0:
                        data[key]["gpu"].append(gpu / 1e6)
                    frames.append(fr / 1e6)
            data[key]["rounds"].append(sum(frames) / max(1, len(frames)))
    return data


def pct(values, p):
    if not values:
        return float("nan")
    s = sorted(values)
    return s[min(len(s) - 1, int(len(s) * p / 100))]


def mean(values):
    return sum(values) / len(values) if values else float("nan")


def main():
    args = sys.argv[1:]
    plot_dir = None
    if "--plot" in args:
        i = args.index("--plot")
        plot_dir = args[i + 1]
        args = args[:i] + args[i + 2:]
    data = load(args)
    groups = defaultdict(dict)
    for (loader, label, scene, mode), v in data.items():
        groups[(loader, label, scene)][mode] = v

    print("| loader | shaders | scene | path | fps | frame mean | p50 | p95 | p99 | CPU mean | GPU mean | cost vs none (mean) | round means |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for (loader, label, scene) in sorted(groups):
        modes = groups[(loader, label, scene)]
        base = mean(modes["none"]["frame"]) if "none" in modes else float("nan")
        for mode in ("none", "mesh", "per_frame"):
            if mode not in modes:
                continue
            v = modes[mode]
            fm = mean(v["frame"])
            print(f"| {loader} | {label} | {scene} | {mode} | {1000 / fm:.0f} | {fm:.3f} | {pct(v['frame'], 50):.3f} | "
                  f"{pct(v['frame'], 95):.3f} | {pct(v['frame'], 99):.3f} | {mean(v['cpu']):.3f} | {mean(v['gpu']):.3f} | "
                  f"{fm - base:+.3f} ms | {' / '.join(f'{r:.3f}' for r in v['rounds'])} |")

    if plot_dir:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        os.makedirs(plot_dir, exist_ok=True)
        for (loader, label, scene), modes in sorted(groups.items()):
            fig, axes = plt.subplots(1, 3, figsize=(15, 4))
            for ax, metric, title in zip(axes, ("frame", "cpu", "gpu"), ("frame time", "CPU render time", "GPU time")):
                for mode, color in (("none", "#888888"), ("mesh", "#2a9d8f"), ("per_frame", "#e76f51")):
                    if mode in modes and modes[mode][metric]:
                        s = sorted(modes[mode][metric])
                        ax.plot(s, [i / len(s) for i in range(len(s))], label=mode, color=color)
                ax.set_title(f"{title} (ms)")
                ax.set_xlabel("ms")
                ax.set_ylabel("fraction of frames")
                ax.grid(alpha=0.3)
                ax.legend()
            fig.suptitle(f"{loader} / {label} / {scene}")
            fig.tight_layout()
            out = os.path.join(plot_dir, f"{loader}_{label}_{scene}.png")
            fig.savefig(out, dpi=90)
            plt.close(fig)
            print("plot:", out)


if __name__ == "__main__":
    main()
