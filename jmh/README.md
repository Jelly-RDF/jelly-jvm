See the README here: https://github.com/sbt/sbt-jmh

Run all benchmarks with:

```bash
sbt jmh/Jmh/run
```

Or an individual benchmark, in this case with 10 warmup iterations and 10 iterations:

```bash
jmh/Jmh/run -wi 10 -i 10 .*RdfIriParseBench.*
```

To run with the perfasm profiler, use:

```bash
sbt jmh/Jmh/run -f1 -prof "perfasm:intelSyntax=true;tooBigThreshold=1500;top=3" .*RdfIriParseBench.*
```
