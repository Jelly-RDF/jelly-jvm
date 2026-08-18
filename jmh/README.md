See the README here: https://github.com/sbt/sbt-jmh

These benchmarks should be run with the latest JDK (at least 24).

Run all benchmarks with:

```bash
sbt jmh/Jmh/run
```

Or an individual benchmark, in this case with 10 warmup iterations and 10 iterations:

```bash
sbt "jmh/Jmh/run -wi 10 -i 10 .*RdfIriParseBench.*"
```

To run with the perfasm profiler, use:

```bash
sbt "jmh/Jmh/run -f1 -prof "perfasm:intelSyntax=true;tooBigThreshold=1500;top=3" .*RdfIriParseBench.*"
```

Run this to get all options for perfasm:

```bash
sbt "jmh/Jmh/run -f1 -prof perfasm:help"
```

To see allocation rates:

```bash
sbt "jmh/Jmh/run -f1 -prof gc .*NodeCacheBench.*"
```
