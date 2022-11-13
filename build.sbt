name := "jelly-jvm"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.2.1"

lazy val jenaV = "4.6.1"
lazy val scalapbV = "0.11.12"

// !!! 2.1.x is the last release with the Apache license. Do not upgrade to Akka gRPC 2.2.0.
// addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.6")

lazy val commonSettings = Seq(

)

lazy val core = (project in file("core"))
  .settings(
    name := "core",
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
    name := "jena",
    libraryDependencies ++= Seq(
      "org.apache.jena" % "jena-core" % jenaV,
      "org.apache.jena" % "jena-arq" % jenaV,
    ),
    commonSettings,
  )
  .dependsOn(core)

lazy val rdf4j = (project in file("rdf4j"))
  .settings(
    name := "rdf4j",
    libraryDependencies ++= Seq(
      "org.eclipse.rdf4j" % "rdf4j-model" % "4.2.1",
    ),
    commonSettings,
  )
  .dependsOn(core)
