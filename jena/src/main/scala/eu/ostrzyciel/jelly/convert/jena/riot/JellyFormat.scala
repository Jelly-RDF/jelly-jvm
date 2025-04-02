package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.convert.jena.riot.JellyLanguage.JELLY
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import org.apache.jena.riot.{RDFFormat, RDFFormatVariant}

/**
 * Subclass of [[RDFFormatVariant]] to pass Jelly's options to the encoder.
 * @param opt Jelly options
 * @param frameSize size of each RdfStreamFrame, in rows
 * @param enableNamespaceDeclarations whether to include namespace declarations in the output
 * @param delimited whether to write the output as delimited frames. Note: files saved to disk are
 *                  recommended to be delimited, for better interoperability with other implementations.
 *                  In a non-delimited file you can have ONLY ONE FRAME. If the input data is large,
 *                  this will lead to an out-of-memory error. So, this makes sense only for small data.
 *                  **Disable this only if you know what you are doing.**
 */
case class JellyFormatVariant(
  opt: RdfStreamOptions = JellyOptions.smallAllFeatures,
  frameSize: Int = 256,
  enableNamespaceDeclarations: Boolean = false,
  delimited: Boolean = true,
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
