package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.convert.jena.riot.JellyLanguage.JELLY
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import org.apache.jena.riot.{RDFFormat, RDFFormatVariant}

/**
 * Subclass of [[RDFFormatVariant]] to pass Jelly's options to the encoder.
 * @param opt Jelly options
 * @param frameSize size of each RdfStreamFrame, in rows
 */
case class JellyFormatVariant(
  opt: RdfStreamOptions = JellyOptions.smallAllFeatures,
  frameSize: Int = 256
) extends RDFFormatVariant(opt.toString)

/**
 * Pre-defined serialization format variants for Jelly.
 */
object JellyFormat:
  val JELLY_SMALL_STRICT = new RDFFormat(JELLY, JellyFormatVariant(JellyOptions.smallStrict))
  val JELLY_SMALL_GENERALIZED = new RDFFormat(JELLY, JellyFormatVariant(JellyOptions.smallGeneralized))
  val JELLY_SMALL_RDF_STAR = new RDFFormat(JELLY, JellyFormatVariant(JellyOptions.smallRdfStar))
  val JELLY_SMALL_ALL_FEATURES = new RDFFormat(JELLY, JellyFormatVariant(JellyOptions.smallAllFeatures))
  val JELLY_BIG_STRICT = new RDFFormat(JELLY, JellyFormatVariant(JellyOptions.bigStrict))
  val JELLY_BIG_GENERALIZED = new RDFFormat(JELLY, JellyFormatVariant(JellyOptions.bigGeneralized))
  val JELLY_BIG_RDF_STAR = new RDFFormat(JELLY, JellyFormatVariant(JellyOptions.bigRdfStar))
  val JELLY_BIG_ALL_FEATURES = new RDFFormat(JELLY, JellyFormatVariant(JellyOptions.bigAllFeatures))
