addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.3")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.6")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.11")
addDependencyTreePlugin

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.12"
