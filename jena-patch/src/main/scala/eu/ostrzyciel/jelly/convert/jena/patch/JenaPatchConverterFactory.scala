package eu.ostrzyciel.jelly.convert.jena.patch

import eu.ostrzyciel.jelly.convert.jena.JenaConverterFactory
import eu.ostrzyciel.jelly.core.patch.PatchConverterFactory
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.graph.Node

import scala.annotation.experimental

/**
 * Factory for Jena-based Jelly-Patch encoders and decoders.
 */
@experimental
object JenaPatchConverterFactory extends PatchConverterFactory[Node, RDFDatatype](
  JenaConverterFactory
)
