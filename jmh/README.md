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

`JellyDecodeBench` reads Jelly BIG into Jena or RDF4J (`-p library=jena,rdf4j`) without passing rows through a Scala function, so its results move less between runs. Use it for work on the decoder.

`JellyEncodeBench` writes Jena bindings or RDF4J binding sets as Jelly BIG (`-p library=jena,rdf4j`), with nothing but the library's writer between the benchmark and the rows. Use it for work on the encoder.

`JellyBigBench` writes and reads Jelly BIG in both formats (`-p format=rdf,sparql`) with both libraries (`-p library=jena,rdf4j`), through each library's own writer and reader, in terms per second. `JellyBigSizes` (`sbt "jmh/runMain eu.neverblink.jelly.jmh.JellyBigSizes 100000 nanopubs"`) prints the sizes of what it writes. Use them to compare versions.

`RdfCodecBench` encodes and decodes the same RiverBench datasets as Jelly-RDF with Jena (`-p preset=small,big`), in statements per second (the `:cells` lines). Use it for work on the RDF encoder, decoder and lookups.

```bash
sbt sparqlRoundTrip   # check every format against every dataset
sbt sparqlSizes       # byte sizes (plain, gzip, zstd) of the benchmarked combinations
```
