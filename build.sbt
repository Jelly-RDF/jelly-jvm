import scala.language.postfixOps
import scala.sys.process.*

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "eu.neverblink.jelly"
ThisBuild / homepage := Some(url("https://w3id.org/jelly/jelly-jvm"))
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / autoAPIMappings := true
ThisBuild / developers := List(
  Developer(
    "Ostrzyciel",
    "Piotr Sowiński",
    "piotr@neverblink.eu",
    url("https://github.com/Ostrzyciel"),
  ),
)
// Allow scalatest to control the logging output
Test / logBuffered := false

lazy val pekkoV = "1.1.5"
lazy val pekkoGrpcV = "1.1.1"
lazy val jenaV = "5.4.0"
lazy val rdf4jV = "5.1.4"
lazy val titaniumApiV = "1.0.0"
lazy val titaniumNqV = "1.0.2"
lazy val protobufV = "4.31.1"
lazy val javapoetV = "0.7.0"
lazy val jmhV = "1.37"
lazy val grpcV = "1.74.0"

lazy val jellyCliV = "0.4.5"

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
    "-source", "17",
    "-target", "17",
    "-Werror",
    // TODO: enable more warnings
  ),
  // Explicitly specify the options for javadoc, otherwise sbt will pass all javacOptions to it
  // which will cause an error.
  Compile / doc / javacOptions := Seq("-source", "17"),
  assemblyJarName := s"${name.value}.jar",
  assemblyMergeStrategy := {
    case x if x.endsWith("module-info.class") => MergeStrategy.concat
    case x => assemblyMergeStrategy.value(x)
  },
  crossVersion := CrossVersion.binary,
  jacocoAggregateReportSettings := JacocoReportSettings(
    formats = Seq(JacocoReportFormats.XML)
  ),
  Test / javaOptions ++= Seq(
    // Disable Jacoco instrumentation by default, to make test execution faster and to make it pass
    // on JDK 22+ where Jacoco instrumentation is not supported.
    "-Djacoco.skip=true"
  )
)

// Shared settings for all Java-only modules
lazy val commonJavaSettings = Seq(
  // Disable Scala features for this module
  crossPaths := false,
  crossVersion := CrossVersion.disabled,
  autoScalaLibrary := false,
  libraryDependencies ++= Seq(
    // Add test-time dependency on Scala for ScalaTest
    "org.scala-lang" %% "scala3-library" % (ThisBuild / scalaVersion).value % Test,
  ),
)

lazy val prepareGoogleProtos = taskKey[Seq[File]](
  "Copies and modifies proto files before Google protoc-java compilation"
)
lazy val generatePluginRunScript = taskKey[Seq[File]]("Generate the run script for the protoc plugin")
lazy val downloadJellyCli = taskKey[File]("Downloads Jelly CLI binary file")

/**
 * Used for core*ProtosGoogle modules.
 * Copies the proto files from the protobuf submodule to the protoc input directory,
 * while applying some options to the proto files.
 */
def doPrepareGoogleProtos(baseDir: File): Seq[File] = {
  val inputDir = (baseDir / ".." / "submodules" / "protobuf" / "proto").getAbsoluteFile
  val outputDir = (baseDir / "src" / "main" / "protobuf").getAbsoluteFile
  // Make output dir if not exists
  IO.createDirectory(outputDir)
  // Clean the output directory
  IO.delete(IO.listFiles(outputDir).filterNot(_.getName == ".gitkeep"))
  val protoFiles = (inputDir ** "*.proto").get
  protoFiles
    .map { file =>
      // Copy the file to the output directory
      val outputFile = outputDir / file.relativeTo(inputDir).get.getPath
      IO.copyFile(file, outputFile)
      outputFile
    }
    .map { file =>
      // Append java options to the file
      val outPackage = "eu.neverblink.jelly.core.proto.google.v1" +
        (if (file.getName == "patch.proto") ".patch" else "")
      val content = IO.read(file)
      val newContent = content +
        f"""
          |option java_multiple_files = true;
          |option java_package = "$outPackage";
          |option optimize_for = SPEED;
          |""".stripMargin
      IO.write(file, newContent)
      file
    }
  // Return the list of generated files
  protoFiles.map { file =>
    val outputFile = outputDir / file.relativeTo(inputDir).get.getPath
    outputFile
  }
}

