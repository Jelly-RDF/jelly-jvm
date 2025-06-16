# Compatibility policy

Jelly-JVM follows [Semantic Versioning 2.0.0](https://semver.org/), with MAJOR.MINOR.PATCH releases. Please see the compatibility table on the [main page](../index.md) for the current compatibility information. The documentation is versioned to match each Jelly-JVM MAJOR.MINOR version.

## JVM and Scala

The current version of Jelly-JVM is compatible with Java 17 and newer. Java 17, 21, and 24 are tested in CI and are guaranteed to work. We recommend using a recent release of [GraalVM](https://www.graalvm.org/) to get the best performance. If you need Java 11 support, you should use [Jelly-JVM 1.0.x](https://w3id.org/jelly/jelly-jvm/1.0.x).

The current version of Jelly-JVM has some modules (currently `jelly-pekko-stream`) built with [Scala 3 LTS releases](https://www.scala-lang.org/blog/2022/08/17/long-term-compatibility-plans.html) and support only Scala 3. 

Jelly-JVM 2.x.x was written entirely in Scala 3, using [Scala LTS releases](https://www.scala-lang.org/blog/2022/08/17/long-term-compatibility-plans.html).

## RDF libraries

Major-version upgrades of RDF4J and Apache Jena (e.g., updating from 4.0.x to 5.0.x) are done in Jelly-JVM MINOR releases. Jelly-JVM generally does not use any complex features of these libraries, so it should work with multiple versions without any problems.

If you do encounter any compatibility issues, please report them on the [issue tracker](https://github.com/Jelly-RDF/jelly-jvm/issues).

## Internal vs external APIs

Generally, all public classes and methods in Jelly-JVM are considered part of the public API. However, there are some exceptions.

Auto-generated classes in the `jelly-core` module, `eu.neverblink.jelly.core.proto.v1` package are not considered part of the public API, although we will avoid any incompatibilities where possible. These classes may change between MINOR releases.

## Backward and forward protocol compatibility

Jelly-JVM follows [the Jelly protocol's backward compatibility policy]({{ proto_link('specification/serialization#versioning') }}). This means that Jelly-JVM can read data serialized with older versions of Jelly. Backward compatibility is tested in CI – the code is in [BackCompatSpec.scala]({{ git_link('integration-tests/src/test/scala/eu/neverblink/jelly/integration_tests/rdf/BackCompatSpec.scala') }}).

Forward compatibility is provided only in a very limited manner in Jelly-JVM. The parser is guaranteed to only parse the stream options header and reject the rest of the stream, if the used protocol version is not supported. You may choose to disable this check and try to parse the rest of the data anyway, but this is most certainly **NOT** recommended and may lead to unexpected results. In general, Jelly-JVM will ignore any unknown fields in the stream, but any other changes in the protocol may lead to really "funny" errors. Forward compatibility is tested in CI – the code is in [ForwardCompatSpec.scala]({{ git_link('integration-tests/src/test/scala/eu/neverblink/jelly/integration_tests/rdf/ForwardCompatSpec.scala') }}).

## See also

- [Release notes on GitHub](https://github.com/Jelly-RDF/jelly-jvm/releases)
- [Making Jelly-JVM releases](../contributing/releases.md)
- [Contributing to Jelly-JVM](../contributing/index.md)
