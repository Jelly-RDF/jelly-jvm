# Jelly-JVM

**Jelly-JVM** is an implementation of the [Jelly serialization format and gRPC streaming protocol]({{ proto_link() }}) for the Java Virtual Machine (JVM), written in Scala 3[^1]. The supported RDF libraries are [Apache Jena](https://jena.apache.org/) and [Eclipse RDF4J](https://rdf4j.org/).

Jelly-JVM provides a **full stack** of utilities for fast and scalable RDF streaming with the [Jelly protocol]({{ proto_link( 'specification' ) }}). Oh, and [**it's *blazing-fast***]({{ proto_link('performance') }}), too!

!!! tip "Getting started with plugins – no code required"

    See the **[getting started guide with plugins](getting-started-plugins.md)** for a quick way to use Jelly with your Apache Jena or RDF4J application without writing any code.

!!! tip "Getting started for application developers"

    If you want to use the full feature set of Jelly-JVM in your code, see the **[getting started guide for application developers](getting-started-devs.md)**.

{% if jvm_version() == 'dev' %}
**This documentation is for the latest development version of Jelly-JVM** – it is not considered stable. If you are looking for the documentation of a stable release, use the version selector on the left of the top navigation bar. See: [latest stable version](https://w3id.org/jelly/jelly-jvm/stable).
{% else %}
**This documentation is for Jelly-JVM version {{ jvm_version() }}**, which uses the [Jelly protocol version {{ proto_version() }}]({{ proto_link('specification') }}). If you are looking for the documentation of a different version, use the version selector on the left of the top navigation bar. See: [latest stable version](https://w3id.org/jelly/jelly-jvm/stable), [latest development version](https://w3id.org/jelly/jelly-jvm/dev).
{% endif %}

## Library modules

The implementation is split into a few modules that can be used separately:

- `jelly-core` – implementation of the [Jelly serialization format]({{ proto_link( 'specification/serialization' ) }}) (using the [scalapb](https://scalapb.github.io/) library), along with generic utilities for converting the deserialized RDF data to/from the representations of RDF libraries (like Apache Jena or RDF4J). 
    - {{ module_badges('core') }}

- `jelly-jena` – conversions and interop code for the [Apache Jena](https://jena.apache.org/) library.
    - {{ module_badges('jena') }}

- `jelly-rdf4j` – conversions and interop code for the [RDF4J](https://rdf4j.org/) library.
    - {{ module_badges('rdf4j') }}

- `jelly-stream` – utilities for building [Reactive Streams](https://www.reactive-streams.org/) of RDF data (based on Pekko Streams). Useful for integrating with gRPC or other streaming protocols (e.g., Kafka, MQTT).
    - {{ module_badges('stream') }}

- `jelly-grpc` – implementation of a gRPC client and server for the [Jelly gRPC streaming protocol]({{ proto_link( 'specification/streaming' ) }}).
    - {{ module_badges('grpc') }}

## Plugin JARs

We also publish plugin JARs which allow you to use Jelly-JVM with [Apache Jena](https://jena.apache.org/) and [RDF4J](https://rdf4j.org/) just by dropping the JARs into the classpath. **[Find out more about using the plugins](getting-started-plugins.md)**.

## Compatibility

The Jelly-JVM implementation is compatible with Java 11 and newer. Java 11, 17, and 21 are tested in CI and are guaranteed to work. Jelly is built with [Scala 3 LTS releases](https://www.scala-lang.org/blog/2022/08/17/long-term-compatibility-plans.html).

The following table shows the compatibility of the Jelly-JVM implementation with other libraries:

| Jelly-JVM | Scala                     | Java | RDF4J | Apache Jena | Apache Pekko |
| --------- | :-----------------------: | :--: | :---: | :---------: | :----------: |
| **1.1.x** | 3.3.x (LTS)<br>2.13.x[^1] | 17+  | 5.x.x | 4.x.x       | 1.1.x        |
| 1.0.x     | 3.3.x (LTS)<br>2.13.x[^1] | 11+  | 4.x.x | 4.x.x       | 1.0.x        |

See the **[compatibility policy](user/compatibility.md)** for more details and the **[release notes on GitHub](https://github.com/Jelly-RDF/jelly-jvm/releases)**.

## Documentation

Below is a list of all documentation pages about Jelly-JVM. You can also browse the Javadoc using the badges in the module list above. The documentation uses examples written in Scala, but the libraries can be used from Java as well.

- [Getting started with Jena/RDF4J plugins](getting-started-plugins.md) – how to use Jelly-JVM as a plugin for Apache Jena or RDF4J, without writing any code.
- [Getting started for application developers](getting-started-devs.md) – how to use Jelly-JVM in code.
- User guide
    - [Apache Jena integration](user/jena.md)
    - [RDF4J integration](user/rdf4j.md)
    - [Reactive streaming](user/reactive.md)
    - [gRPC](user/grpc.md)
    - [Useful utilities](user/utilities.md)
    - [Compatibility policy](user/compatibility.md)
    - [Scala 2.13 builds](user/scala2.md)
- Developer guide
    - [Releases](dev/releases.md)
    - [Implementing Jelly for other libraries](dev/implementing.md)
- [Contributing to Jelly-JVM](contributing.md)
- [License and citation](licensing.md)
- [Release notes on GitHub](https://github.com/Jelly-RDF/jelly-jvm/releases)
- [Main Jelly website]({{ proto_link( '' ) }}) – including the Jelly protocol specification and explanation of the various stream types.



[^1]: Scala 2.13-compatible builds of Jelly-JVM are available as well. [See more details](user/scala2.md).
