package eu.neverblink.jelly.core.sparql.helpers

import eu.neverblink.jelly.core.helpers.MockConverterFactory
import eu.neverblink.jelly.core.helpers.MockProtoDecoderConverter
import eu.neverblink.jelly.core.helpers.MockProtoEncoderConverter
import eu.neverblink.jelly.core.helpers.Mrl.{Datatype, Node}
import eu.neverblink.jelly.core.sparql.JellySparqlConverterFactory

import scala.annotation.experimental

@experimental
object MockSparqlConverterFactory
    extends JellySparqlConverterFactory[
      Node,
      Datatype,
      MockProtoEncoderConverter,
      MockProtoDecoderConverter,
    ](MockConverterFactory)
