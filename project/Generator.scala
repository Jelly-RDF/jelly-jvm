import sbt._

/**
 * sbt task that transforms the Scala code generated with protoc to other Scala code, using scalameta.
 */
object Generator {
  def gen(inputDir: File, outputDir: File, module: String): Seq[File] = {
    println(s"Generating Scala files from $inputDir to $outputDir")
    val finder: PathFinder = inputDir ** "*.scala"

    val exclusions = Seq("RdfTriple.scala", "RdfQuad.scala", "RdfGraphStart.scala", "RdfStreamRow.scala",
      "RdfNamespaceDeclaration.scala", "RdfPatchRow.scala", "RdfPatchHeader.scala", "RdfPatchNamespace.scala")
    val fileNameFilters = Map(
      "core-patch" -> Seq("RdfPatch", "Patch"),
    )

    val files = for (inputFile <- finder.get) yield {
      val excludedByModule: Boolean = if (module == "core") {
        fileNameFilters.exists(el => module != el._1 && el._2.exists(inputFile.getName.startsWith))
      } else {
        !fileNameFilters(module).exists(inputFile.getName.startsWith)
      }

      if (excludedByModule) {
        // println(s"  ($module) Skipping by module ${inputFile.getName}")
        None
      } else if (exclusions.contains(inputFile.getName)) {
        println(s"  ($module) Skipping by exclusion ${inputFile.getName}")
        None
      } else {
        println(s"  ($module) Processing ${inputFile.getName}")
        val inputStr = IO.read(inputFile)
        val outputFile = outputDir / inputFile.name
        // Apply transformations
        val outputStr = ProtoTransformer.transform(inputStr)
        IO.write(outputFile, outputStr)
        Some(outputFile)
      }
    }
    files.flatten
  }
}
