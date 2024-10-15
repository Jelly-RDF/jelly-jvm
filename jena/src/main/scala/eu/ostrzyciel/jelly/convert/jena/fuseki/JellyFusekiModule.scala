package eu.ostrzyciel.jelly.convert.jena.fuseki

import eu.ostrzyciel.jelly.core.Constants
import org.apache.jena.atlas.web.{AcceptList, MediaRange}
import org.apache.jena.fuseki.{DEF, Fuseki}
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.main.sys.FusekiAutoModule
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.WebContent

import java.util

object JellyFusekiModule:
  val mediaRangeJelly: MediaRange = new MediaRange(Constants.jellyContentType)

/**
 * A Fuseki module that adds Jelly content type to the list of accepted content types.
 *
 * This allows users to use the Accept header set to application/x-jelly-rdf to request Jelly RDF responses.
 * It works for SPARQL CONSTRUCT queries and for the Graph Store Protocol.
 *
 * More info on Fuseki modules: https://jena.apache.org/documentation/fuseki2/fuseki-modules.html
 */
final class JellyFusekiModule extends FusekiAutoModule:
  import JellyFusekiModule.*

  override def name(): String = "Jelly"

  override def start(): Unit =
    try {
      maybeAddJellyToList(DEF.constructOffer).foreach(offer => DEF.constructOffer = offer)
      maybeAddJellyToList(DEF.rdfOffer).foreach(offer => DEF.rdfOffer = offer)
      maybeAddJellyToList(DEF.quadsOffer).foreach(offer => {
        DEF.quadsOffer = offer
        Fuseki.serverLog.info(s"Added ${Constants.jellyContentType} to the list of accepted content types")
      })
    } catch {
      case e: IllegalAccessError => Fuseki.serverLog.warn(
        s"Cannot register the ${Constants.jellyContentType} content type, because you are running an Apache Jena " +
          s"Fuseki version that doesn't support content type registration. " +
          s"Update to Fuseki 5.2.0 or newer for this to work."
      )
    }

  /**
   * Adds the Jelly content type to the list of accepted content types if it is not already present.
   * @param list current list of accepted content types
   * @return none or a new list with Jelly content type
   */
  private def maybeAddJellyToList(list: AcceptList): Option[AcceptList] =
    if list.entries().contains(mediaRangeJelly) then
      None
    else
      val newList = new util.Vector[MediaRange](list.entries())
      newList.add(mediaRangeJelly)
      Some(new AcceptList(newList))
