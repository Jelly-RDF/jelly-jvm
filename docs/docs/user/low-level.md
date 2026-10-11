# Low-level usage

!!! warning

    This page describes a low-level API that is a bit of a hassle to use directly. It's recommended to use the higher-level abstractions provided by the [`jelly-pekko-stream` module](reactive.md), or the integrations with [Apache Jena's RIOT](jena.md) or [RDF4J's Rio](rdf4j.md) libraries. If you really want to use this, it is highly recommended that you first get a [basic understanding of how Jelly works under the hood]({{ proto_link('user-guide') }}) and take a look at the [code in the `jelly-pekko-stream` module]({{ git_link('pekko-stream') }}) to see how it's done there.

!!! note

    The following guide uses the [Apache Jena](jena.md) library as an example. The exact same thing can be done with [RDF4J](rdf4j.md) or any other RDF library that has a Jelly integration.

## Deserialization

To parse a serialized stream frame into triples/quads:

1. Call {{ javadoc_link_pretty('core', 'proto.v1.RdfStreamFrame', 'parseFrom') }} if it's a non-delimited frame (like you would see, e.g., in a Kafka or gRPC stream), or `parseDelimitedFrom` if it's a [delimited stream]({{ proto_link('user-guide#delimited-vs-non-delimited-jelly') }}) (like you would see in a file or a socket).
    - There is also a utility method to detect if the stream is delimited or not: {{ javadoc_link_pretty('core', 'utils.IoUtils', 'autodetectDelimiting') }}. In most cases you will not need to use it. It is used internally by the Jena and RDF4J integrations for user convenience.
2. Obtain a decoder that turns `RdfStreamFrame`s into triples/quads: {{ javadoc_link_pretty('jena', 'JenaConverterFactory') }} has different methods for [different physical stream types]({{ proto_link('user-guide#stream-types') }}):
    - `anyStatementDecoder` for any physical stream type, outputs `Triple` or `Quad`
    - `triplesDecoder` for TRIPLES streams, outputs `Triple`
    - `quadsDecoder` for QUADS streams, outputs `Quad`
    - `graphsDecoder` for GRAPHS streams, outputs `Node` (named graph) and `Triple`s
    - `graphsAsQuadsDecoder` for GRAPHS streams, outputs `Quad`
    - The decoders read all versions of Jelly-RDF: 1.0 and 1.1 (row layout) and 1.2 (column layout). GRAPHS streams only exist in Jelly-RDF 1.0 and 1.1.
3. For each frame, call the decoder's `ingestFrame` method. This method does not return anything, because handling is done differently from previous versions.
    - In a Jelly-RDF 1.0 or 1.1 stream, you can also call `ingestRow` for each row of the frame. In a Jelly-RDF 1.2 stream you must use `ingestFrame`.
4. For any decoder you must pass an implementation of respective `RdfHandler` (i.e. for `triplesDecoder` it is `TripleHandler`, for `graphsDecoder` it is `GraphHandler`, etc.). These handlers have methods that start from `handle` and are called for each triple/quad/graph that is parsed. You can implement them to do necessary actions with the data.

## Serialization

To serialize triples/quads into a stream frame:

1. If you want to serialize an RDF graph/dataset, transform them first into triples/quads in an iterable form. Use the `triples`/`quads`/`graphs` methods provided by the {{ javadoc_link_pretty('jena', 'JenaAdapters') }} objects (`MODEL_ADAPTER`, `DATASET_ADAPTER` and `GRAPH_ADAPTER`).
2. Obtain an encoder of Jelly-RDF 1.2 streams: use the {{ javadoc_link_pretty('jena', 'JenaConverterFactory', 'encoder') }} method with `RdfEncoder.Params` to get an instance of {{ javadoc_link_pretty('core', 'RdfEncoder') }}. The parameters are the stream options, the target number of statements in a frame, and a frame sink: a function that receives each finished `RdfStreamFrame`.
    - You are responsible for setting the stream options with the physical stream type (TRIPLES or QUADS). See the guide on [Jelly options presets](utilities.md#jelly-options-presets) for more information.
3. Call the encoder's `handleTriple`, `handleQuad`, and `handleNamespace` methods. In a stream of RDF Messages (stream type MESSAGES), call `handleMessageEnd` at the end of each message.
4. The encoder passes each finished frame to the frame sink: when the frame has the target number of statements, when the lookup tables fill up, and when you call `flush`. Call `flush` at the end of the stream to get the last frame. The frame is only valid until the sink returns, because the encoder reuses its buffers – serialize it (e.g., with `writeDelimitedTo`) in the sink.

## See also

- [Useful utilities](utilities.md)
- [Reactive streaming with Jelly-JVM](reactive.md)
- [Implementing Jelly-JVM for a new RDF library](implementing.md)
