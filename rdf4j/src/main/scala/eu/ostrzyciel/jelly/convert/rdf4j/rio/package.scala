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
    true, // supports namespaces if ENABLE_NAMESPACE_DECLARATIONS is true, otherwise ignored
    true,
    true
  )
