package eu.neverblink.jelly.core.sparql.helpers

import eu.neverblink.jelly.core.helpers.Mrl.{Datatype, Node}
import eu.neverblink.jelly.core.helpers.{
  MockProtoDecoderConverter,
  MockProtoEncoderConverter,
  StrictMockConverterFactory,
}
import eu.neverblink.jelly.core.sparql.JellySparqlConverterFactory

/** Same as [[MockSparqlConverterFactory]], but its decoder converter refuses IRIs that are not
  * absolute.
  */
object StrictMockSparqlConverterFactory
    extends JellySparqlConverterFactory[
      Node,
      Datatype,
      MockProtoEncoderConverter,
      MockProtoDecoderConverter,
    ](StrictMockConverterFactory)
