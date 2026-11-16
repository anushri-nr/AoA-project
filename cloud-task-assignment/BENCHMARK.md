# Reproducible Cloud Task Assignment Evaluation

`src/main/java/CloudTaskBenchmark.java` is a dependency-free Java 17 implementation and benchmark of the maximum-flow reduction. It requires no external libraries. Tasks occupy one slot, eligibility is the only placement restriction, and each server has a nonnegative integer slot capacity. Dinic uses residual edges, BFS levels, and an iterative current-arc DFS to compute blocking flows without risking recursion-stack overflow. The extracted assignment maps task indices to server indices, with `-1` denoting an unassigned task.

## Build and correctness checks

Run from this module directory (or build all modules with `mvn package` from the repository root):

```sh
mvn package
java -jar target/cloud-task-assignment-1.0-SNAPSHOT.jar --self-test
```

The self-test checks the five-task example (optimal value 5) and compares Dinic with an independent exhaustive assignment optimizer on 672 tiny instances. These cover zero tasks, zero servers, zero capacities, densities 0, 0.5, and 1, and all three capacity distributions. The worked example also requires reversing earlier assignments with the implementation's deterministic edge order. Every benchmark invocation validates eligibility, server loads, task uniqueness, and flow/assignment cardinality outside timed intervals. Repeated runs must produce the same flow value. These checks supplement the mathematical proof; they do not replace it.

## Experimental design

The default factorial experiment crosses all of the following settings:

| Factor | Default values |
|---|---|
| Task count n | 100, 500, 1000 |
| Server count m | 25, 100 |
| Eligibility probability p | 0.01, 0.1, 0.5 |
| Capacity distribution | FIXED, UNIFORM, SKEWED |
| Base capacity c | 4 |
| Instance seeds | 11, 29, 47 |
| Discarded warm-up runs per instance | 5 |
| Measured repetitions per instance | 9 |

This gives 162 distinct parameter/seed cases, each with 14 fresh network executions. Every potential task-server pair is independently included when `SplittableRandom.nextDouble() < p`. Thus r is observed rather than forced to equal pnm. Generation scans all nm pairs, takes O(nm + m) time, and is excluded from algorithm timing because the algorithm accepts an already specified eligibility relation.

Capacities are sampled independently across servers, using a separate random stream:

- FIXED: C = c.
- UNIFORM: C is discrete uniform on {0, ..., 2c}.
- SKEWED: C = 10c with probability 0.1 and C = 0 otherwise.

All distributions have expected capacity c per server. Their realized total capacities generally differ; CSV files report the actual sum, minimum, and maximum. The skewed model represents a platform where only a small fraction of servers have available task slots. It intentionally includes zero-capacity servers. Distribution effects therefore include both capacity concentration and realized capacity variation. Equal expected capacity does not establish a controlled equal-total-capacity comparison.

For fixed (n,m,p,seed), all distributions and base capacities use exactly the same eligibility edges. Eligibility and capacity draws use separate fixed streams, and task/server edge order is deterministic. Parameter cases are shuffled with fixed order seed 20261115. This reduces systematic size-order bias, although it cannot eliminate temporal machine effects. Keep the same Java version and command arguments for exact input reproducibility.

## Execution

Run the default experiment:

```sh
java -Xms512m -Xmx512m -jar target/cloud-task-assignment-1.0-SNAPSHOT.jar --out=results/default
```

For a publication experiment, use at least three independent JVM processes, each writing to a different directory:

```sh
java -Xms512m -Xmx512m -jar target/cloud-task-assignment-1.0-SNAPSHOT.jar --fork=1 --out=results/fork1
java -Xms512m -Xmx512m -jar target/cloud-task-assignment-1.0-SNAPSHOT.jar --fork=2 --out=results/fork2
java -Xms512m -Xmx512m -jar target/cloud-task-assignment-1.0-SNAPSHOT.jar --fork=3 --out=results/fork3
```

The fork label records process replication; it does not alter input seeds. Run these processes sequentially. Five warm-up runs are a reproducible starting protocol, not a guarantee that JIT compilation has stabilized. Inspect raw repetition timings and repeat with larger warm-up counts (for example 20 or 50) to check sensitivity before reporting conclusions. Use more repetitions for noisy or very short cases. For workloads near clock/read overhead, prefer larger cases and report the limitation; do not subtract an assumed constant overhead.

All options use `--name=value`; list options are comma-separated:

```sh
java -Xms1g -Xmx1g -jar target/cloud-task-assignment-1.0-SNAPSHOT.jar --tasks=250,500,1000,2000 --servers=50,100,200 --densities=0.01,0.1,0.5 --capacities=1,4,16 --distributions=FIXED,UNIFORM,SKEWED --seeds=11,29,47 --warmup=20 --runs=15 --order-seed=20261115 --fork=1 --out=results/extended
```

This larger command is an optional experiment, not a claim that it has been executed. Reusing an output directory overwrites its three output files. Inputs must fit available heap memory. Record source revision or source checksum alongside outputs.

## Timing protocol and exports

For each case, generate the instance once. Each discarded or measured repetition constructs a new network, runs Dinic, and extracts an assignment. Reusing a solved residual graph would invalidate timings and is deliberately avoided.

