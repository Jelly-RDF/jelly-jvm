package eu.neverblink.jelly.pekko.stream

import com.typesafe.config.{Config, ConfigFactory}
import eu.neverblink.jelly.core.proto.v1.{
  LogicalStreamType,
  PhysicalStreamType,
  RdfStreamOptions,
  RdfStreamType,
}
import eu.neverblink.jelly.core.utils.RdfVersionUtils

/** Convenience methods for building Jelly's options ([[RdfStreamOptions]]) from
  * [[com.typesafe.Config]].
  *
  * See also [[eu.neverblink.jelly.core.JellyOptions]]
  */
object JellyOptionsFromTypesafe:
  private val defaultConfig = ConfigFactory.parseString("""
    |physical-type = UNSPECIFIED
    |stream-type = FLAT
    |rdf-version = ""
    |logical-type = UNSPECIFIED
    |generalized-statements = false
    |rdf-star = false
    |name-table-size = 128
    |prefix-table-size = 16
    |datatype-table-size = 16
    |protocol-version = 0
    |""".stripMargin)

  private val physicalStreamTypeMap = PhysicalStreamType.values
    .map(v => v.name.replace("PHYSICAL_STREAM_TYPE_", "") -> v)
    .toMap

  private val logicalStreamTypeMap = LogicalStreamType.values
    .map(v => v.name.replace("LOGICAL_STREAM_TYPE_", "") -> v)
    .toMap

  private val streamTypeMap = RdfStreamType.values
    .map(v => v.name.replace("RDF_STREAM_TYPE_", "") -> v)
    .toMap

  /** Builds RdfStreamOptions from a typesafe config instance.
    *
    * @param config
    *   typesafe config with keys:
    *   - "physical-type", either UNSPECIFIED, TRIPLES, QUADS, or GRAPHS (Jelly-RDF 1.0 and 1.1
    *     only). Default: UNSPECIFIED.
    *   - "stream-type" (Jelly-RDF 1.2 only), either FLAT or MESSAGES. Default: FLAT.
    *   - "rdf-version" (Jelly-RDF 1.2 only), the version of RDF whose terms may occur in the
    *     stream: "1.1", "1.2-basic", "1.2", or "" (unspecified, any RDF 1.2 term). Default: "".
    *   - "logical-type" (Jelly-RDF 1.0 and 1.1 only), one of the defined logical types like
    *     "FLAT_TRIPLES" in rdf.proto, LogicalStreamType enum. Default: UNSPECIFIED.
    *   - "generalized-statements" (Jelly-RDF 1.0 and 1.1 only), boolean. Default: false.
    *   - "rdf-star" (Jelly-RDF 1.0 and 1.1 only), boolean. Default: false.
    *   - "name-table-size", integer. Default: 128.
    *   - "prefix-table-size", integer. Default: 16.
    *   - "datatype-table-size", integer. Default: 16.
    *   - "protocol-version", integer. 0 (the default) means the newest version, Jelly-RDF 1.2. Set
    *     it to 1 or 2 to write Jelly-RDF 1.0 or 1.1 (deprecated).
    *
    * The encoder flows ([[EncoderFlow]]) set the physical type, the stream type, and the logical
    * type themselves.
    * @return
    */
  def fromTypesafeConfig(config: Config): RdfStreamOptions =
    val merged = config.withFallback(defaultConfig)
    RdfStreamOptions.newInstance()
      .setPhysicalType(
        physicalStreamTypeMap.get(merged.getString("physical-type")) match
          case Some(v) => v
          case None =>
            throw new IllegalArgumentException(
              s"Unknown physical type: ${merged.getString("physical-type")}",
            ),
      )
      .setStreamType(
        streamTypeMap.get(merged.getString("stream-type")) match
          case Some(v) => v
          case None =>
            throw new IllegalArgumentException(
              s"Unknown stream type: ${merged.getString("stream-type")}",
            ),
      )
      .setRdfVersion(RdfVersionUtils.rdfVersionFromLabel(merged.getString("rdf-version")))
      .setLogicalType(
        logicalStreamTypeMap.get(merged.getString("logical-type")) match
          case Some(v) => v
          case None =>
            throw new IllegalArgumentException(
              s"Unknown logical type: ${merged.getString("logical-type")}",
            ),
      )
      .setGeneralizedStatements(merged.getBoolean("generalized-statements"))
      .setRdfStar(merged.getBoolean("rdf-star"))
      .setMaxNameTableSize(merged.getInt("name-table-size"))
      .setMaxPrefixTableSize(merged.getInt("prefix-table-size"))
      .setMaxDatatypeTableSize(merged.getInt("datatype-table-size"))
      .setVersion(merged.getInt("protocol-version"))
