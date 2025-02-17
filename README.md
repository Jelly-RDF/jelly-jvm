[![Documentation](https://img.shields.io/website?url=https%3A%2F%2Fw3id.org%2Fjelly%2Fjelly-jvm&label=Documentation)](https://w3id.org/jelly/jelly-jvm) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![Scala build and test](https://github.com/Jelly-RDF/jelly-jvm/actions/workflows/scala.yml/badge.svg)](https://github.com/Jelly-RDF/jelly-jvm/actions/workflows/scala.yml) [![Release](https://github.com/Jelly-RDF/jelly-jvm/actions/workflows/release.yml/badge.svg)](https://github.com/Jelly-RDF/jelly-jvm/actions/workflows/release.yml) [![Discord](https://img.shields.io/discord/1333391881404420179?label=Discord%20chat)](https://discord.gg/A8sN5XwVa5)

# Jelly-JVM

Implementation of [Jelly](https://w3id.org/jelly), a super-fast RDF serialization format and streaming protocol, for Apache Jena and Eclipse RDF4J. Jelly-JVM was written in Scala 3 and a bit of Java.

Jelly-JVM gives you the full stack of utilities for fast and scalable RDF streaming with the **[Jelly protocol](https://w3id.org/jelly)**. You can only use a part of the stack (e.g., only the serializer), or you may choose to use the full gRPC server and the reactive streaming utilities.

**Documentation, download links, usage guide and more: [https://w3id.org/jelly/jelly-jvm](https://w3id.org/jelly/jelly-jvm)**

## Modules

**Read more about each module in the [documentation](https://w3id.org/jelly/jelly-jvm).**

### Published to Maven Central

- [`jelly-core`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/core/src) – serialization and deserialization code (using the [scalapb](https://scalapb.github.io/) library), along with generic utilities for converting the deserialized RDF data to/from the representations of RDF libraries (like Apache Jena or RDF4J). 
  - [![jelly-core Scala version support](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-core/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-core) [![javadoc](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-core_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-core_3) 

- [`jelly-jena`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/jena/src) – conversions and interop code for the [Apache Jena](https://jena.apache.org/) library.
  - [![jelly-jena Scala version support](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-jena/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-jena) [![javadoc](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-jena_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-jena_3)

- [`jelly-rdf4j`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/rdf4j/src) – conversions and interop code for the [RDF4J](https://rdf4j.org/) library.
  - [![jelly-rdf4j Scala version support](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-rdf4j/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-rdf4j) [![javadoc](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-rdf4j_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-rdf4j_3)

- [`jelly-stream`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/stream/src) – utilities for building [Reactive Streams](https://www.reactive-streams.org/) of RDF data (based on Pekko Streams). Useful for integrating with gRPC or other streaming protocols (e.g., Kafka, MQTT).
  - [![jelly-stream Scala version support](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-stream/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-stream) [![javadoc](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-stream_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-stream_3)

- [`jelly-grpc`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/grpc/src) – implementation of a gRPC client and server for the full Jelly protocol.
  - [![jelly-grpc Scala version support](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-grpc/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-grpc) [![javadoc](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-grpc_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-grpc_3)

### Other – not published to Maven

- [`jelly-integration-tests`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/integration-tests/src) – integration tests for the Jelly protocol. This module is only used for development and testing purposes.
- [`jelly-examples`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/examples/src) – examples of using Jelly-JVM in practical scenarios.

### Plugin JARs

We also publish plugin JARs which allow you to use Jelly-JVM with [Apache Jena](https://jena.apache.org/) and [RDF4J](https://rdf4j.org/) just by dropping the JARs into the classpath. You can find the JARs on the [releases page](https://github.com/Jelly-RDF/jelly-jvm/releases). **[More information about using the plugins](https://w3id.org/jelly/jelly-jvm/dev/getting-started-plugins/)**.

## Contributing and support

Feel free to submit bug reports, feature proposals and pull requests!

Check out the **[contribution guide](https://w3id.org/jelly/jelly-jvm/dev/contributing/)** for more information.

You can also join the **[Jelly Discord chat](https://discord.gg/A8sN5XwVa5)** to ask questions about using Jelly-JVM and to be up-to-date with the development activities.

### Commercial support

**[NeverBlink](https://neverblink.eu)** provides commercial support services for Jelly, including implementing custom features, system integrations, implementations for new frameworks, benchmarking, and more.

## License

The Jelly-JVM libraries are licensed under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0).

Jelly-JVM was written by [Piotr Sowiński](https://ostrzyciel.eu) ([Ostrzyciel](https://github.com/Ostrzyciel)).
