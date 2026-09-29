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
sbt "jmh/Jmh/run -f1 -prof \"perfasm:intelSyntax=true;tooBigThreshold=1500;top=3\" .*RdfIriParseBench.*"
```

Run this to get all options for perfasm:

```bash
sbt "jmh/Jmh/run -f1 -prof \"perfasm:help\""
```

To see allocation rates:

```bash
sbt "jmh/Jmh/run -f1 -prof gc .*NodeCacheBench.*"
```

## SPARQL result set benchmarks

Datasets are synthetic presets (`SparqlDataGen`) or RiverBench datasets, which have to be downloaded once (~500 MB):

```bash
sbt jmh/riverbenchFetch
```

Compare Jena, RDF4J and Jelly-SPARQL result set formats (all params are free-form, see `SparqlMethods` for method names):

```bash
sbt "jmh/Jmh/run -p dataset=nanopubs,wide-5 -p rows=100000 -p method=jena-srj,rdf4j-jelly-big SparqlFormatBench"
```

Throughput is in cells (rows × variables) per second, in the `:cells` lines. Setup round-trips the data first and prints `ROUND-TRIP WARNING` if a format changes it.

```bash
sbt sparqlRoundTrip   # check every format against every dataset
sbt sparqlSizes       # byte sizes (plain, gzip, zstd) of the benchmarked combinations
```