// .proto -> .java protoc compiler plugin
lazy val crunchyProtocPlugin = (project in file("crunchy-protoc-plugin"))
  .settings(
    name := "crunchy-protoc-plugin",
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % protobufV,
      "com.palantir.javapoet" % "javapoet" % javapoetV,
    ),
    generatePluginRunScript := Def.task {
      val targetDir = (Compile / assembly / assemblyOutputPath).value.getParentFile.getParentFile
      val script = targetDir / "crunchy-protoc-plugin"
      println(s"Generating script for protoc plugin at ${script.getAbsolutePath}")
      val cp = (Runtime / fullClasspath).value
        .map(_.data.getAbsolutePath)
        .mkString(":")
      val content =
        s"""#!/bin/bash
           |java -cp "$cp" eu.neverblink.protoc.java.CrunchyProtocPlugin
           |""".stripMargin
      IO.write(script, content)
      script.setExecutable(true)
      Seq(script)
    }.dependsOn(Compile / compile).value,
    publishArtifact := false,
    commonSettings,
  )

def runProtoc(
  pluginArgsFilePath: File,
  pluginPath: File,
  originalRunner: Seq[String] => Int
): Seq[String] => Int = {
  (args: Seq[String]) => {
    val pluginOptions = IO.read(pluginArgsFilePath).replaceAll("\\s", "")
    val javaPluginArg = args.indexWhere(_.startsWith("--java_out="))
    val newArgs = args.slice(0, javaPluginArg) ++
      Seq(
        "--plugin=protoc-gen-crunchy=" +
          pluginPath.getAbsolutePath,
        args(javaPluginArg).replace("--java_out=", s"--crunchy_out=$pluginOptions:"),
      ) ++ args.slice(javaPluginArg + 1, args.length)
    println("Invoking protoc...")
    // println(newArgs)
    originalRunner(newArgs)
  }
}

def doDownloadJellyCli(targetDir: File): File = {
  targetDir.mkdirs()

  val targetFile = targetDir / "jelly-cli"
  // Very dumb check for if the file exists and its size is not 0,
  // helps on trains with unstable coverage and high speeds.
  if (targetFile.exists() && targetFile.length() > 0) {
    println(s"Will not attempt to download Jelly CLI (located ${targetFile.getAbsolutePath}) as it exists. If tests fail, try cleaning the project files.")
    targetFile.setExecutable(true)
    return targetFile
  }

  println(s"Downloading Jelly CLI v$jellyCliV")

  val architecture = System.getProperty("os.arch") match {
    case "aarch64" | "arm64" => "arm64"
    case "x86_64" | "amd64" => "x86_64"
    case _ => throw new RuntimeException(s"Unsupported architecture: ${System.getProperty("os.arch")}")
  }

  val os = System.getProperty("os.name").toLowerCase match {
    case os if os.contains("windows") => "windows"
    case os if os.contains("mac") => "mac"
    case os if os.contains("linux") => "linux"
    case _ => throw new RuntimeException(s"Unsupported operating system: ${System.getProperty("os.name")}")
  }

  url(s"https://github.com/Jelly-RDF/cli/releases/download/v$jellyCliV/jelly-cli-$os-$architecture") #> targetFile !

  if (!targetFile.exists()) {
    throw new RuntimeException(
      s"Failed to download Jelly CLI to ${targetFile.getAbsolutePath}. " +
        "Please check your internet connection and try again."
    )
  }

  targetFile.setExecutable(true)
  targetFile
}

val grpcJavaSourceFiles = Seq("Grpc.java", "RdfStreamSubscribe.java", "RdfStreamReceived.java")

