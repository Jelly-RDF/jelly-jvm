name := "jelly-jvm"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.2.1"

// !!! 2.6.x is the last release with the Apache license. Do not upgrade to Akka 2.7.x
lazy val akkaV = "2.6.20"
lazy val jenaV = "4.6.1"
lazy val scalapbV = "0.11.12"

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.14" % Test,
  )
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
      "org.eclipse.rdf4j" % "rdf4j-model" % "4.2.1",
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
    ),
    commonSettings,
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val integrationTests = (project in file("integration-tests"))
  .settings(
    name := "jelly-integration-tests",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % Test,
    ),
    commonSettings,
  )
  .dependsOn(stream, rdf4j, jena)
