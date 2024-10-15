package eu.ostrzyciel.jelly.convert.jena.fuseki

import eu.ostrzyciel.jelly.core.Constants
import org.apache.jena.atlas.web.{AcceptList, MediaRange}
import org.apache.jena.fuseki.DEF
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.main.sys.FusekiAutoModule
import org.apache.jena.rdf.model.Model

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

  override def prepare(serverBuilder: FusekiServer.Builder, datasetNames: util.Set[String], configModel: Model): Unit =
    maybeAddJellyToList(DEF.constructOffer).foreach(offer => DEF.constructOffer = offer)
    maybeAddJellyToList(DEF.rdfOffer).foreach(offer => DEF.rdfOffer = offer)
    maybeAddJellyToList(DEF.quadsOffer).foreach(offer => DEF.quadsOffer = offer)

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
