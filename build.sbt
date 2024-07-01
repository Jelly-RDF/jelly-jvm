ThisBuild / scalaVersion := "3.3.3"
ThisBuild / organization := "eu.ostrzyciel.jelly"
ThisBuild / homepage := Some(url("https://github.com/Jelly-RDF/jelly-jvm"))
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

lazy val pekkoV = "1.0.3"
lazy val pekkoGrpcV = "1.0.2"
lazy val jenaV = "4.10.0"
lazy val rdf4jV = "4.3.12"
// Must be synchronized to the version used by Pekko gRPC
// See: https://mvnrepository.com/artifact/org.apache.pekko/pekko-grpc-runtime_3
// When updating also change the version in plugins.sbt
lazy val scalapbV = "0.11.13"

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  ),
)

lazy val core = (project in file("core"))
  .settings(
    name := "jelly-core",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "compilerplugin" % scalapbV,
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbV % "protobuf",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbV,
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
    ),
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    // Add the shared proto sources
    Compile / PB.protoSources ++= Seq(baseDirectory.value / "src" / "main" / "protobuf_shared"),
    Compile / PB.generate / excludeFilter := "grpc.proto",
    commonSettings,
  )

lazy val jena = (project in file("jena"))
  .settings(
    name := "jelly-jena",
    libraryDependencies ++= Seq(
      "org.apache.jena" % "jena-core" % jenaV,
      "org.apache.jena" % "jena-arq" % jenaV,
    ),
    commonSettings,
  )
  .dependsOn(core)

lazy val rdf4j = (project in file("rdf4j"))
  .settings(
    name := "jelly-rdf4j",
    libraryDependencies ++= Seq(
      "org.eclipse.rdf4j" % "rdf4j-model" % rdf4jV,
      "org.eclipse.rdf4j" % "rdf4j-rio-api" % rdf4jV,
    ),
    commonSettings,
  )
  .dependsOn(core)

lazy val stream = (project in file("stream"))
  .settings(
    name := "jelly-stream",
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
      (core / baseDirectory).value / "src" / "main" / "protobuf_shared",
      (core / baseDirectory).value / "src" / "main" / "protobuf",
    ),
    Compile / PB.generate / excludeFilter := "rdf.proto",
    commonSettings,
  )
  .dependsOn(stream % "test->compile")
  .dependsOn(core % "compile->compile;test->test;protobuf->protobuf")

lazy val integrationTests = (project in file("integration-tests"))
  .settings(
    publishArtifact := false,
    name := "jelly-integration-tests",
    libraryDependencies ++= Seq(
      "org.eclipse.rdf4j" % "rdf4j-rio-turtle" % rdf4jV % Test,
      "org.eclipse.rdf4j" % "rdf4j-rio-nquads" % rdf4jV % Test,
    ),
    commonSettings,
  )
  .dependsOn(stream, rdf4j, jena)

lazy val examples = (project in file("examples"))
  .settings(
    publishArtifact := false,
    name := "jelly-examples",
    libraryDependencies ++= Seq(
      "org.eclipse.rdf4j" % "rdf4j-rio-turtle" % rdf4jV,
      "org.eclipse.rdf4j" % "rdf4j-rio-nquads" % rdf4jV,
    ),
    commonSettings,
  )
  .dependsOn(grpc, stream, rdf4j, jena)
