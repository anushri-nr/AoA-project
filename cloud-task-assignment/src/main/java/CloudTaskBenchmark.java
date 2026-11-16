import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Dependency-free Java 17 benchmark. See BENCHMARK.md for protocol and interpretation. */
public final class CloudTaskBenchmark {
    enum Distribution { FIXED, UNIFORM, SKEWED }
    record Pair(int task, int server) {}
    record Instance(int n, int m, double p, Distribution distribution, int baseCapacity,
                    long seed, List<Pair> pairs, int[] capacities) {}
    record Sample(long construction, long dinic, long extraction, long total, long flow) {}
    static volatile long blackhole;

    static final class Edge {
        final int to, reverse;
        long residual;
        Edge(int to, int reverse, long residual) {
            this.to = to; this.reverse = reverse; this.residual = residual;
        }
    }

    static final class Network {
        final ArrayList<Edge>[] adj;
        final Edge[] assignmentEdges;
        final int source, sink;
        final int[] level, next, queue, pathVertex, pathEdge;
        final long[] bottleneck;
        @SuppressWarnings("unchecked")
        Network(Instance in) {
            int vertices = Math.addExact(Math.addExact(in.n, in.m), 2);
            source = vertices - 2; sink = vertices - 1;
            adj = (ArrayList<Edge>[]) new ArrayList<?>[vertices];
            for (int v = 0; v < vertices; v++) adj[v] = new ArrayList<>();
            assignmentEdges = new Edge[in.pairs.size()];
            level = new int[vertices]; next = new int[vertices]; queue = new int[vertices];
            pathVertex = new int[vertices]; pathEdge = new int[vertices];
            bottleneck = new long[vertices];
            for (int i = 0; i < in.n; i++) add(source, i, 1);
            for (int k = 0; k < in.pairs.size(); k++) {
                Pair pair = in.pairs.get(k);
                assignmentEdges[k] = add(pair.task, in.n + pair.server, 1);
            }
            for (int j = 0; j < in.m; j++) add(in.n + j, sink, in.capacities[j]);
        }
        Edge add(int u, int v, long capacity) {
            Edge forward = new Edge(v, adj[v].size(), capacity);
            Edge reverse = new Edge(u, adj[u].size(), 0);
            adj[u].add(forward); adj[v].add(reverse);
            return forward;
        }
        boolean bfs() {
            Arrays.fill(level, -1);
            int head = 0, tail = 0;
            queue[tail++] = source; level[source] = 0;
            while (head < tail) {
                int u = queue[head++];
                for (Edge e : adj[u]) if (e.residual > 0 && level[e.to] < 0) {
                    level[e.to] = level[u] + 1;
                    queue[tail++] = e.to;
                }
            }
            return level[sink] >= 0;
        }
        // Iterative equivalent of current-arc DFS: avoids JVM recursion depth limits.
        long send(long limit) {
            int depth = 0;
            pathVertex[0] = source; bottleneck[0] = limit;
            while (true) {
                int u = pathVertex[depth];
                if (u == sink) {
                    long amount = bottleneck[depth];
                    for (int d = 0; d < depth; d++) {
                        Edge e = adj[pathVertex[d]].get(pathEdge[d]);
                        e.residual -= amount;
                        adj[e.to].get(e.reverse).residual += amount;
                    }
                    return amount;
                }
                while (next[u] < adj[u].size()) {
                    Edge e = adj[u].get(next[u]);
                    if (e.residual > 0 && level[e.to] == level[u] + 1) break;
                    next[u]++;
                }
                if (next[u] == adj[u].size()) {
                    if (depth == 0) return 0;
                    depth--;
                    next[pathVertex[depth]]++;
                } else {
                    Edge e = adj[u].get(next[u]);
                    pathEdge[depth] = next[u];
                    pathVertex[depth + 1] = e.to;
                    bottleneck[depth + 1] = Math.min(bottleneck[depth], e.residual);
                    depth++;
                }
            }
        }
        long maxFlow(int n) {
            long flow = 0;
            while (bfs()) {
                Arrays.fill(next, 0);
                long pushed;
                while ((pushed = send(n)) != 0) flow += pushed;
            }
            return flow;
        }
        int[] extract(Instance in) {
            int[] assignment = new int[in.n];
            Arrays.fill(assignment, -1);
            for (int k = 0; k < assignmentEdges.length; k++) {
                if (assignmentEdges[k].residual == 0) {
                    Pair p = in.pairs.get(k);
                    if (assignment[p.task] != -1) throw new AssertionError("Duplicate task");
                    assignment[p.task] = p.server;
                }
            }
            return assignment;
        }
    }

