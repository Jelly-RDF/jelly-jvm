# Compatibility policy

Jelly-JVM follows [Semantic Versioning 2.0.0](https://semver.org/), with MAJOR.MINOR.PATCH releases. Please see the compatibility table on the [main page](index.md) for the current compatibility information. The documentation is versioned to match each Jelly-JVM MAJOR.MINOR version.

## JVM and Scala

The current version of Jelly-JVM is compatible with Java 11 and newer. Java 11, 17, and 21 are tested in CI and are guaranteed to work. We recommend using a recent release of [GraalVM](https://www.graalvm.org/) to get the best performance.

Jelly is built with [Scala 3 LTS releases](https://www.scala-lang.org/blog/2022/08/17/long-term-compatibility-plans.html).

## RDF libraries

Major-version upgrades of RDF4J and Apache Jena (e.g., updating from 4.0.x to 5.0.x) are done in Jelly-JVM MINOR releases. Jelly-JVM generally does not use any complex features of these libraries, so you can expect it to work with multiple versions without any problems. For example, although Jelly-JVM 1.0.x officially supports only Jena 4.x.x, it works fine with 5.0.x.

If you do encounter any compatibility issues, please report them on the [issue tracker](https://github.com/Jelly-RDF/jelly-jvm/issues).

### Apache Jena 4.x.x

Jelly-JVM 1.0.x only supports the RDF 1.1 mode of Apache Jena (enabled by default in Jena 4.x.x). The RDF 1.0 mode was removed entirely in Apache Jena 5.

## Internal vs external APIs

Generally, all public classes and methods in Jelly-JVM are considered part of the public API. However, there are some exceptions.

Auto-generated classes in the `jelly-core` module, `eu.ostrzyciel.jelly.core.proto.v1` package are not considered part of the public API, although we will avoid any incompatibilities where possible. These classes may change between MINOR releases.

## See also

- [Contributing to Jelly-JVM](../contributing.md)
- [Making Jelly-JVM releases](../dev/releases.md)