// Intermediate project that generates the Java code from the protobuf files
lazy val rdfProtos = (project in file("rdf-protos"))
  .enablePlugins(ProtobufPlugin)
  .settings(
    name := "jelly-protos",
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % protobufV,
    ),
    // Don't compile this project
    Compile / compile / skip := true,
    // Exclusions to make sure IntelliJ doesn't try to compile the intermediate project
    Compile / sourceDirectories := Nil,
    Compile / managedSourceDirectories := Nil,
    ProtobufConfig / protobufRunProtoc := Def.task {
      runProtoc(
        (Compile / sourceDirectory).value / "args.txt",
        (crunchyProtocPlugin / Compile / assembly / assemblyOutputPath).value
          .getParentFile.getParentFile / "crunchy-protoc-plugin",
        (ProtobufConfig / protobufRunProtoc).value,
      )
    }.dependsOn(crunchyProtocPlugin / generatePluginRunScript).value,
    publishArtifact := false,
  )

lazy val core = (project in file("core"))
  .settings(
    name := "jelly-core",
    description := "Core code for serializing and deserializing RDF data in the Jelly format.",
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % protobufV,
    ),
    Compile / sourceGenerators += Def.task {
      // Copy from the managed source directory to the output directory
      val inputDir = (rdfProtos / target).value / ("scala-" + scalaVersion.value) /
        "src_managed" / "main" / "compiled_protobuf" /
        "eu" / "neverblink" / "jelly" / "core" / "proto" / "v1"

      val outputDir = sourceManaged.value / "main" /
        "eu" / "neverblink" / "jelly" / "core" / "proto" / "v1"

      val javaFiles = (inputDir * "*.java").get
      javaFiles
        .filterNot { file => grpcJavaSourceFiles.contains(file.getName) }
        .map { file =>
          val outputFile = outputDir / file.relativeTo(inputDir).get.getPath
          IO.copyFile(file, outputFile)
          outputFile
        }
    }.dependsOn(rdfProtos / ProtobufConfig / protobufGenerate),
    Compile / sourceManaged := sourceManaged.value / "main",
    commonSettings,
    commonJavaSettings,
  )
  .dependsOn(
    // Test-time dependency on Google protos for ProtoAuxiliarySpec
    coreProtosGoogle % "test->compile",
  )

lazy val coreProtosGoogle = (project in file("core-protos-google"))
  .enablePlugins(ProtobufPlugin)
  .settings(
    name := "jelly-core-protos-google",
    description := "Optional proto classes for Jelly-RDF (rdf.proto) compiled with Google's " +
      "official Java protoc plugin. This is not needed, unless you need some functionality " +
      "that is only available with the more heavyweight, Google-style proto classes, like " +
      "support for the Protobuf Text Format.",
    libraryDependencies ++= Seq("com.google.protobuf" % "protobuf-java" % protobufV),
    prepareGoogleProtos := { doPrepareGoogleProtos(baseDirectory.value) },
    Compile / compile := (Compile / compile).dependsOn(prepareGoogleProtos).value,
    ProtobufConfig / protobufRunProtoc := (ProtobufConfig / protobufRunProtoc).dependsOn(prepareGoogleProtos).value,
    ProtobufConfig / protobufIncludeFilters := Seq(Glob(baseDirectory.value.toPath) / "**" / "rdf.proto"),
    // Don't throw errors, because Google's protoc generates code with a lot of warnings
    javacOptions := javacOptions.value.filterNot(_ == "-Werror"),
    commonSettings,
    commonJavaSettings,
  )

lazy val corePatch = (project in file("core-patch"))
  .settings(
    name := "jelly-core-patch",
    description := "Core code for the RDF Patch Jelly extension.",
    Compile / sourceGenerators += Def.task {
      // Copy from the managed source directory to the output directory
      val inputDir = (rdfProtos / target).value / ("scala-" + scalaVersion.value) /
        "src_managed" / "main" / "compiled_protobuf" /
        "eu" / "neverblink" / "jelly" / "core" / "proto" / "v1" / "patch"
      val outputDir = sourceManaged.value / "main" /
        "eu" / "neverblink" / "jelly" / "core" / "proto" / "v1" / "patch"
      val javaFiles = (inputDir * "*.java").get
      javaFiles.map { file =>
        val outputFile = outputDir / file.relativeTo(inputDir).get.getPath
        IO.copyFile(file, outputFile)
        outputFile
      }
    }.dependsOn(rdfProtos / ProtobufConfig / protobufGenerate),
    Compile / sourceManaged := sourceManaged.value / "main",
    commonSettings,
    commonJavaSettings,
  )
  .dependsOn(
    core % "compile->compile;test->test",
    // Test-time dependency on Google protos for PatchProtoSpec
    corePatchProtosGoogle % "test->compile",
  )

