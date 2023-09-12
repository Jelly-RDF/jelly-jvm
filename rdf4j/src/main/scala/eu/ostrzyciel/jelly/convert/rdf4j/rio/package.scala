package eu.ostrzyciel.jelly.convert.rdf4j

import eu.ostrzyciel.jelly.core.Constants.*
import org.eclipse.rdf4j.rio.RDFFormat

package object rio:
  /**
   * The Jelly RDF format for RDF4J Rio.
   */
  val JELLY = new RDFFormat(
    jellyName,
    jellyContentType,
    null,
    jellyFileExtension,
    false,
    false,
    true
  )
