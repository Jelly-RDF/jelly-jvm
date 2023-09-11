# Jelly-JVM
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Java Virtual Machine implementation of Jelly, an RDF streaming protocol, written in Scala 3.

This collection of libraries aims to provide the full stack of utilities for fast and scalable RDF streaming with the [Jelly protocol](https://github.com/Jelly-RDF/specification). You can only use a part of the stack (e.g., only the serializer), or you may choose to use the full gRPC server and the reactive streaming utilities.

## Development status
The libraries are in active (alpha) development and **are not yet stable**. Expect a first stable release in Q4 2023.

The libraries follow the semantic versioning scheme for binary compatibility.

The `jelly-stream` and `jelly-grpc` use the [Apache Pekko library](https://pekko.apache.org/), which was forked recently from the popular Akka library. This is due to Akka switching to a non-free license.

## Modules
- `jelly-core` – serialization and deserialization code (using the [scalapb](https://scalapb.github.io/) library), along with generic utilities for converting the deserialized RDF data to/from the representations of RDF libraries (like Apache Jena or RDF4J). 
  - [![jelly-core Scala version support](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-core/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-core) [![javadoc](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-core_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-core_3) 

- `jelly-jena` – conversions and interop code for the [Apache Jena](https://jena.apache.org/) library.
  - [![jelly-jena Scala version support](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-jena/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-jena) [![javadoc](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-jena_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-jena_3)

- `jelly-rdf4j` – conversions and interop code for the [RDF4J](https://rdf4j.org/) library.
  - [![jelly-rdf4j Scala version support](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-rdf4j/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-rdf4j) [![javadoc](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-rdf4j_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-rdf4j_3)

- `jelly-stream` – utilities for building [Reactive Streams](https://www.reactive-streams.org/) of RDF data (based on Pekko Streams). Useful for integrating with gRPC or other streaming protocols (e.g., Kafka, MQTT).
  - [![jelly-stream Scala version support](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-stream/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-stream) [![javadoc](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-stream_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-stream_3)

- `jelly-grpc` – implementation of a gRPC client and server for the full Jelly protocol.
  - [![jelly-grpc Scala version support](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-grpc/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-grpc) [![javadoc](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-grpc_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-grpc_3)

## Contributing
Feel free to submit bug reports, feature proposals and pull requests! You can also contribute to the documentation in the [wiki](https://github.com/Jelly-RDF/jelly-jvm/wiki).

## License
The Jelly-JVM libraries are licensed under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0).

## Authors
- Piotr Sowiński ([Ostrzyciel](https://github.com/Ostrzyciel))