Four `System.nanoTime()` readings define consecutive intervals:

1. Construction: allocate adjacency lists, residual edge pairs, extraction references, and Dinic work arrays; insert all network edges.
2. Dinic: compute maximum flow, including BFS and blocking-flow phases.
3. Extraction: allocate and populate the task-to-server assignment array.
4. Total: elapsed time from immediately before construction to immediately after extraction.

For each repetition, total equals construction + Dinic + extraction exactly. Total excludes graph generation, parameter parsing, validation, CSV formatting/writing, and metadata collection. Dinic work arrays are allocated during construction, not during the flow interval. This boundary must remain consistent in comparisons. Validation runs between measured executions can affect cache state, but its duration is excluded. A volatile checksum consumes the validated output.

No timed region performs console or file I/O. No explicit `System.gc()` is used. Allocation and garbage-collection pauses that occur within intervals remain included: these are part of the measured JVM execution environment. Sequential runs share one JVM per invocation; fork replication checks process-level variability.

Outputs:

- `raw.csv`: one row per measured repetition, with construction, Dinic, extraction, and total nanoseconds and maximum flow.
- `summary.csv`: one row per parameter/seed case, containing the median of each timing component across measured repetitions. Even-sized samples use the integer midpoint of the two central values. Component medians need not sum to the median total.
- `metadata.txt`: command arguments, Java/VM version, JVM options, OS version and architecture, available processors, maximum heap, order seed, fork identifier, and case count.

Both CSV files include n, m, requested p, distribution, base capacity, seed, realized r and density, realized capacity sum/minimum/maximum, V, E, the numerical V²E work proxy, fork, case order, warm-up count, measured repetition count, and flow value. `raw.csv` additionally identifies the measured repetition. Together with the generator, these parameters reproduce each input; timing values themselves are not deterministic.

Record CPU model, installed RAM, power mode, collector, OS load, and source revision separately if publishing results. Use a quiet machine on external power, fixed JVM/heap settings, and the same environment for comparisons. Do not run benchmark forks concurrently. Preserve raw timings rather than reporting only a favorable run.

## Theoretical analysis

Let r = |R|. The original network has

    V = n + m + 2,     E = n + m + r.

Its residual representation stores 2E edge records. Construction costs O(n+m+r); extraction initializes n outputs and scans r eligibility edges, costing O(n+r). Dinic's general worst-case bound is O(V²E): fewer than V blocking-flow phases, each taking O(VE), with BFS within that bound. Current-edge pointers prevent repeatedly scanning failed edges in a phase. The iterative DFS implements the same level-respecting traversal as recursive DFS.

Consequently, the complete assignment algorithm has upper bound

    O(n+m+r) + O((n+m+2)²(n+m+r)) + O(n+r)
      = O((n+m+2)²(n+m+r)).

This is a worst-case upper bound, not an exact runtime prediction or a claim of tightness for this network family. Auxiliary and network storage is O(V+E) = O(n+m+r) machine words, including the residual representation, DFS path arrays, and assignment output. Capacity arithmetic uses Java long residual values; capacities are validated bounded int values and total flow is at most n. Input generation has a separate O(nm+m) cost and is outside the measured algorithm.

Dinic starts with feasible zero flow. Every augmentation respects residual capacities and flow conservation and is integral. A blocking flow eliminates all paths at the current shortest residual distance; subsequent successful BFS phases strictly increase that distance, proving termination. On termination there is no residual source-to-sink path, so the flow is maximum. Source-to-task capacities enforce task uniqueness, task-to-server edges enforce eligibility, and server-to-sink capacities enforce server limits. Each integral unit of flow corresponds to one assignment, and every feasible assignment induces a flow of the same cardinality. Thus extraction returns an optimal assignment.

## Comparing empirical scaling with the bound

Use measured r, rather than substituting pnm, to calculate V²E for each instance. Plot median Dinic and total times against n, m, and r with other factors held fixed. Also plot time against V²E on logarithmic axes, and inspect time/(V²E) as a descriptive normalization. V²E is an operation-count proxy without a specified constant, not an upper bound in nanoseconds.

Useful additional sweeps include varying n at fixed m,p; varying m at fixed n,p; varying p at fixed n,m; and varying distribution or c on paired eligibility graphs. When n and m both scale as q and p is fixed and positive, expected r is proportional to q² and the general bound scales as O(q⁴). If r is instead proportional to q, that expression becomes O(q³). These statements describe the bound under the specified scaling regimes, not expected measured running times.

Report per-seed medians and show variation across seeds and forks, for example ranges or quartiles. Do not treat repeated runs of one graph as independent graph samples. Stratify comparisons by capacity distribution and consider flow/n and flow/min(n,sum capacities), defining the latter only for a positive denominator. A timing change can arise from different attainable flow or bottlenecks as well as graph size.

Finite random-input measurements cannot prove the asymptotic worst-case bound, identify the worst-case family, or establish a matching lower bound. Faster observed scaling is compatible with O(V²E). Any fitted exponent describes only the sampled parameter range and distribution. Reproducible inputs, raw timings, and documented environment make that empirical statement inspectable without overstating it.
