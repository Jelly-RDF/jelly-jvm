package eu.ostrzyciel.jelly.convert.jena.fuseki

import eu.ostrzyciel.jelly.core.Constants
import org.apache.jena.atlas.web.{AcceptList, MediaRange}
import org.apache.jena.fuseki.{DEF, Fuseki}
import org.apache.jena.sys.JenaSubsystemLifecycle

import java.util

object JellyFusekiLifecycle:
  val mediaRangeJelly: MediaRange = new MediaRange(Constants.jellyContentType)

/**
 * A Jena module that adds Jelly content type to the list of accepted content types in Fuseki.
 * This isn't a Fuseki module, because Fuseki modules are not supported in all distributions of Fuseki, see:
 * https://github.com/apache/jena/issues/2774
 *
 * This allows users to use the Accept header set to application/x-jelly-rdf to request Jelly RDF responses.
 * It works for SPARQL CONSTRUCT queries and for the Graph Store Protocol.
 */
final class JellyFusekiLifecycle extends JenaSubsystemLifecycle:
  import JellyFusekiLifecycle.*

  override def start(): Unit =
    try {
      maybeAddJellyToList(DEF.constructOffer).foreach(offer => DEF.constructOffer = offer)
      maybeAddJellyToList(DEF.rdfOffer).foreach(offer => DEF.rdfOffer = offer)
      maybeAddJellyToList(DEF.quadsOffer).foreach(offer => {
        DEF.quadsOffer = offer
        Fuseki.serverLog.info(s"Jelly: Added ${Constants.jellyContentType} to the list of accepted content types")
      })
    } catch {
      case e: NoClassDefFoundError => // ignore, we are not running Fuseki
      case e: IllegalAccessError => Fuseki.serverLog.warn(
        s"Jelly: Cannot register the ${Constants.jellyContentType} content type, because you are running an " +
          s"Apache Jena Fuseki version that doesn't support content type registration. " +
          s"Update to Fuseki 5.2.0 or newer for this to work."
      )
    }

  override def stop(): Unit = ()

  // Initialize after JellySubsystemLifecycle
  override def level(): Int = 502

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
