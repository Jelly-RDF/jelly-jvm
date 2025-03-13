// Scala 2 version used for meta-programming – transforming the generated proto classes.
// Not used to compile any of the Jelly projects.
lazy val scala2MetaVersion = "2.13.15"

ThisBuild / scalaVersion := "3.3.5"
ThisBuild / organization := "eu.ostrzyciel.jelly"
ThisBuild / homepage := Some(url("https://w3id.org/jelly/jelly-jvm"))
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / autoAPIMappings := true
ThisBuild / developers := List(
  Developer(
    "Ostrzyciel",
    "Piotr Sowiński",
    "psowinski17@gmail.com",
    url("https://github.com/Ostrzyciel"),
  ),
)
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

sonatypeProfileName := "eu.ostrzyciel"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

lazy val pekkoV = "1.1.3"
lazy val pekkoGrpcV = "1.1.1"
lazy val jenaV = "5.3.0"
lazy val rdf4jV = "5.1.2"
lazy val titaniumApiV = "1.0.0"
lazy val titaniumNqV = "1.0.0"
// !! When updating ScalaPB also change the version of the plugin in plugins.sbt
lazy val scalapbV = "0.11.17"
lazy val protobufV = "4.30.0"

// List of exclusions for the grpc module and its dependencies
lazy val grpcExclusions = Seq(
  ExclusionRule(organization = "org.scala-lang.modules", name = "scala-collection-compat_2.13"),
  ExclusionRule(organization = "com.thesamet.scalapb", name = "lenses_2.13"),
  ExclusionRule(organization = "com.thesamet.scalapb", name = "scalapb-runtime_2.13"),
)

// Dependencies used for subprojects that generate Scala code from protobuf files
lazy val protobufCompilerDeps = Seq(
  "com.thesamet.scalapb" %% "compilerplugin" % scalapbV,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbV % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbV,
  "com.google.protobuf" % "protobuf-java" % protobufV,
)

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  ),
  scalacOptions ++= Seq(
    "-Werror",
    "-feature",
    "-deprecation",
    "-unchecked",
  ),
  javacOptions ++= Seq(
    "-Werror",
    // TODO: enable more warnings
    "-Xlint:unchecked",
  ),
  assemblyJarName := s"${name.value}.jar",
  assemblyMergeStrategy := {
    case x if x.endsWith("module-info.class") => MergeStrategy.concat
    case x => assemblyMergeStrategy.value(x)
  },
  crossVersion := CrossVersion.binary,
)

// Intermediate project that generates the Scala code from the protobuf files
lazy val rdfProtos = (project in file("rdf-protos"))
  .settings(
    name := "jelly-scalameta",
    libraryDependencies ++= protobufCompilerDeps,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    // Add the shared proto sources
    Compile / PB.protoSources ++= Seq(baseDirectory.value / "src" / "main" / "protobuf_shared"),
    Compile / PB.generate / excludeFilter := "grpc.proto",
    publishArtifact := false,
  )

lazy val core = (project in file("core"))
  .settings(
    name := "jelly-core",
    description := "Core code for serializing and deserializing RDF data in the Jelly format.",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbV,
      "com.google.protobuf" % "protobuf-java" % protobufV,
    ),
    // Add the generated proto classes after transforming them with Scalameta
    Compile / sourceGenerators += Def.task {
      Generator.gen(
        inputDir = (rdfProtos / target).value / ("scala-" + scalaVersion.value) / "src_managed" / "main",
        outputDir = sourceManaged.value / "scalapb",
      )
    }.dependsOn(rdfProtos / Compile / PB.generate),
    commonSettings,
  )

lazy val jena = (project in file("jena"))
  .settings(
    name := "jelly-jena",
    description := "Jelly parsers, serializers, and other utilities for Apache Jena.",
    libraryDependencies ++= Seq(
      "org.apache.jena" % "jena-core" % jenaV,
      "org.apache.jena" % "jena-arq" % jenaV,
      // Integration with Fuseki is optional, so include this dep as "provided"
      "org.apache.jena" % "jena-fuseki-main" % jenaV % "provided,test",
    ),
    commonSettings,
  )
  .dependsOn(core)

// jena-plugin is a dummy directory that contains only a symlink (src) to the source code
// in the jena directory. This way sbt won't shout at us for having two projects in the
// same directory.
// Same applies to rdf4j-plugin below.
lazy val jenaPlugin = (project in file("jena-plugin"))
  .settings(
    name := "jelly-jena-plugin",
    libraryDependencies ++= Seq(
      // Use the "provided" scope to not include the Jena dependencies in the plugin JAR
      "org.apache.jena" % "jena-core" % jenaV % "provided,test",
      "org.apache.jena" % "jena-arq" % jenaV % "provided,test",
      "org.apache.jena" % "jena-fuseki-main" % jenaV % "provided,test",
    ),
    // Do not publish this to Maven – we will separately do sbt assembly and publish to GitHub
    publishArtifact := false,
    commonSettings,
  )
  .dependsOn(core)

