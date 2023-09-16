package eu.ostrzyciel.jelly.stream

import com.typesafe.config.{Config, ConfigFactory}
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamOptions, RdfStreamType}

/**
 * Convenience methods for building Jelly's options ([[RdfStreamOptions]]) from [[com.typesafe.Config]].
 *
 * See also [[eu.ostrzyciel.jelly.core.JellyOptions]]
 */
object JellyOptionsFromTypesafe:
  private val defaultConfig = ConfigFactory.parseString("""
    |stream-type = UNSPECIFIED
    |generalized-statements = false
    |use-repeat = true
    |rdf-star = false
    |name-table-size = 128
    |prefix-table-size = 16
    |dt-table-size = 16
    |""".stripMargin)

  /**
   * Builds RdfStreamOptions from a typesafe config instance.
   *
   * @param config typesafe config with keys:
   *               - "stream-type", either UNSPECIFIED, TRIPLES, QUADS, or GRAPHS. Default: UNSPECIFIED.
   *               - "generalized-statements", boolean. Default: false.
   *               - "use-repeat", boolean. Default: true.
   *               - "rdf-star", boolean. Default: false.
   *               - "name-table-size", integer. Default: 128.
   *               - "prefix-table-size", integer. Default: 16.
   *               - "dt-table-size", integer. Default: 16.
   * @return
   */
  def fromTypesafeConfig(config: Config): RdfStreamOptions =
    val merged = config.withFallback(defaultConfig)
    RdfStreamOptions(
      streamType = (
        merged.getString("stream-type") match
          case "UNSPECIFIED" => RdfStreamType.RDF_STREAM_TYPE_UNSPECIFIED
          case "TRIPLES" => RdfStreamType.RDF_STREAM_TYPE_TRIPLES
          case "QUADS" => RdfStreamType.RDF_STREAM_TYPE_QUADS
          case "GRAPHS" => RdfStreamType.RDF_STREAM_TYPE_GRAPHS
          case _ => throw IllegalArgumentException()
        ),
      generalizedStatements = merged.getBoolean("generalized-statements"),
      useRepeat = merged.getBoolean("use-repeat"),
      rdfStar = merged.getBoolean("rdf-star"),
      maxNameTableSize = merged.getInt("name-table-size"),
      maxPrefixTableSize = merged.getInt("prefix-table-size"),
      maxDatatypeTableSize = merged.getInt("dt-table-size"),
    )
