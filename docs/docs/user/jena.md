This guide explains the functionalities of the `jelly-jena` module, which provides Jelly support for [Apache Jena](https://jena.apache.org/).

If you just want to add Jelly format support to Apache Jena / Apache Jena Fuseki, you can use the Jelly-JVM plugin JAR. See the **[dedicated guide](../getting-started-plugins.md#apache-jena-apache-jena-fuseki)** for more information.

## Base facilities

`jelly-jena` implements the {{ javadoc_link_pretty('core', 'ConverterFactory') }} trait in {{ javadoc_link_pretty('jena', 'JenaConverterFactory$') }}. This factory allows you to build encoders and decoders that convert between Jelly's `RdfStreamFrame`s and Apache Jena's `Triple` and `Quad` objects. The {{ javadoc_link_pretty('core', 'proto.v1.RdfStreamFrame') }} class is an object representation of Jelly's binary format.

The module also implements the {{ javadoc_link_pretty('core', 'IterableAdapter') }} trait in {{ javadoc_link_pretty('jena', 'JenaIterableAdapter$') }}. This adapter provides extension methods for Apache Jena's `Model`, `Dataset`, `Graph`, and `DatasetGraph` classes to convert them into an iterable of triples (`.asTriples`), quads (`.asQuads`), or named graphs (`.asGraphs`). This is useful when working with Jelly [on a lower level](low-level.md) or when [using the `jelly-stream` module](reactive.md).

## Serialization and deserialization with RIOT

`jelly-jena` implements an RDF writer and reader for [Apache Jena's RIOT library](https://jena.apache.org/documentation/io/). This means you can use Jelly just like, for example, Turtle or RDF/XML. See the example below:

{{ code_example('JenaRiot.java') }}

Usage notes:

- {{ javadoc_link_pretty('core', 'JellyOptions$') }} provides a few common presets for Jelly serialization options construct a `JellyFormatVariant`, as shown in the example above. You can also further customize the serialization options (e.g., dictionary size).
- The RIOT writer (serializer) integration implements only the [delimited variant of Jelly]({{ proto_link('user-guide#delimited-vs-non-delimited-jelly') }}). It is used for writing Jelly to files on disk or sockets. Because of this, you cannot use RIOT to write non-delimited Jelly data (e.g., a single message to a Kafka stream). For this, you should use the `jelly-stream` module or the more low-level API: [Low-level usage](low-level.md).
- However, the RIOT parser (deserializer) integration will automatically detect if the parsed Jelly data is delimited or not. If it's non-delimited, the parser will assume that there is only one `RdfStreamFrame` in the file.
- Jelly's parsers and writers are registered in the {{ javadoc_link_pretty('jena', 'riot.JellyLanguage$') }} object ([source code]({{ git_link('jena/src/main/scala/eu/ostrzyciel/jelly/convert/jena/riot/JellyLanguage.scala') }})). This registration should happen automatically when you include the `jelly-jena` module in your project, using Jena's [component initialization mechanism](https://jena.apache.org/documentation/notes/system-initialization.html).

## Streaming serialization with RIOT

`jelly-jena` also implements a streaming writer ([`StreamRDF` API in Jena](https://jena.apache.org/documentation/io/streaming-io.html)). Using it is similar to the regular RIOT writer, with a slightly different setup:

{{ code_example('JenaRiotStreaming.java') }}


## See also

- [Useful utilities](utilities.md)
- [Reactive streaming with Jelly-JVM](reactive.md)
- [Using Jelly with Jena's CLI tools](jena-cli.md)
