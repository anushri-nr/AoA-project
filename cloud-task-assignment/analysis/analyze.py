#!/usr/bin/env python3
"""Reproduce all figures from existing measurements; never runs the benchmark.

Usage: python3 analysis/analyze.py [--input results/default] [--output analysis]
Requires Python 3, numpy, pandas, matplotlib. Paths are relative to the caller.
"""
import argparse
import hashlib
import json
import platform
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import ScalarFormatter
import numpy as np
import pandas as pd

COLORS = ["#0072B2", "#D55E00", "#009E73"]
MARKERS = ["o", "s", "^"]
METRICS = ["construction", "dinic", "extraction", "total"]


def load_and_validate(directory):
    summary = pd.read_csv(directory / "summary.csv")
    raw = pd.read_csv(directory / "raw.csv")
    keys = ["fork", "case_order"]
    assert not summary.duplicated(keys).any(), "Nonunique summary cases"
    assert not raw.duplicated(keys + ["run"]).any(), "Duplicate repetitions"
    assert (summary.V == summary.n + summary.m + 2).all()
    assert (summary.E == summary.n + summary.m + summary.r).all()
    assert np.allclose(summary.V2E, summary.V.astype(float)**2 * summary.E)
    assert (raw.total_ns == raw.construction_ns + raw.dinic_ns + raw.extraction_ns).all()
    assert (raw[[f"{metric}_ns" for metric in METRICS]] >= 0).all().all()
    assert (summary.max_flow <= np.minimum(summary.n, summary.capacity_sum)).all()
    checked = summary.merge(raw.groupby(keys).size().rename("observed_runs"), on=keys, validate="one_to_one")
    assert (checked.measured_runs == checked.observed_runs).all()
    assert len(checked) == len(summary)
    for metric in METRICS:
        med = raw.groupby(keys)[metric + "_ns"].median().rename("recomputed")
        check = summary.merge(med, on=keys, validate="one_to_one")
        assert np.allclose(check["median_" + metric + "_ns"], np.floor(check.recomputed), rtol=0, atol=0)
        summary[metric + "_ms"] = summary["median_" + metric + "_ns"] / 1e6
    flow = raw.groupby(keys).max_flow.agg(["min", "max"])
    check = summary.merge(flow, on=keys, validate="one_to_one")
    assert (check["min"] == check["max"]).all() and (check["min"] == check.max_flow).all()
    return summary, raw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=Path("results/default"))
    parser.add_argument("--output", type=Path, default=Path("analysis"))
    args = parser.parse_args()
    out = args.output
    figures = out / "output" / "pdf"
    previews = out / "previews"
    figures.mkdir(parents=True, exist_ok=True)
    previews.mkdir(parents=True, exist_ok=True)
    df, raw = load_and_validate(args.input)
    # The plotted controlled slices are chosen before examining their timing trends.
    fixed = df[(df.distribution == "FIXED") & (df.base_capacity == 4)]
    tasks = fixed[fixed.m == 100]
    servers = fixed[fixed.n == 1000]
    density = df[(df.n == 1000) & (df.m == 100) & (df.base_capacity == 4)]
    for data in [tasks, servers, density]:
        assert len(data) > 0, "Required controlled slice absent"
    assert set(df.fork) == {1}, "Revise aggregation for multiple JVM forks"

    plt.rcParams.update({
        "font.family": "serif", "font.serif": ["DejaVu Serif"],
        "font.size": 8, "axes.labelsize": 9, "axes.titlesize": 9,
        "legend.fontsize": 7, "xtick.labelsize": 8, "ytick.labelsize": 8,
        "lines.linewidth": 1.1, "lines.markersize": 4,
        "axes.spines.top": False, "axes.spines.right": False,
        "pdf.fonttype": 42, "ps.fonttype": 42, "savefig.dpi": 300,
    })
    plotted = []

    def style(ax, xlabel, ylabel="Runtime (ms)", title=None, logx=False):
        ax.set_xlabel(xlabel)
        ax.set_ylabel(ylabel)
        ax.set_yscale("log")
        if logx:
            ax.set_xscale("log")
        ax.grid(True, which="major", color="0.88", linewidth=.5)
        ax.set_axisbelow(True)
        if title:
            ax.set_title(title)

    def curve(ax, data, x, y, label, color, marker, figid):
        grouped = data.groupby(x)[y].agg(["median", "min", "max"]).sort_index()
        # The bars are the full range of three graph-seed medians, not confidence intervals.
        ax.errorbar(grouped.index, grouped["median"],
                    yerr=np.vstack([grouped["median"] - grouped["min"], grouped["max"] - grouped["median"]]),
                    color=color, marker=marker, capsize=2, label=label)
        for xv, row in grouped.iterrows():
            plotted.append(dict(figure=figid, series=label, x_name=x, x=xv,
                                metric=y, median=row["median"], minimum=row["min"], maximum=row["max"],
                                graph_seeds=int((data[x] == xv).sum())))

    def save(fig, name):
        fig.savefig(figures / (name + ".pdf"), bbox_inches="tight", pad_inches=.035)
        fig.savefig(previews / (name + ".png"), bbox_inches="tight", pad_inches=.035)
        plt.close(fig)

    # 1. Separate algorithm-only and total time; p is controlled within each series.
    fig, axes = plt.subplots(1, 2, figsize=(7.16, 2.65), layout="constrained")
    for ax, metric, title in zip(axes, ["total_ms", "dinic_ms"], ["(a) Complete assignment", "(b) Dinic"]):
        for k, (p, data) in enumerate(tasks.groupby("p")):
            curve(ax, data, "n", metric, f"p = {p:g}", COLORS[k], MARKERS[k], "01_tasks")
        style(ax, "Number of tasks, n", title=title, logx=True)
        ax.set_xticks([100, 500, 1000], ["100", "500", "1000"])
    axes[0].legend(loc="upper left")
    save(fig, "01_runtime_vs_tasks")

    # 2. Actual edge counts, with all 3 seeds shown individually. r varies through p.
    fig, axes = plt.subplots(1, 2, figsize=(7.16, 2.65), layout="constrained")
    for ax, metric, title in zip(axes, ["total_ms", "dinic_ms"], ["(a) Complete assignment", "(b) Dinic"]):
        for k, (dist, data) in enumerate(density.groupby("distribution", sort=True)):
            ax.scatter(data.r, data[metric], label=dist.capitalize(), color=COLORS[k], marker=MARKERS[k], s=20)
            for _, row in data.iterrows():
                plotted.append(dict(figure="02_edges", series=dist, x_name="r", x=row.r,
                                    metric=metric, median=row[metric], minimum=row[metric], maximum=row[metric], graph_seeds=1))
        style(ax, "Eligibility edges, r", title=title, logx=True)
    axes[0].legend(loc="upper left")
    save(fig, "02_runtime_vs_edges")

    # 3. Only two server counts exist. Connect measured endpoints without fitting.
    fig, axes = plt.subplots(1, 2, figsize=(7.16, 2.65), layout="constrained")
    for ax, metric, title in zip(axes, ["total_ms", "dinic_ms"], ["(a) Complete assignment", "(b) Dinic"]):
        for k, (p, data) in enumerate(servers.groupby("p")):
            curve(ax, data, "m", metric, f"p = {p:g}", COLORS[k], MARKERS[k], "03_servers")
        style(ax, "Number of servers, m", title=title)
        ax.set_xticks([25, 100])
        ax.set_xlim(20, 105)
    axes[0].legend(loc="upper left")
    save(fig, "03_runtime_vs_servers")

    # 4. Fixed n,m; realized density is available in the source, p is the generator input.
    fig, axes = plt.subplots(1, 2, figsize=(7.16, 2.65), layout="constrained")
    for ax, metric, title in zip(axes, ["total_ms", "dinic_ms"], ["(a) Complete assignment", "(b) Dinic"]):
        for k, (dist, data) in enumerate(density.groupby("distribution", sort=True)):
            curve(ax, data, "p", metric, dist.capitalize(), COLORS[k], MARKERS[k], "04_density")
        style(ax, "Eligibility probability, p", title=title, logx=True)
        ax.set_xticks([.01, .1, .5], ["0.01", "0.1", "0.5"])
    axes[0].legend(loc="upper left")
    save(fig, "04_runtime_vs_density")

    # 5. Side-by-side component comparison for each capacity distribution.
    fig, axes = plt.subplots(1, 3, figsize=(7.16, 2.65), layout="constrained", sharey=True)
    for ax, (dist, data) in zip(axes, density.groupby("distribution", sort=True)):
        for k, (metric, label) in enumerate([("construction_ms", "Construction"), ("dinic_ms", "Dinic")]):
            curve(ax, data, "p", metric, label, COLORS[k], MARKERS[k], "05_components_" + dist)
        style(ax, "Eligibility probability, p", title=dist.capitalize(), logx=True)
        ax.set_xticks([.01, .1, .5], ["0.01", "0.1", "0.5"])
    axes[0].legend(loc="upper left")
    for ax in axes[1:]:
        ax.set_ylabel("")
    save(fig, "05_construction_vs_dinic")

    # 6. Compare normalized observed growth with the operation-count expression.
    # Every point uses measured time; no curve is presented as theoretical time.
    compare = tasks[tasks.p == .1].groupby("n").agg(
        total_ms=("total_ms", "median"), dinic_ms=("dinic_ms", "median"),
        V2E=("V2E", "median")).sort_index()
    ratios = compare / compare.iloc[0]
    ratios.to_csv(out / "growth_comparison.csv", index_label="n")
    fig, ax = plt.subplots(figsize=(3.5, 2.65), layout="constrained")
    for k, (metric, label) in enumerate([("total_ms", "Measured total"), ("dinic_ms", "Measured Dinic")]):
        ax.plot(ratios.index, ratios[metric], marker=MARKERS[k], color=COLORS[k], label=label)
    style(ax, "Number of tasks, n", "Observed time / time at n = 100", logx=True)
    ax.set_xticks([100, 500, 1000], ["100", "500", "1000"])
    ax.legend(loc="upper left")
    save(fig, "06_observed_growth")

    pd.DataFrame(plotted).to_csv(out / "plotted_values.csv", index=False)
    aggregate = df.groupby(["n", "m", "p", "distribution", "base_capacity"])[
        ["construction_ms", "dinic_ms", "extraction_ms", "total_ms", "r", "max_flow", "capacity_sum", "V2E"]
    ].agg(["median", "min", "max"])
    aggregate.to_csv(out / "aggregated_measurements.csv")
    shares = raw.assign(construction_share=raw.construction_ns / raw.total_ns,
                        dinic_share=raw.dinic_ns / raw.total_ns,
                        extraction_share=raw.extraction_ns / raw.total_ns)
    provenance = {
        "source": str(args.input.resolve()), "summary_cases": len(df), "raw_runs": len(raw),
        "python": platform.python_version(), "matplotlib": matplotlib.__version__,
        "numpy": np.__version__, "pandas": pd.__version__,
        "sha256": {name: hashlib.sha256((args.input / name).read_bytes()).hexdigest()
                   for name in ["summary.csv", "raw.csv", "metadata.txt"]},
        "median_raw_component_shares": {x: float(shares[x].median()) for x in
            ["construction_share", "dinic_share", "extraction_share"]},
        "aggregation": "Median of per-instance medians across three seeds; bars show full seed range, not CI.",
    }
    (out / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print(json.dumps(provenance, indent=2))
    print("Normalized growth:\n", ratios.to_string())


if __name__ == "__main__":
    main()
