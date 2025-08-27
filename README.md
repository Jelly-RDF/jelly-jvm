[![Documentation](https://img.shields.io/website?url=https%3A%2F%2Fw3id.org%2Fjelly%2Fjelly-jvm&label=Documentation)](https://w3id.org/jelly/jelly-jvm) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![Scala build and test](https://github.com/Jelly-RDF/jelly-jvm/actions/workflows/scala.yml/badge.svg)](https://github.com/Jelly-RDF/jelly-jvm/actions/workflows/scala.yml) [![Release](https://github.com/Jelly-RDF/jelly-jvm/actions/workflows/release.yml/badge.svg)](https://github.com/Jelly-RDF/jelly-jvm/actions/workflows/release.yml) [![Discord](https://img.shields.io/discord/1333391881404420179?label=Discord%20chat)](https://discord.gg/A8sN5XwVa5)

# Jelly-JVM

Implementation of [Jelly](https://w3id.org/jelly), a super-fast RDF serialization format and streaming protocol, for Apache Jena, Eclipse RDF4J, Titanium RDF API, and Neo4j.

You can use Jelly-JVM simply as a "plain old" RDF file serializer/parser, just a very fast one.

But, this library also gives you the full stack of utilities for scalable RDF streaming with **[Jelly](https://w3id.org/jelly)** in a lot of different scenarios, including networking environments. You can only use a part of the stack (e.g., only the serializer), or you may choose to use the full gRPC server and the reactive streaming utilities.

**Documentation, download links, usage guide and more: [https://w3id.org/jelly/jelly-jvm](https://w3id.org/jelly/jelly-jvm)**

## Quick start

* **[📃 How to use Jelly-JVM in code](https://w3id.org/jelly/jelly-jvm/dev/getting-started-devs/)**
* **[🛠️ How to use Jelly-JVM as a Jena or RDF4J plugin (zero coding!)](https://w3id.org/jelly/jelly-jvm/dev/getting-started-plugins/)**
- **[🛠️ How to use Jelly-JVM with Neo4j via the neosemantics plugin](https://w3id.org/jelly/jelly-jvm/dev/getting-started-neo4j/)**

## Modules

**Read more about each module in the [documentation](https://w3id.org/jelly/jelly-jvm).**

### Published to Maven Central

- [`jelly-core`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/core/src) – core generic code for serializing/deserializing Jelly data. You need an additional module (like `jelly-jena`) to integrate it with a specific RDF library. 
  - [![Maven Central Version](https://img.shields.io/maven-central/v/eu.neverblink.jelly/jelly-core)](https://central.sonatype.com/artifact/eu.neverblink.jelly/jelly-core) [![javadoc](https://javadoc.io/badge2/eu.neverblink.jelly/jelly-core/javadoc.svg)](https://javadoc.io/doc/eu.neverblink.jelly/jelly-core) 

- [`jelly-jena`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/jena/src) – interop code for the [Apache Jena](https://jena.apache.org/) library.
  - [![Maven Central Version](https://img.shields.io/maven-central/v/eu.neverblink.jelly/jelly-jena)](https://central.sonatype.com/artifact/eu.neverblink.jelly/jelly-jena) [![javadoc](https://javadoc.io/badge2/eu.neverblink.jelly/jelly-jena/javadoc.svg)](https://javadoc.io/doc/eu.neverblink.jelly/jelly-jena)

- [`jelly-rdf4j`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/rdf4j/src) – interop code for the [Eclipse RDF4J](https://rdf4j.org/) library.
  - [![Maven Central Version](https://img.shields.io/maven-central/v/eu.neverblink.jelly/jelly-rdf4j)](https://central.sonatype.com/artifact/eu.neverblink.jelly/jelly-rdf4j) [![javadoc](https://javadoc.io/badge2/eu.neverblink.jelly/jelly-rdf4j/javadoc.svg)](https://javadoc.io/doc/eu.neverblink.jelly/jelly-rdf4j)

- [`jelly-titanium-rdf-api`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/titanium-rdf-api/src) – integration with the minimalistic [Titanium RDF API](https://github.com/filip26/titanium-rdf-api).
  - [![Maven Central Version](https://img.shields.io/maven-central/v/eu.neverblink.jelly/jelly-titanium-rdf-api)](https://central.sonatype.com/artifact/eu.neverblink.jelly/jelly-titanium-rdf-api) [![javadoc](https://javadoc.io/badge2/eu.neverblink.jelly/jelly-titanium-rdf-api/javadoc.svg)](https://javadoc.io/doc/eu.neverblink.jelly/jelly-titanium-rdf-api)

- [`jelly-neo4j-plugin`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/neo4j-plugin/src) – integration with the [Neo4j](https://neo4j.com/) graph database via the [neosemantics](https://neo4j.com/labs/neosemantics/) plugin.
  - [![Maven Central Version](https://img.shields.io/maven-central/v/eu.neverblink.jelly/jelly-neo4j-plugin)](https://central.sonatype.com/artifact/eu.neverblink.jelly/jelly-neo4j-plugin) [![javadoc](https://javadoc.io/badge2/eu.neverblink.jelly/jelly-neo4j-plugin/javadoc.svg)](https://javadoc.io/doc/eu.neverblink.jelly/jelly-neo4j-plugin)

- [`jelly-pekko-stream`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/pekko-stream/src) – utilities for building [Reactive Streams](https://www.reactive-streams.org/) of RDF data, based on Pekko Streams. Useful for integrating with for example gRPC, Kafka, MQTT...
  - [![Maven Central Version](https://img.shields.io/maven-central/v/eu.neverblink.jelly/jelly-pekko-stream_3)](https://central.sonatype.com/artifact/eu.neverblink.jelly/jelly-pekko-stream_3) [![javadoc](https://javadoc.io/badge2/eu.neverblink.jelly/jelly-pekko-stream_3/javadoc.svg)](https://javadoc.io/doc/eu.neverblink.jelly/jelly-pekko-stream_3)

- [`jelly-pekko-grpc`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/pekko-grpc/src) – implementation of a gRPC client and server for the full Jelly protocol, based on Pekko Streams.
  - [![Maven Central Version](https://img.shields.io/maven-central/v/eu.neverblink.jelly/jelly-pekko-grpc_3)](https://central.sonatype.com/artifact/eu.neverblink.jelly/jelly-pekko-grpc_3) [![javadoc](https://javadoc.io/badge2/eu.neverblink.jelly/jelly-pekko-grpc_3/javadoc.svg)](https://javadoc.io/doc/eu.neverblink.jelly/jelly-pekko-grpc_3)

### Other – not published to Maven

- [`jelly-integration-tests`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/integration-tests/src) – integration tests for the Jelly protocol. This module is only used for development and testing purposes.
- [`jelly-examples`](https://github.com/Jelly-RDF/jelly-jvm/tree/main/examples/src) – examples of using Jelly-JVM in practical scenarios.

### Plugin JARs

We also publish plugin JARs which allow you to use Jelly-JVM with [Apache Jena](https://jena.apache.org/), [RDF4J](https://rdf4j.org/), and Neo4j just by dropping the JARs into the classpath. You can find the JARs on the [releases page](https://github.com/Jelly-RDF/jelly-jvm/releases). More information about using the plugins with **[Jena and RDF4J](https://w3id.org/jelly/jelly-jvm/dev/getting-started-plugins/)** and with **[Neo4j](https://w3id.org/jelly/jelly-jvm/dev/getting-started-neo4j/)**.

## Contributing and support

Feel free to submit bug reports, feature proposals and pull requests!

Check out the **[contribution guide](https://w3id.org/jelly/jelly-jvm/dev/contributing/)** for more information.

You can also join the **[Jelly Discord chat](https://discord.gg/A8sN5XwVa5)** to ask questions about using Jelly-JVM and to be up-to-date with the development activities.

### Commercial support

**[NeverBlink](https://neverblink.eu)** provides commercial support services for Jelly, including implementing custom features, system integrations, implementations for new frameworks, benchmarking, and more.

## License

The Jelly-JVM libraries are licensed under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0).

Jelly-JVM contributors: https://github.com/Jelly-RDF/jelly-jvm/graphs/contributors

----

The development of the Jelly protocol, its implementations, and supporting tooling was co-funded by the European Union. **[More details](https://w3id.org/jelly/dev/licensing/projects)**.
