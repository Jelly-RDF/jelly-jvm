package eu.neverblink.jelly.integration_tests.util
import java.io.File
import scala.sys.process.{Process, ProcessLogger}

object JellyCli:

  def rdfValidate(
    actualJelly: File,
    expectedJelly: File,
    optionsFile: Option[File],
    frameIndexToCompare: Option[Int]
  ): Int =
    val jellyCliFile = try {
      File(getClass.getResource("/jelly-cli").getFile)
    } catch {
      case t: Throwable =>
        throw new RuntimeException("jelly-cli not found in resources. Please ensure it is included " +
          "in the classpath. Running `sbt test` should resolve this.", t)
    }


    val command = Seq(
      jellyCliFile.getAbsolutePath,
      "rdf", "validate",
      s"--compare-to-rdf-file=${expectedJelly.getAbsolutePath}",
      "--compare-ordered=true",
      frameIndexToCompare.map(i => s"--compare-frame-index=$i").getOrElse(""),
      optionsFile.map(file => s"--options-file=${file.getAbsolutePath}").getOrElse(""),
      actualJelly.getAbsolutePath
    ).filter(_ != "")
    
    println(s"Running command: ${command.mkString(" ")}")

    val process = Process(command)
    val exitCode = process ! ProcessLogger(println, println)
    exitCode
