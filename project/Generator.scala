import sbt._

/**
 * sbt task that transforms the Scala code generated with protoc to other Scala code, using scalameta.
 */
object Generator {
  def gen(inputDir: File, outputDir: File): Seq[File] = {
    println(s"Generating Scala files from $inputDir to $outputDir")
    val finder: PathFinder = inputDir ** "*.scala"

    for(inputFile <- finder.get) yield {
      println(s"  Processing ${inputFile.getName}")
      val inputStr = IO.read(inputFile)
      val outputFile = outputDir / inputFile.name
      // Apply transformations
      val outputStr = ProtoTransformer.transform(inputStr)
      IO.write(outputFile, outputStr)
      outputFile
    }
  }
}
