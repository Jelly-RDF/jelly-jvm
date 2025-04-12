package eu.ostrzyciel.jelly.convert.rdf4j.patch

import eu.ostrzyciel.jelly.convert.rdf4j.{Rdf4jConverterFactory, Rdf4jDatatype}
import eu.ostrzyciel.jelly.core.patch.PatchConverterFactory
import org.eclipse.rdf4j.model.Value

import scala.annotation.experimental

@experimental
object Rdf4jPatchConverterFactory extends PatchConverterFactory[Value, Rdf4jDatatype](
  Rdf4jConverterFactory
)
