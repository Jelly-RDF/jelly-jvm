addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.7")
addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "1.1.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.2")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.0")
addDependencyTreePlugin

lazy val scalapbV = "0.11.17"

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % scalapbV