    static Instance generate(int n, int m, double p, Distribution d, int c, long seed) {
        // Separate streams ensure identical eligibility for all capacity distributions.
        SplittableRandom eligibility = new SplittableRandom(seed);
        SplittableRandom capacity = new SplittableRandom(seed ^ 0x6A09E667F3BCC909L);
        ArrayList<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) for (int j = 0; j < m; j++)
            if (eligibility.nextDouble() < p) pairs.add(new Pair(i, j));
        int[] capacities = new int[m];
        for (int j = 0; j < m; j++) capacities[j] = switch (d) {
            case FIXED -> c;
            case UNIFORM -> capacity.nextInt(2 * c + 1);
            case SKEWED -> capacity.nextInt(10) == 0 ? 10 * c : 0;
        };
        return new Instance(n, m, p, d, c, seed, List.copyOf(pairs), capacities);
    }

    static Sample run(Instance in) {
        long start = System.nanoTime();
        Network network = new Network(in);
        long constructed = System.nanoTime();
        long flow = network.maxFlow(in.n);
        long solved = System.nanoTime();
        int[] assignment = network.extract(in);
        long extracted = System.nanoTime();
        validate(in, assignment, flow); // Outside all reported timing intervals.
        blackhole = blackhole ^ flow ^ Arrays.hashCode(assignment);
        return new Sample(constructed - start, solved - constructed,
                          extracted - solved, extracted - start, flow);
    }

    static void validate(Instance in, int[] assignment, long flow) {
        int[] loads = new int[in.m];
        boolean[] eligible = new boolean[in.n];
        for (Pair p : in.pairs) if (assignment[p.task] == p.server) eligible[p.task] = true;
        long count = 0;
        for (int i = 0; i < in.n; i++) if (assignment[i] != -1) {
            int j = assignment[i];
            if (j < 0 || j >= in.m || !eligible[i]) throw new AssertionError("Eligibility");
            loads[j]++; count++;
        }
        for (int j = 0; j < in.m; j++)
            if (loads[j] > in.capacities[j]) throw new AssertionError("Capacity");
        if (count != flow) throw new AssertionError("Flow/assignment mismatch");
    }

    // Independent exhaustive optimizer for tiny correctness tests only.
    static int brute(Instance in, int task, int[] loads) {
        if (task == in.n) return 0;
        int best = brute(in, task + 1, loads);
        for (Pair p : in.pairs) if (p.task == task && loads[p.server] < in.capacities[p.server]) {
            loads[p.server]++;
            best = Math.max(best, 1 + brute(in, task + 1, loads));
            loads[p.server]--;
        }
        return best;
    }
    static void selfTest() {
        Instance example = new Instance(5, 3, 7.0 / 15, Distribution.FIXED, 0, 0,
            List.of(new Pair(0,0), new Pair(0,1), new Pair(1,0), new Pair(2,1),
                    new Pair(2,2), new Pair(3,1), new Pair(4,2)), new int[]{1,2,2});
        if (run(example).flow != 5) throw new AssertionError("Worked example");
        for (int n = 0; n <= 6; n++) for (int m = 0; m <= 3; m++)
            for (Distribution d : Distribution.values()) for (long seed = 0; seed < 8; seed++) {
                Instance in = generate(n, m, (seed % 3) / 2.0, d, (int)(seed % 3), seed);
                if (run(in).flow != brute(in, 0, new int[m]))
                    throw new AssertionError("Exhaustive comparison failed");
            }
        System.out.println("Self-test passed: worked example and 672 tiny instances.");
    }

    static long median(List<Sample> samples, int field) {
        long[] values = new long[samples.size()];
        for (int i = 0; i < values.length; i++) {
            Sample s = samples.get(i);
            values[i] = switch (field) {
                case 0 -> s.construction; case 1 -> s.dinic;
                case 2 -> s.extraction; default -> s.total;
            };
        }
        Arrays.sort(values);
        int mid = values.length / 2;
        return values.length % 2 == 1 ? values[mid]
            : values[mid - 1] + (values[mid] - values[mid - 1]) / 2;
    }
    static String csv(Object... fields) {
        StringJoiner joiner = new StringJoiner(",");
        for (Object field : fields) joiner.add("\"" + field.toString().replace("\"", "\"\"") + "\"");
        return joiner.toString();
    }
    static String parameters(Instance in) {
        long capacitySum = 0;
        int minimum = in.m == 0 ? 0 : Integer.MAX_VALUE, maximum = 0;
        for (int c : in.capacities) {
            capacitySum += c; minimum = Math.min(minimum, c); maximum = Math.max(maximum, c);
        }
        long vertices = (long) in.n + in.m + 2;
        long edges = (long) in.n + in.m + in.pairs.size();
        return csv(in.n, in.m, in.p, in.distribution, in.baseCapacity, in.seed,
            in.pairs.size(), in.n == 0 || in.m == 0 ? 0.0 : in.pairs.size() / ((double) in.n * in.m),
            capacitySum, minimum, maximum, vertices, edges, (double) vertices * vertices * edges);
    }
    static int[] ints(String value) { return Arrays.stream(value.split(",")).mapToInt(Integer::parseInt).toArray(); }
    static long[] longs(String value) { return Arrays.stream(value.split(",")).mapToLong(Long::parseLong).toArray(); }
    static double[] doubles(String value) { return Arrays.stream(value.split(",")).mapToDouble(Double::parseDouble).toArray(); }

    public static void main(String[] args) throws IOException {
        Map<String,String> options = new HashMap<>();
        Set<String> allowed = Set.of("tasks", "servers", "densities", "capacities", "seeds",
            "distributions", "warmup", "runs", "out", "order-seed", "fork");
        for (String arg : args) {
            if (arg.equals("--self-test")) { selfTest(); return; }
            String[] parts = arg.split("=", 2);
            if (parts.length != 2 || !parts[0].startsWith("--") || !allowed.contains(parts[0].substring(2)))
                throw new IllegalArgumentException("Expected --option=value; see BENCHMARK.md");
            options.put(parts[0].substring(2), parts[1]);
        }
        int[] tasks = ints(options.getOrDefault("tasks", "100,500,1000"));
        int[] servers = ints(options.getOrDefault("servers", "25,100"));
        double[] densities = doubles(options.getOrDefault("densities", "0.01,0.1,0.5"));
        int[] capacities = ints(options.getOrDefault("capacities", "4"));
        long[] seeds = longs(options.getOrDefault("seeds", "11,29,47"));
        Distribution[] distributions = Arrays.stream(options.getOrDefault("distributions", "FIXED,UNIFORM,SKEWED").split(","))
            .map(Distribution::valueOf).toArray(Distribution[]::new);
        int warmup = Integer.parseInt(options.getOrDefault("warmup", "5"));
        int runs = Integer.parseInt(options.getOrDefault("runs", "9"));
        long orderSeed = Long.parseLong(options.getOrDefault("order-seed", "20261115"));
        String fork = options.getOrDefault("fork", "1");
        if (warmup < 1 || runs < 1) throw new IllegalArgumentException("warmup and runs must be positive");
        for (int n : tasks) if (n < 0) throw new IllegalArgumentException("Negative task count");
        for (int m : servers) if (m < 0) throw new IllegalArgumentException("Negative server count");
        for (double p : densities) if (!Double.isFinite(p) || p < 0 || p > 1) throw new IllegalArgumentException("Invalid density");
        for (int c : capacities) if (c < 0 || c > Integer.MAX_VALUE / 10) throw new IllegalArgumentException("Invalid base capacity");

        // Store parameter combinations, not all generated graphs, to limit retained memory.
        record Case(int n, int m, double p, Distribution d, int c, long seed) {}
        List<Case> cases = new ArrayList<>();
        for (int n : tasks) for (int m : servers) for (double p : densities)
            for (Distribution d : distributions) for (int c : capacities) for (long seed : seeds)
                cases.add(new Case(n,m,p,d,c,seed));
        Collections.shuffle(cases, new Random(orderSeed));
        Path directory = Path.of(options.getOrDefault("out", "results"));
        Files.createDirectories(directory);
        String prefix = "n,m,p,distribution,base_capacity,seed,r,actual_density,capacity_sum,capacity_min,capacity_max,V,E,V2E";
        try (PrintWriter raw = new PrintWriter(Files.newBufferedWriter(directory.resolve("raw.csv"), StandardCharsets.UTF_8));
             PrintWriter summary = new PrintWriter(Files.newBufferedWriter(directory.resolve("summary.csv"), StandardCharsets.UTF_8))) {
            raw.println(prefix + ",fork,case_order,run,warmup_runs,measured_runs,construction_ns,dinic_ns,extraction_ns,total_ns,max_flow");
            summary.println(prefix + ",fork,case_order,warmup_runs,measured_runs,median_construction_ns,median_dinic_ns,median_extraction_ns,median_total_ns,max_flow");
            int caseOrder = 0;
            for (Case c : cases) {
                Instance in = generate(c.n,c.m,c.p,c.d,c.c,c.seed);
                long expectedFlow = -1;
                for (int k = 0; k < warmup; k++) expectedFlow = run(in).flow;
                List<Sample> samples = new ArrayList<>();
                String params = parameters(in);
                for (int k = 0; k < runs; k++) {
                    Sample sample = run(in);
                    if (sample.flow != expectedFlow) throw new AssertionError("Inconsistent flow");
                    samples.add(sample);
                    raw.println(params + "," + csv(fork, caseOrder, k + 1, warmup, runs,
                        sample.construction, sample.dinic, sample.extraction, sample.total, sample.flow));
                }
                summary.println(params + "," + csv(fork, caseOrder, warmup, runs,
                    median(samples,0), median(samples,1), median(samples,2), median(samples,3), expectedFlow));
                caseOrder++;
            }
            if (raw.checkError() || summary.checkError()) throw new IOException("CSV write failed");
        }
        List<String> metadata = List.of(
            "arguments=" + Arrays.toString(args), "java.version=" + System.getProperty("java.version"),
            "java.vm.name=" + System.getProperty("java.vm.name"),
            "jvm.arguments=" + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments(),
            "os.name=" + System.getProperty("os.name"), "os.version=" + System.getProperty("os.version"),
            "os.arch=" + System.getProperty("os.arch"), "available.processors=" + Runtime.getRuntime().availableProcessors(),
            "max.heap.bytes=" + Runtime.getRuntime().maxMemory(), "order.seed=" + orderSeed,
            "fork=" + fork, "case.count=" + cases.size(), "blackhole=" + blackhole);
        Files.write(directory.resolve("metadata.txt"), metadata, StandardCharsets.UTF_8);
        System.out.println("Completed " + cases.size() + " cases; CSV and metadata: " + directory.toAbsolutePath());
    }
}
