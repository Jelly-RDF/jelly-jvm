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
    val jellyCliFile = File(getClass.getResource("/jelly-cli.jar").getFile)

    val javaBinary =
      if System.getProperty("os.name").toLowerCase.contains("windows") then "java.exe"
      else "java"

    val command = Seq(
      javaBinary, "-jar", jellyCliFile.getAbsolutePath,
      "rdf", "validate",
      s"--compare-to-rdf-file=${expectedJelly.getAbsolutePath}",
      "--compare-ordered=true",
      frameIndexToCompare.map(i => s"--compare-frame-index=$i").getOrElse(""),
      optionsFile.map(file => s"--options-file=${file.getAbsolutePath}").getOrElse(""),
      actualJelly.getAbsolutePath
    ).filter(_ != "")

    val process = Process(command)
    val exitCode = process ! ProcessLogger(println, println)
    exitCode
