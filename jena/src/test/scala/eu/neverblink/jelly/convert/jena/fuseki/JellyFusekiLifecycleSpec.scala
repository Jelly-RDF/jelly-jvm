package eu.neverblink.jelly.convert.jena.fuseki

import eu.neverblink.jelly.convert.jena.riot.JellySubsystemLifecycle
import eu.neverblink.jelly.convert.jena.traits.JenaTest
import org.apache.jena.fuseki.DEF
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

class JellyFusekiLifecycleSpec extends AnyWordSpec, Matchers, JenaTest:
  "JellyFusekiLifecycle" should {
    "initialize after JenaSubsystemLifecycle" in {
      val jenaModule = JellySubsystemLifecycle()
      val module = JellyFusekiLifecycle()
      module.level() should be > jenaModule.level()
    }

    "use the correct content type for Jelly" in {
      JellyFusekiLifecycle.JELLY_MEDIA_RANGE.getContentTypeStr should be ("application/x-jelly-rdf")
    }

    "register the Jelly content type in the lists of accepted content types" in {
      // This assumption is broken for `jenaPlugin` tests, because there the JenaSystem is initialized
      // and reads the service definition.
      // This test thus only can be executed in the `jena` module.
      assume(DEF.constructOffer == DEF.constructOfferDefault())

      val oldLists = List(DEF.constructOffer, DEF.rdfOffer, DEF.quadsOffer)
      for list <- oldLists do
        list.entries().asScala should not contain JellyFusekiLifecycle.JELLY_MEDIA_RANGE

      val module = JellyFusekiLifecycle()
      module.start()

      val lists = List(DEF.constructOffer, DEF.rdfOffer, DEF.quadsOffer)
      for (list, oldList) <- lists.zip(oldLists) do
        list.entries().asScala should contain (JellyFusekiLifecycle.JELLY_MEDIA_RANGE)
        list.entries().size() should be (oldList.entries().size() + 1)
    }

    "not register the Jelly content type if it's already registered" in {
      val module = JellyFusekiLifecycle()
      module.start()
      DEF.rdfOffer.entries().asScala should contain (JellyFusekiLifecycle.JELLY_MEDIA_RANGE)
      val size1 = DEF.rdfOffer.entries().size()

      module.start()
      DEF.rdfOffer.entries().asScala should contain (JellyFusekiLifecycle.JELLY_MEDIA_RANGE)
      val size2 = DEF.rdfOffer.entries().size()
      size2 should be (size1)
    }
  }