lazy val corePatchProtosGoogle = (project in file("core-patch-protos-google"))
  .enablePlugins(ProtobufPlugin)
  .settings(
    name := "jelly-core-patch-protos-google",
    description := "Optional proto classes for Jelly-Patch (patch.proto) compiled with Google's " +
      "official Java protoc plugin. This is not needed, unless you need some functionality " +
      "that is only available with the more heavyweight, Google-style proto classes, like " +
      "support for the Protobuf Text Format.",
    libraryDependencies ++= Seq("com.google.protobuf" % "protobuf-java" % protobufV),
    prepareGoogleProtos := { doPrepareGoogleProtos(baseDirectory.value) },
    Compile / compile := (Compile / compile).dependsOn(prepareGoogleProtos).value,
    ProtobufConfig / protobufRunProtoc := (ProtobufConfig / protobufRunProtoc).dependsOn(prepareGoogleProtos).value,
    ProtobufConfig / protobufIncludeFilters := Seq(Glob(baseDirectory.value.toPath) / "**" / "patch.proto"),
    commonSettings,
    commonJavaSettings,
  ).dependsOn(coreProtosGoogle)

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
    commonJavaSettings,
  )
  .dependsOn(core)

lazy val jenaPatch = (project in file("jena-patch"))
  .settings(
    name := "jelly-jena-patch",
    description := "Jelly-Patch integration for Apache Jena.",
    libraryDependencies ++= Seq(
      "org.apache.jena" % "jena-rdfpatch" % jenaV,
    ),
    commonSettings,
    commonJavaSettings,
  )
  .dependsOn(corePatch, jena)

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
    commonJavaSettings,
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
    commonJavaSettings,
  )
  .dependsOn(core)

lazy val rdf4jPatch = (project in file("rdf4j-patch"))
  .settings(
    name := "jelly-rdf4j-patch",
    description := "Jelly-Patch integration for RDF4J.",
    commonSettings,
    commonJavaSettings,
  )
  .dependsOn(corePatch, rdf4j)

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
    commonJavaSettings,
  )
  .dependsOn(core)

