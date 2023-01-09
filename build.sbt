ThisBuild / scalaVersion := "3.2.1"
ThisBuild / organization := "eu.ostrzyciel.jelly"
ThisBuild / sonatypeProfileName := "eu.ostrzyciel"
ThisBuild / homepage := Some(url("https://github.com/Jelly-RDF/jelly-jvm"))
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / developers := List(
  Developer(
    "Ostrzyciel",
    "Piotr Sowiński",
    "psowinski17@gmail.com",
    url("https://github.com/Ostrzyciel"),
  ),
)

sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

// !!! 2.6.x is the last release with the Apache license. Do not upgrade to Akka 2.7.x
lazy val akkaV = "2.6.20"
lazy val jenaV = "4.6.1"
lazy val rdf4jV = "4.2.2"
lazy val scalapbV = "0.11.12"

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.14" % Test,
  ),
  excludeDependencies ++= Seq(
    "com.thesamet.scalapb" % "scalapb-runtime_2.13",
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
    ),
    commonSettings,
  )
  .dependsOn(core)

lazy val stream = (project in file("stream"))
  .settings(
    name := "jelly-stream",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaV,
      "com.typesafe.akka" %% "akka-stream-typed" % akkaV,
    ).map(_.cross(CrossVersion.for3Use2_13)),
    commonSettings,
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val grpc = (project in file("grpc"))
  .enablePlugins(AkkaGrpcPlugin)
  .settings(
    name := "jelly-grpc",
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaV,
      "com.typesafe.akka" %% "akka-discovery" % akkaV,
      "com.typesafe.akka" %% "akka-stream-typed" % akkaV,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaV % Test,
      // 2.1.x is the last release with the Apache license
      "com.lightbend.akka.grpc" %% "akka-grpc-runtime" % "2.1.6",
    ).map(_.cross(CrossVersion.for3Use2_13)),
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
    name := "jelly-integration-tests",
    libraryDependencies ++= Seq(
      "org.eclipse.rdf4j" % "rdf4j-rio-turtle" % rdf4jV % Test,
      "org.eclipse.rdf4j" % "rdf4j-rio-nquads" % rdf4jV % Test,
    ),
    commonSettings,
  )
  .dependsOn(stream, rdf4j, jena)
