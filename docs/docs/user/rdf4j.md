This guide explains the functionalities of the `jelly-rdf4j` module, which provides Jelly support for [Eclipse RDF4J](https://rdf4j.org/).

If you just want to add Jelly format support to your RDF4J application, you can use the Jelly-JVM plugin JAR. See the **[dedicated guide](../getting-started-plugins.md#eclipse-rdf4j)** for more information.

## Base facilities

`jelly-rdf4j` implements the {{ javadoc_link_pretty('core', 'JellyConverterFactory') }} interface in {{ javadoc_link_pretty('rdf4j', 'Rdf4jConverterFactory') }}. This factory allows you to build encoders and decoders that convert between Jelly's `RdfStreamFrame`s and RDF4J's `Statement` objects. The {{ javadoc_link_pretty('core', 'proto.v1.RdfStreamFrame') }} class is an object representation of Jelly's binary format.

The module also implements the {{ javadoc_link_pretty('core', 'utils.DatasetAdapter') }} and {{ javadoc_link_pretty('core', 'utils.GraphAdapter') }} interfaces in {{ javadoc_link_pretty('rdf4j', 'Rdf4jAdapters') }}. These adapters provide methods for RDF4J's `Model` class to convert it into an iterable of triples (`GRAPH_ADAPTER.triples`), quads (`DATASET_ADAPTER.quads`), or named graphs (`DATASET_ADAPTER.graphs`). This is useful when working with Jelly [on a lower level](low-level.md) or when [using the `jelly-pekko-stream` module](reactive.md).

## Serialization and deserialization with RDF4J Rio

`jelly-rdf4j` implements an RDF writer and parser for [Eclipse RDF4J's Rio library](https://rdf4j.org/documentation/programming/rio/). This means you can use Jelly just like any other RDF serialization format (e.g., RDF/XML, Turtle). See the example below:

{{ code_example('Rdf4jRio.java') }}

Usage notes:

- {{ javadoc_link_pretty('core', 'JellyOptions') }} provides a few common presets for Jelly serialization options. These options are passed through {{ javadoc_link_pretty('rdf4j', 'rio.JellyWriterSettings', 'configFromOptions') }} and used to configure the writer, as shown in the example above. You can also further customize the serialization options (e.g., dictionary size).
- The RDF4J Rio writer (serializer) integration implements only the [delimited variant of Jelly]({{ proto_link('user-guide#delimited-vs-non-delimited-jelly') }}). It is used for writing Jelly to files on disk or sockets. Because of this, you cannot use Rio to write non-delimited Jelly data (e.g., a single message to a Kafka stream). For this, you should use the `jelly-pekko-stream` module or the more low-level API: [Low-level usage](low-level.md).
- However, the Rio parser (deserializer) integration will automatically detect if the parsed Jelly data is delimited or not. If it's non-delimited, the parser will assume that there is only one `RdfStreamFrame` in the file.
- Jelly's parsers and writers are in the {{ javadoc_link_pretty('rdf4j', 'rio') }} package ([source code]({{ git_link('rdf4j/src/main/java/eu/neverblink/jelly/convert/rdf4j/rio') }})). They are automatically registered on startup using the `RDFParserFactory` and `RDFWriterFactory` [SPIs](https://en.wikipedia.org/wiki/Service_provider_interface) provided by RDF4J.

## See also

- [Useful utilities](utilities.md)
- [Reactive streaming with Jelly-JVM](reactive.md)
