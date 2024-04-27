addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.3")
addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "1.0.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")
addDependencyTreePlugin

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.12"
