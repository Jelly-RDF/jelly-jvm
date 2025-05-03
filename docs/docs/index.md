# Jelly-JVM

**Jelly-JVM** is an implementation of the [Jelly serialization format and gRPC streaming protocol]({{ proto_link() }}) for the Java Virtual Machine (JVM). It supports [Apache Jena](user/jena.md), [Eclipse RDF4J](user/rdf4j.md), and the [Titanium RDF API](user/titanium.md).

Jelly-JVM provides a **full stack** of utilities for fast and scalable RDF streaming with the [Jelly protocol]({{ proto_link( 'specification' ) }}). Oh, and [**it's *blazing-fast***]({{ proto_link('performance') }}), too!

!!! tip "Getting started with plugins – no code required"

    **[See how to use plugins](getting-started-plugins.md)** to quickly add Jelly support to your Apache Jena or RDF4J app without writing any code.

!!! tip "Getting started for application developers"

    If you want to use the full feature set of Jelly-JVM in your code, see the **[getting started guide for application developers](getting-started-devs.md)**.

{% if jvm_version() == 'dev' %}
**This documentation is for the latest development version of Jelly-JVM** – it is not considered stable. If you are looking for the documentation of a stable release, use the version selector on the left of the top navigation bar. See: [latest stable version](https://w3id.org/jelly/jelly-jvm/stable).
{% else %}
**This documentation is for Jelly-JVM version {{ jvm_version() }}**, which uses the [Jelly protocol version {{ proto_version() }}]({{ proto_link('specification') }}). If you are looking for the documentation of a different version, use the version selector on the left of the top navigation bar. See: [latest stable version](https://w3id.org/jelly/jelly-jvm/stable), [latest development version](https://w3id.org/jelly/jelly-jvm/dev).
{% endif %}

## Library modules

The implementation is split into a few modules that can be used separately:

- `jelly-core` – core generic code for serializing/deserializing Jelly data. You need an additional module (like `jelly-jena`) to integrate it with a specific RDF library.
    - {{ module_badges('core') }}

- `jelly-jena` – interop code for the [Apache Jena](https://jena.apache.org/) library. **[:octicons-arrow-right-24: Learn more](user/jena.md)**
    - {{ module_badges('jena') }}

- `jelly-rdf4j` – interop code for the [Eclipse RDF4J](https://rdf4j.org/) library. **[:octicons-arrow-right-24: Learn more](user/rdf4j.md)**
    - {{ module_badges('rdf4j') }}

- `jelly-titanium-rdf-api` – integration with the [Titanium RDF API](https://github.com/filip26/titanium-rdf-api). **[:octicons-arrow-right-24: Learn more](user/titanium.md)**
    - {{ module_badges('titanium-rdf-api') }}

- `jelly-stream` – utilities for building [Reactive Streams](https://www.reactive-streams.org/) of RDF data, based on Pekko Streams. Useful for integrating with for example gRPC, Kafka, MQTT... **[:octicons-arrow-right-24: Learn more](user/reactive.md)**
    - {{ module_badges('stream') }}

- `jelly-grpc` – implementation of a gRPC client and server for the [Jelly gRPC streaming protocol]({{ proto_link( 'specification/streaming' ) }}). **[:octicons-arrow-right-24: Learn more](user/grpc.md)**
    - {{ module_badges('grpc') }}

## Plugin JARs

We also publish plugin JARs which allow you to use Jelly-JVM with [Apache Jena](https://jena.apache.org/) and [RDF4J](https://rdf4j.org/) just by dropping the JARs into the classpath. **[Find out more about using the plugins](getting-started-plugins.md)**.

## Compatibility

Jelly-JVM is compatible with Java 17 and newer. Java 17, 21, and 24 are tested in CI and are guaranteed to work. Jelly is built with [Scala 3 LTS releases](https://www.scala-lang.org/blog/2022/08/17/long-term-compatibility-plans.html).

The following table shows the compatibility of the Jelly-JVM implementation with other libraries:

| Jelly-JVM | Scala                     | Java | RDF4J | Apache Jena | Apache Pekko |
| --------- | :-----------------------: | :--: | :---: | :---------: | :----------: |
| [2.0.x](https://w3id.org/jelly/jelly-jvm/2.0.x) – [**{{ jvm_package_version_minor() }}**](https://w3id.org/jelly/jelly-jvm/{{ jvm_package_version_minor() }}) | 3.3.x (LTS)               | 17+  | 5.x.x | 5.x.x       | 1.1.x        |
| [1.0.x](https://w3id.org/jelly/jelly-jvm/1.0.x)     | 3.3.x (LTS)<br>2.13.x[^1] | 11+  | 4.x.x | 4.x.x       | 1.0.x        |

See the **[compatibility policy](user/compatibility.md)** for more details and the **[release notes on GitHub](https://github.com/Jelly-RDF/jelly-jvm/releases)**.

## Documentation

Below is a list of all documentation pages about Jelly-JVM. You can also browse the Javadoc using the badges in the module list above. The documentation uses examples written in Scala, but the libraries can be used from Java as well.

- [Getting started with Jena/RDF4J plugins](getting-started-plugins.md) – how to use Jelly-JVM as a plugin for Apache Jena or RDF4J, without writing any code.
- [Getting started for application developers](getting-started-devs.md) – how to use Jelly-JVM in code.
- User guide
    - [Apache Jena integration](user/jena.md)
    - [RDF4J integration](user/rdf4j.md)
    - [Titanium RDF API integration](user/titanium.md) **(new in 2.9.0!)**
    - [Reactive streaming](user/reactive.md)
    - [gRPC](user/grpc.md)
    - [Useful utilities](user/utilities.md)
    - [Compatibility policy](user/compatibility.md)
- Developer guide
    - [Releases](dev/releases.md)
    - [Implementing Jelly for other libraries](dev/implementing.md)
- [Contributing to Jelly-JVM](contributing/index.md)
- [License and citation](licensing.md)
- [Release notes on GitHub](https://github.com/Jelly-RDF/jelly-jvm/releases)
- [Main Jelly website]({{ proto_link( '' ) }}) – including the Jelly protocol specification and explanation of the various stream types.

## Commercial and community support

**[NeverBlink](https://neverblink.eu)** provides commercial support services for Jelly, including implementing custom features, system integrations, implementations for new frameworks, benchmarking, and more.

Community support is available on the **[Jelly Discord chat](https://discord.gg/A8sN5XwVa5)**.

----

The development of the Jelly protocol, its implementations, and supporting tooling was co-funded by the European Union. **[More details]({{ proto_link( 'licensing/projects' ) }})**.

![European Funds for Smart Economy, Republic of Poland, Co-funded by the European Union](assets/featured/feng_rp_eu.png)

[^1]: Scala 2.13-compatible builds of Jelly-JVM are available for Jelly-JVM 1.0.x. Scala 2 support was removed in subsequent versions. [See more details](https://w3id.org/jelly/jelly-jvm/1.0.x/user/scala2).
