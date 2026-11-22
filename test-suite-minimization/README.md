# testmin: Regression Test-Suite Minimization

Given tests that each cover a set of requirements, choose the fewest tests that
still cover every requirement. The decision version is NP-complete (reduction
from Vertex Cover); this project implements a greedy heuristic, an exact
baseline, the reduction, and benchmarks.

## Layout

| Package | Contents |
|---|---|
| `testmin.model` | `TestCase`, `Instance`, `Solution` (immutable, validated) |
| `testmin.algo` | `Minimizer` interface, `GreedyMinimizer`, `ExactMinimizer` (branch and bound) |
| `testmin.verify` | `CoverageVerifier`: polynomial-time certificate check (NP membership) |
| `testmin.reduction` | `Graph`, `VertexCoverReduction` (VC ≤p test-suite minimization) |
| `testmin.gen` | `InstanceGenerator`: seeded, always-feasible random instances |
| `testmin.bench` | `RuntimeBenchmark`, `QualityBenchmark` → CSV in `results/` |
| `scripts/plot.py` | Figures from the CSVs |

## Running

Requires Java 17+ and Maven.

```bash
mvn test                                  
mvn -q compile
java -cp target/classes testmin.bench.RuntimeBenchmark results
java -cp target/classes testmin.bench.QualityBenchmark results
python3 scripts/plot.py results            
```

## Complexity

* Greedy: O(k·m + N + n). Here m is the number of tests, n the number of requirements,
  N the total coverage size and k the number of tests selected.
  Approximation ratio ≤ H(d) ≤ ln d + 1, where d is the largest test size.
* Verifier: O(m + n + N).
* Reduction: O(|V| + |E|).
* Exact: exponential worst case; small instances only.

## Results summary (from the included run)

* Runtime: fitted model `time ≈ 0.18 ns·(k·m) + 3.2 ns·(N + n)` (two constants
  fitted to the same 18 points). R² = 0.991 on absolute time, 0.93 on a log
  scale (sparse 0.69, dense 0.98). The sparse family's ratio of measured time to
  the model is 0.99–1.07 from m = 4000 up, but higher on small inputs, where fixed
  overheads dominate. The dense family's ratio stays at about 0.65–0.75 up to
  m = 6000, then rises to 1.06, possibly due to cache effects (not measured). The
  measurements are consistent with O(k·m + N + n) at larger sizes.
* Quality: on 630 small instances, greedy was optimal 61% of the time. The mean
  ratio to the optimum was 1.05 and the worst was 1.43, far below the theoretical
  H(d) bound (≥ 2.28).
