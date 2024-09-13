# Compatibility policy

Jelly-JVM follows [Semantic Versioning 2.0.0](https://semver.org/), with MAJOR.MINOR.PATCH releases. Please see the compatibility table on the [main page](../index.md) for the current compatibility information. The documentation is versioned to match each Jelly-JVM MAJOR.MINOR version.

## JVM and Scala

The current version of Jelly-JVM is compatible with Java 17 and newer. Java 17, 21, and 22 are tested in CI and are guaranteed to work. We recommend using a recent release of [GraalVM](https://www.graalvm.org/) to get the best performance. If you need Java 11 support, you should use [Jelly-JVM 1.0.x](https://w3id.org/jelly/jelly-jvm/1.0.x).

Jelly is built with [Scala 3 LTS releases](https://www.scala-lang.org/blog/2022/08/17/long-term-compatibility-plans.html), however, [Scala 2.13-compatible builds are available as well](scala2.md).

## RDF libraries

Major-version upgrades of RDF4J and Apache Jena (e.g., updating from 4.0.x to 5.0.x) are done in Jelly-JVM MINOR releases. Jelly-JVM generally does not use any complex features of these libraries, so it should work with multiple versions without any problems.

If you do encounter any compatibility issues, please report them on the [issue tracker](https://github.com/Jelly-RDF/jelly-jvm/issues).

## Internal vs external APIs

Generally, all public classes and methods in Jelly-JVM are considered part of the public API. However, there are some exceptions.

Auto-generated classes in the `jelly-core` module, `eu.ostrzyciel.jelly.core.proto.v1` package are not considered part of the public API, although we will avoid any incompatibilities where possible. These classes may change between MINOR releases.

## See also

- [Scala 2.13 builds](scala2.md)
- [Release notes on GitHub](https://github.com/Jelly-RDF/jelly-jvm/releases)
- [Making Jelly-JVM releases](../dev/releases.md)
- [Contributing to Jelly-JVM](../contributing.md)