lazy val rdf4j = (project in file("rdf4j"))
  .settings(
    name := "jelly-rdf4j",
    description := "Jelly parsers, serializers, and other utilities for RDF4J.",
    libraryDependencies ++= Seq(
      "org.eclipse.rdf4j" % "rdf4j-model" % rdf4jV,
      "org.eclipse.rdf4j" % "rdf4j-rio-api" % rdf4jV,
    ),
    commonSettings,
  )
  .dependsOn(core)

lazy val rdf4jPlugin = (project in file("rdf4j-plugin"))
  .settings(
    name := "jelly-rdf4j-plugin",
    libraryDependencies ++= Seq(
      // Use the "provided" scope to not include the RDF4J dependencies in the plugin JAR
      "org.eclipse.rdf4j" % "rdf4j-model" % rdf4jV % "provided,test",
      "org.eclipse.rdf4j" % "rdf4j-rio-api" % rdf4jV % "provided,test",
    ),
    // Do not publish this to Maven – we will separately do sbt assembly and publish to GitHub
    publishArtifact := false,
    commonSettings,
  )
  .dependsOn(core)

lazy val titaniumRdfApi = (project in file("titanium-rdf-api"))
  .settings(
    name := "jelly-titanium-rdf-api",
    description := "Implementation of the Titanium RDF API for Jelly-JVM. " +
      "See: https://github.com/filip26/titanium-rdf-api \n\n" +
      "If you are already using RDF4J or Jena, it's recommended to use their dedicated " +
      "integration modules instead of this one for better performance and more features.",
    libraryDependencies ++= Seq(
      "com.apicatalog" % "titanium-rdf-api" % titaniumApiV,
    ),
    commonSettings,
  )
  .dependsOn(core)

lazy val stream = (project in file("stream"))
  .settings(
    name := "jelly-stream",
    description := "Utilities for using the Jelly RDF serialization format with Reactive Streams (via Apache Pekko).",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-actor-typed" % pekkoV,
      "org.apache.pekko" %% "pekko-stream-typed" % pekkoV,
    ),
    commonSettings,
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val grpc = (project in file("grpc"))
  .enablePlugins(PekkoGrpcPlugin)
  .settings(
    name := "jelly-grpc",
    description := "Implementation of a gRPC client and server for the Jelly gRPC streaming protocol.",
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "org.apache.pekko" %% "pekko-actor-typed" % pekkoV,
      "org.apache.pekko" %% "pekko-discovery" % pekkoV,
      "org.apache.pekko" %% "pekko-stream-typed" % pekkoV,
      "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoV % Test,
      "org.apache.pekko" %% "pekko-grpc-runtime" % pekkoGrpcV,
    ),
    // Add the shared proto sources
    Compile / PB.protoSources ++= Seq(
      (rdfProtos / baseDirectory).value / "src" / "main" / "protobuf_shared",
      (rdfProtos / baseDirectory).value / "src" / "main" / "protobuf",
    ),
    Compile / PB.generate / excludeFilter := "rdf.proto",
    excludeDependencies ++= grpcExclusions,
    commonSettings,
  )
  .dependsOn(stream % "test->compile")
  .dependsOn(core % "compile->compile;test->test;protobuf->protobuf")
  .dependsOn(rdfProtos % "protobuf->protobuf")

lazy val integrationTests = (project in file("integration-tests"))
  .settings(
    publishArtifact := false,
    name := "jelly-integration-tests",
    libraryDependencies ++= Seq(
      "org.eclipse.rdf4j" % "rdf4j-rio-turtle" % rdf4jV % Test,
      "org.eclipse.rdf4j" % "rdf4j-rio-nquads" % rdf4jV % Test,
      "com.apicatalog" % "titanium-rdf-n-quads" % titaniumNqV % Test,
      "com.apicatalog" % "titanium-json-ld" % "1.6.0" % Test,
    ),
    libraryDependencies ++= protobufCompilerDeps,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    commonSettings,
  )
  .dependsOn(stream, jena % "compile->compile;test->test", rdf4j, titaniumRdfApi)

lazy val examples = (project in file("examples"))
  .settings(
    publishArtifact := false,
    name := "jelly-examples",
    libraryDependencies ++= Seq(
      "org.eclipse.rdf4j" % "rdf4j-rio-turtle" % rdf4jV,
      "org.eclipse.rdf4j" % "rdf4j-rio-nquads" % rdf4jV,
      "com.apicatalog" % "titanium-rdf-n-quads" % titaniumNqV,
    ),
    excludeDependencies ++= grpcExclusions,
    commonSettings,
  )
  .dependsOn(grpc, stream, jena % "compile->compile;test->test", rdf4j, titaniumRdfApi)
