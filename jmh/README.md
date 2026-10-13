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

## RDF benchmarks

Compare Jena, RDF4J and Jelly-RDF formats on the same RiverBench datasets (all params are free-form, see `RdfMethods` for method names):

```bash
sbt "jmh/Jmh/run -p dataset=nanopubs -p method=jena-nt,rdf4j-jelly-big RdfFormatBench"
```

Throughput is in statements per second, in the `:cells` lines. As for SPARQL, setup round-trips the data first and prints `ROUND-TRIP WARNING` if a format changes it.

```bash
sbt rdfRoundTrip   # check every format against every dataset
sbt rdfSizes       # byte sizes (plain, gzip, zstd) of the benchmarked combinations
```

## Methodology of benchmarks for publication

The following methodology was designed in a way to make comparisons across different formats implemented for a given library as fair as possible.

### Data

- 13 RiverBench datasets: all except `politiquices` (too short) and `yago-annotated-facts` (RDF-star, incompatible with many formats)
- Quoted triples (RDF-star) in the input are treated as reifying triples, not as triple terms.
- If a given dataset could not be parsed or serialized with the given method, it is ignored.
- We use the first 100k statements of each dataset.

### Tasks

- [Flat serialization](https://w3id.org/riverbench/v/dev/tasks/flat-serialization-throughput), [flat parsing](https://w3id.org/riverbench/v/dev/tasks/flat-deserialization-throughput) and [flat compression](https://w3id.org/riverbench/v/dev/tasks/flat-compression).
- The datasets may contain duplicate statements. Non-streaming writers (e.g., RDF/XML in Jena) are free to deduplicate statements when serializing.
  - Numbers of duplicate statements in the first 100k statements: assist-iot-weather 29,532, officegraph 15,448, dbpedia-live 5,150, lod-katrina 4,567, linked-spending 106.
- During parsing the throughput figure takes into account the number of statements *actually parsed from the serialized file*. For example, if Jelly or N-Triples serialized 100k statements, the numerator of its parsing throughput is 100k. If RDF/XML deduplicated these statements and serialized only 90k of them, the numerator of its parsing throughput is 90k.
- In serialization, the numerator is always 100k statements.
- Outputs are written to `OutputStream.nullOutputStream()`. 
  - This slightly favors poorly compressed formats, because they don't have to bear the memory copy costs.
  - We do not add any extra buffering. Most writers apply their own buffering strategies.

### RDF parser and writer settings

- Parsers and writers even in a single library can differ in terms of how many checks or additional processing steps they perform. Hence, we choose settings that force the same workload on all tested methods.
- In general, we disabled as many extra checks as possible to isolate the performance of the serializer / parser.
- RDF4J parser settings:
  - `PRESERVE_BNODE_IDS=true`
  - `PROCESS_ENCODED_TRIPLE_TERMS=false`
  - `VERIFY_URI_SYNTAX=false` and `VERIFY_RELATIVE_URIS=false`
  - `VERIFY_LANGUAGE_TAGS=false`, `NORMALIZE_LANGUAGE_TAGS=false` and `FAIL_ON_UNKNOWN_LANGUAGES=false`
  - `VERIFY_DATATYPE_VALUES=false`, `NORMALIZE_DATATYPE_VALUES=false` and
    `FAIL_ON_UNKNOWN_DATATYPES=false`
  - `DATATYPE_HANDLERS` and `LANGUAGE_HANDLERS` use a dummy handler that accepts anything
- RDF4J writer settings:
  - `ENCODE_TRIPLE_TERMS=false`, `ANNOUNCE_RDF12_VERSION=false`, `CONVERT_RDF_12_REIFICATION=false`,`PRETTY_PRINT=false` and `INLINE_BLANK_NODES=false`
  - Every writer except Jelly gets the dataset's prefixes through `handleNamespace` after `startRDF`.
- Jena parser settings:
  - `checking(false)`
  - `labelToNode(LabelToNode.createUseLabelAsGiven())` (Jena's Thrift and Protobuf readers already do this).
  - `SystemARQ.EnableCDTs=false`
- Jena writer settings:
  - Every streaming writer except Jelly gets the dataset's prefixes through `StreamRDF.prefix`
  - The writers that need a whole graph (RDF/XML, JSON-LD, RDF/JSON) are fed a fresh `Graph` or `DatasetGraph` 
  - These writers get the RDF/XML writer property `allowBadURIs=true`, which skips `IRIs.checkEx`

### Tested RDF formats

- N-Triples / N-Quads
- Turtle / TriG: Jena `TURTLE_BLOCKS` / `TRIG_BLOCKS`. RDF4J with pretty printing off. Both are streaming.
- RDF/XML: Jena `RDFXML_PLAIN`, RDF4J's `RDFXMLWriter`, which streams. Triples only.
- JSON-LD:
  - Jena `JSONLD11_FLAT`, expanded with no `@context`.
  - RDF4J's default EXPAND mode, using hasmac-json-ld 0.10.2, RDF4J's own fork of Titanium. Jena
    uses Titanium 1.7.0.
  - Neither one uses RDF prefixes.
- RDF/JSON: triple datasets only.
- Jena Thrift and Protobuf: base (lossless) variant. The lossy `_VALUES` variants were not tested.
- RDF4J Binary: default settings
- Jelly, `SMALL_STRICT` / `BIG_STRICT`, each version at its own default frame size: v4.x.x 1024, v3.x.x 256.

### SPARQL writer and parser settings

- RDF4J result parsers: 
  - `PROCESS_ENCODED_TRIPLE_TERMS=false`, set before `setQueryResultHandler`
- RDF4J result writers: 
  - `ENCODE_TRIPLE_TERMS=false`
- **Jena result readers and writers:** 
  - `ARQ.inputGraphBNodeLabels=true` and `ARQ.outputGraphBNodeLabels=true`
- **Reading:** 
  - Every reader's sink reads every value of every row (`Binding.forEach`, or iterating
    the `BindingSet`).
  - Readers that decode values only when asked, such as RDF4J's triple-term wrapper and lazy bindings, must do this work inside the measurement.
- **Formats:**
  - Jena SRX, SRJ, TSV, Thrift, Protobuf, and Jelly small and big.
  - RDF4J SRX, SRJ, TSV, Binary, and Jelly small and big.
  - CSV is not included in the benchmarks: it is lossy.

### Compression

- We measure the total size of the serialized file in a given format.
- When citing "bytes per statement", we always assume 100k statements were saved, regardless of whether the writer deduplicated some statements or not.
- We additionally test compression with binary compression applied on top. Tested methods:
  - gzip level 6
  - zstd level 3

### Measurement

- On heterogeneous CPU architectures, the benchmark should be pinned to the "performance" cores.
  - On the DGX Spark, we used cores 15–19.
- The benchmark is run in JMH in 3 sweeps. In each sweep:
  - 5 x 1s warmup
  - 5 x 1s measurement
  - 1 fork
  - We have empirically confirmed that this warmup time sufficiently reduces variance, when using the JVM options we picked (see below).
- Instead of using multiple forks in one run we use sweeps to mitigate the effect of hardware's performance drifting over time (e.g., due to changing room temperature).
- We report the median of the 3 measuments from the sweeps.
- We use multiple JVM options to ensure the stability and reprodicibility of the results. See [CommonParams.scala](src/main/scala/eu/neverblink/jelly/jmh/CommonParams.scala) for details.
- We recommend running the benchmarks with either a recent OpenJDK or GraalVM CE. We ran the most recent benchmarks with GraalVM CE 25.4.
