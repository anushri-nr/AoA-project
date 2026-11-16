# Analysis of Algorithms: Java modules

This repository is a Maven multi-module Java 17 project. Open the root `pom.xml` as a Maven project in your IDE; `cloud-task-assignment` imports as its own module.

```text
AoA-project/
  pom.xml                         # parent / reactor build
  cloud-task-assignment/
    pom.xml                       # your Java module
    src/main/java/
      CloudTaskBenchmark.java
    BENCHMARK.md
    results/                      # original benchmark measurements
    analysis/                     # Python analysis and plots
    target/                       # generated classes and executable JAR
  report/
    main.tex                      # single LaTeX source, including Java listing
    main.pdf
    figures/
```

## Build and verify

Requirements: Java 17 and Maven 3. Run from the repository root:

```sh
mvn package
java -jar cloud-task-assignment/target/cloud-task-assignment-1.0-SNAPSHOT.jar --self-test
```

The explicit self-test command runs the worked example and 672 tiny cases against an exhaustive optimizer. `mvn package` builds the executable JAR; the current project does not have JUnit/Surefire test classes.

For an IDE run configuration, choose `CloudTaskBenchmark` as the main class, `cloud-task-assignment` as the classpath module, and the module directory as the working directory. Set program arguments to `--self-test` for verification.

## Benchmark and report

Run benchmarks from the module directory so relative output paths match the analysis:

```sh
cd cloud-task-assignment
java -Xms512m -Xmx512m -jar target/cloud-task-assignment-1.0-SNAPSHOT.jar --out=results/local
python3 analysis/analyze.py --input=results/default --output=analysis
```

Use a new benchmark output directory to preserve the recorded `results/default` measurements. See the module's `BENCHMARK.md` for all parameters. The PDF and single-file LaTeX source are in the repository-root `report/` directory, outside the Java module, and their relative figure paths still work.

## Add the other project part

Create a sibling Java module with its own `pom.xml` and `src/main/java/` tree. Its parent coordinates should match the root POM. Add its folder name as another `<module>` entry in the root POM. The collaborator can then build their part independently with `mvn -pl their-module package`, or build all project modules together with `mvn package`.

These are Maven build modules. A Java Platform Module System descriptor (`module-info.java`) is not required for this project layout. The current Java source and measured benchmark inputs have been preserved byte-for-byte during the move.
