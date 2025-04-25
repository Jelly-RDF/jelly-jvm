addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.7")
addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "1.1.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.3")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")
addSbtPlugin("com.github.sbt" % "sbt-protobuf" % "0.8.2")

addDependencyTreePlugin

// org.xerial.sbt.sbt-sonatype
// TODO: remove when this is merged: https://github.com/xerial/sbt-sonatype/pull/583
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.13.0-SNAPSHOT-JELLY" from
  "https://github.com/Ostrzyciel/jar-hacks/releases/download/dev/sbt-sonatype-0.0.0-782-5a0c7256.jar")

lazy val scalapbV = "0.11.17"

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "compilerplugin" % scalapbV,
  // org.xerial.sbt.sbt-sonatype
  // TODO: remove when this is merged: https://github.com/xerial/sbt-sonatype/pull/583
  "org.wvlet.airframe" %% "airframe-http" % "24.12.2",
  "com.lumidion" %% "sonatype-central-client-sttp-core" % "0.3.0",
  "com.lumidion" %% "sonatype-central-client-upickle" % "0.3.0",
)