lazy val titaniumRdfApi = (project in file("titanium-rdf-api"))
  .settings(
    name := "jelly-titanium-rdf-api",
    description := "Implementation of the Titanium RDF API for Jelly-JVM. " +
      "See: https://github.com/filip26/titanium-rdf-api \n\n" +
      "If you are already using RDF4J or Jena, it's recommended to use their dedicated " +
      "integration modules instead of this one for better performance and more features." +
      "\n\n This is the Java version of the Titanium RDF API adapter.",
    libraryDependencies ++= Seq(
      "com.apicatalog" % "titanium-rdf-api" % titaniumApiV,
    ),
    commonSettings,
    commonJavaSettings,
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val stream = (project in file("pekko-stream"))
  .settings(
    name := "jelly-pekko-stream",
    description := "Utilities for using the Jelly RDF serialization format with Reactive Streams (via Apache Pekko).",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-actor-typed" % pekkoV,
      "org.apache.pekko" %% "pekko-stream-typed" % pekkoV,
    ),
    commonSettings,
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val integrationTests = (project in file("integration-tests"))
  .enablePlugins(ProtobufPlugin)
  .settings(
    publishArtifact := false,
    name := "jelly-integration-tests",
    libraryDependencies ++= Seq(
      "org.eclipse.rdf4j" % "rdf4j-rio-turtle" % rdf4jV % Test,
      "org.eclipse.rdf4j" % "rdf4j-rio-nquads" % rdf4jV % Test,
      "org.eclipse.rdf4j" % "rdf4j-rio-trig" % rdf4jV % Test,
      "com.apicatalog" % "titanium-rdf-n-quads" % titaniumNqV % Test,
      "com.apicatalog" % "titanium-json-ld" % "1.6.0" % Test,
    ),
    libraryDependencies ++= Seq("com.google.protobuf" % "protobuf-java" % protobufV),
    Compile / compile := (Compile / compile).dependsOn(ProtobufConfig / protobufRunProtoc).value,
    ProtobufConfig / protobufIncludeFilters := Seq(Glob(baseDirectory.value.toPath) / "**" / "rdf.proto"),
    downloadJellyCli := { doDownloadJellyCli((Test / resourceManaged).value) },
    Test / resourceGenerators += Def.task {
      val cliBinary = downloadJellyCli.value
      Seq(cliBinary)
    },
    commonSettings,
  )
  .dependsOn(
    core % "compile->compile;test->test",
    jena % "compile->compile;test->test",
    jenaPatch,
    rdf4j,
    rdf4jPatch,
    titaniumRdfApi,
    stream
  )

lazy val examples = (project in file("examples"))
  .settings(
    publishArtifact := false,
    name := "jelly-examples",
    libraryDependencies ++= Seq(
      "org.eclipse.rdf4j" % "rdf4j-rio-turtle" % rdf4jV,
      "org.eclipse.rdf4j" % "rdf4j-rio-nquads" % rdf4jV,
      "com.apicatalog" % "titanium-rdf-n-quads" % titaniumNqV,
    ),
    commonSettings,
  )
  .dependsOn(
    core % "compile->compile;test->test",
    stream,
    jena % "compile->compile;test->test",
    rdf4j,
    titaniumRdfApi,
    grpc
  )

lazy val jmh = (project in file("jmh"))
  .enablePlugins(JmhPlugin)
  .settings(
    publishArtifact := false,
    name := "jelly-jmh",
    description := "JMH benchmarks for Jelly-JVM.",
    libraryDependencies ++= Seq(
      "org.openjdk.jmh" % "jmh-core" % jmhV,
      "org.openjdk.jmh" % "jmh-generator-annprocess" % jmhV,
    ),
    commonSettings,
  )
  .dependsOn(core, jena)


lazy val grpc = (project in file("pekko-grpc"))
  .settings(
    name := "jelly-pekko-grpc",
    description := "Implementation of a gRPC client and server for the Jelly gRPC streaming protocol.",
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "org.apache.pekko" %% "pekko-actor-typed" % pekkoV,
      "org.apache.pekko" %% "pekko-discovery" % pekkoV,
      "org.apache.pekko" %% "pekko-stream-typed" % pekkoV,
      "org.apache.pekko" %% "pekko-grpc-runtime" % pekkoGrpcV,
      "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoV % Test,
      "io.grpc" % "grpc-services" % grpcV % Test,
    ),
    Compile / sourceGenerators += Def.task {
      // Copy from the managed source directory to the output directory
      val inputDir = (rdfProtos / target).value / ("scala-" + scalaVersion.value) /
        "src_managed" / "main" / "compiled_protobuf" /
        "eu" / "neverblink" / "jelly" / "core" / "proto" / "v1"

      val outputDir = sourceManaged.value / "main" /
        "eu" / "neverblink" / "jelly" / "core" / "proto" / "v1"

      val javaFiles = (inputDir * "*.java").get
      javaFiles
        .filter { file => grpcJavaSourceFiles.contains(file.getName) }
        .map { file =>
          val outputFile = outputDir / file.relativeTo(inputDir).get.getPath
          IO.copyFile(file, outputFile)
          outputFile
        }
    }.dependsOn(rdfProtos / ProtobufConfig / protobufGenerate),
    Compile / sourceManaged := sourceManaged.value / "main",
    commonSettings,
  )
  .dependsOn(stream)
  .dependsOn(core % "compile->compile;test->test")
