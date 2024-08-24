# User guide – reactive streaming

This guide explains the reactive streaming functionalities of the `jelly-stream` module.

!!! info "Prerequisites"

    If you are unfamiliar with the concept of reactive streams or Apache Pekko Streams, we highly recommend you start from reading about the **[basic concepts of Pekko Streams](https://pekko.apache.org/docs/pekko/current/stream/stream-flows-and-basics.html)**.
    
    We also recommend you first read about the **[RDF stream types in Jelly]({{ proto_link('user-guide#rdf-stream-types') }})**. Otherwise, this guide may not make much sense.


You can use `jelly-stream` with any RDF library that has a Jelly integration, such as [Apache Jena](jena.md) (using `jelly-jena`) or [RDF4J](rdf4j.md) (using `jelly-rdf4j`). The streaming API is generic and identical across all libraries.

## Basic concepts

A key notion of this API are the encoders and decoders.

- An encoder turns objects from your RDF library of choice (e.g., `Triple` in Apache Jena) into an object representation of Jelly's binary format (`RdfStreamFrame`).
- A decoder does the opposite: it turns `RdfStreamFrame`s into objects from your RDF library of choice.

So, for example, an encoder flow for flat triple streams would have a type of `#!scala Flow[Triple, RdfStreamFrame, NotUsed]` in Apache Jena. The opposite (a flat triple stream decoder) would have a type of `#!scala Flow[RdfStreamFrame, Triple, NotUsed]`.

`RdfStreamFrame`s can be converted to and from raw bytes using a range of methods, depending on your use case. See the sections below for examples.

## Encoding a single RDF graph or dataset as a flat stream (`EncoderSource`)

The easiest way to start is with flat RDF streams (i.e., flat streams of triples or quads). You can convert an RDF dataset or graph into such using the methods in {{ javadoc_link_pretty('stream', 'EncoderSource$') }}.

{{ code_example('PekkoStreamsEncoderSource.scala') }}

## Encoding any RDF data as a flat or grouped stream (`EncoderFlow`)

The {{ javadoc_link_pretty('stream', 'EncoderFlow$') }} provides even more options for turning RDF data into Jelly streams, including both grouped and flat streams. Every [type of RDF stream in Jelly](({{ proto_link('user-guide#rdf-stream-types') }})) can be created using this API.

{{ code_example('PekkoStreamsEncoderFlow.scala') }}

## Decoding RDF streams (`DecoderFlow`)

The {{ javadoc_link_pretty('stream', 'DecoderFlow$') }} provides methods for decoding flat and grouped streams. There is no opposite equivalent to `EncoderSource` for decoding, though. This would require constructing an RDF graph or dataset from statements, which is a process that can vary a lot depending on your application. You will have to do this part yourself.

{{ code_example('PekkoStreamsDecoderFlow.scala') }}

## Byte streams (delimited variant)

In all of the examples above, we used the [non-delimited variant of Jelly]({{ proto_link('user-guide#delimited-vs-non-delimited-jelly') }}), which is appropriate for, e.g., sending Jelly data over [gRPC](grpc.md) or Kafka. If you want to write Jelly data to a file or a socket, you will need to use the delimited variant. `jelly-stream` provides a few methods for this in {{ javadoc_link_pretty('stream', 'JellyIo') }}.

{{ code_example('PekkoStreamsWithIo.scala') }}

## See also

- [Using Jelly gRPC servers and clients](grpc.md)
- [Useful utilities](utilities.md)
- [Low-level usage](low-level.md)
