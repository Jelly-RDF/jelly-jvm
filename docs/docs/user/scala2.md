# Scala 2 builds

Jelly-JVM is written in Scala 3, but we also provide packages that _mostly_ work with Scala 2.13. They include only Scala 2 dependencies and are published to Maven Central.

## How to use this?

- In your project enable the Scala 2.13 TASTy reader – [read more in Scala docs](https://docs.scala-lang.org/scala3/guides/migration/compatibility-classpath.html#the-scala-213-tasty-reader).
- Add Jelly-JVM dependencies with the `_2.13` suffilx, e.g., `eu.ostrzyciel.jelly %% jelly-core % 0.14.0` in sbt (assuming you are building a Scala 2.13 project).
- If you get conflicting Scala version suffix errors from sbt, you may need to add exclusion rules like this:
    ```scala
    libraryDependencies ++= List(
      "eu.ostrzyciel.jelly" %% "jelly-stream" % jellyVersion,
      "eu.ostrzyciel.jelly" %% "jelly-grpc" % jellyVersion,
      "eu.ostrzyciel.jelly" %% "jelly-rdf4j" % jellyVersion,
    ).map(_ excludeAll (
      // These are two leftover Scala 3 dependencies that are improperly handled in the pseudo-Scala 2
      // version of Jelly due to a quirk with how the scalapb plugin works. We must exclude them here.
      ExclusionRule(organization = "org.apache.pekko", name = "pekko-grpc-runtime_3"),
      ExclusionRule(organization = "com.thesamet.scalapb", name = "scalapb-runtime_3"),
    ))
    ```

That's it!

## Is this supported?

Kind of. It does work (we tested it), but it's not a perfect solution, as Jelly-JVM was from the start designed only for Scala 3. **Support for Scala 2 will be dropped in Jelly-JVM 1.2.0** – the support will remain as-is in versions 1.0.x and 1.1.x.

## Technical notes – maintainer guide

The thing is a rather ugly hack in sbt. On the one hand, we need sbt to think we need Scala 2 dependencies for one build, and that we want to publish these artifacts with the `_2.13` suffix in Maven. On the other, this is still Scala 3 code and it _must_ be compiled with the Scala 3 compiler.

The solution looks like [this](https://github.com/Jelly-RDF/jelly-jvm/blob/b9f4083671d6a1d4ee4861061e9bea4b1460adea/build.sbt#L1) – we trick sbt, by having two Scala 3 builds (one with a slightly different version), and then [treating one of them differently](https://github.com/Jelly-RDF/jelly-jvm/blob/b9f4083671d6a1d4ee4861061e9bea4b1460adea/build.sbt#L97) using conditionals. Don't judge me.
