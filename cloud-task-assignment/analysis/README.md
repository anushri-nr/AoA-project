# Benchmark analysis and LaTeX figures

This analysis uses **only** the pre-existing `results/default` measurements. The smoke-test data are excluded. No new timing measurements or synthetic timing curves are added. Source file SHA-256 hashes and Python package versions are saved in `provenance.json`.

From `cloud-task-assignment/`, reproduce the outputs with:

```sh
python3 analysis/analyze.py --input=results/default --output=analysis
```

Dependencies: Python 3, NumPy, pandas, and Matplotlib. The script validates timing sums, flow bounds, graph sizes, repetition counts, and reported medians against raw records before plotting. It expects the default parameter grid and one JVM fork; changing the input design requires revising the controlled slices and the written results.

## Deliverables

- `experimental_results.tex`: complete IEEE-style experimental results section, including six figure environments and captions.
- `output/pdf/01_runtime_vs_tasks.pdf`: total and Dinic runtime, controlled at m=100 and fixed capacity four, with separate p series.
- `output/pdf/02_runtime_vs_edges.pdf`: individual instance medians against actual eligibility edge count, controlled at n=1000 and m=100.
- `output/pdf/03_runtime_vs_servers.pdf`: controlled at n=1000 and fixed per-server capacity four, with separate p series.
- `output/pdf/04_runtime_vs_density.pdf`: controlled at n=1000 and m=100, separated by capacity distribution.
- `output/pdf/05_construction_vs_dinic.pdf`: component times by density and capacity distribution.
- `output/pdf/06_observed_growth.pdf`: observed growth factors for the controlled task-count slice discussed alongside the upper-bound expression.
- `plotted_values.csv`, `aggregated_measurements.csv`, and `growth_comparison.csv`: numerical values supporting plots and reported comparisons.
- `previews/`: PNG previews for convenient inspection; use the vector PDFs in the paper.

Figures 1--5 are designed at approximately IEEE two-column width (7.16 inches); figure 6 is designed at single-column width (3.5 inches). They use embedded TrueType fonts, vector paths, colorblind-friendly colors, distinct markers, axis units, and shared scales where appropriate. Captions are in LaTeX rather than embedded inside the graphics so the manuscript controls caption typography.

To integrate the results, copy `experimental_results.tex` and `output/` beside the main LaTeX manuscript, load `graphicx`, and add:

```tex
\input{experimental_results.tex}
```

Alternatively, adjust the figure paths to your repository layout. The supplied file is an include fragment, not a standalone manuscript. It has not been compiled as an IEEE paper in this environment.

The experimental section now cites the Java timing documentation and Java benchmarking literature. When using it independently, copy the corresponding inline bibliography entries from `../../report/main.tex` into your manuscript bibliography. The complete report in `../../report/main.pdf` has been compiled with these citations resolved.

Each plotted central value is the median of three graph-seed medians, each based on nine measured repetitions. Error bars show the complete range across those three seed medians, not uncertainty bounds or confidence intervals. The edge-count scatter shows each seed separately because realized r varies by seed. Connecting lines in other plots join measured points only. Component medians and median fractions are computed independently and are not additive.

The timings are from one JVM fork, so these plots are descriptive. They cannot establish the asymptotic worst case, hardware-independent throughput, or causal effects of GC/JIT/cache behavior. The theoretical general upper bound remains O((n+m+2)^2(n+m+r)); the numerical V²E expression is an operation-count proxy, not a bound measured in milliseconds.
