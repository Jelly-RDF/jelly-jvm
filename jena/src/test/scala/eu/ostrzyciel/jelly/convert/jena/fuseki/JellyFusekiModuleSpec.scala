package eu.ostrzyciel.jelly.convert.jena.fuseki

import org.apache.jena.fuseki.DEF
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

class JellyFusekiModuleSpec extends AnyWordSpec, Matchers:
  "JellyFusekiModule" should {
    "have a name" in {
      JellyFusekiModule().name() should be ("Jelly")
    }

    "use the correct content type for Jelly" in {
      JellyFusekiModule.mediaRangeJelly.getContentTypeStr should be ("application/x-jelly-rdf")
    }

    "register the Jelly content type in the lists of accepted content types" in {
      val oldLists = List(DEF.constructOffer, DEF.rdfOffer, DEF.quadsOffer)
      for list <- oldLists do
        list.entries().asScala should not contain JellyFusekiModule.mediaRangeJelly
      DEF.constructOffer.entries().asScala should not contain JellyFusekiModule.mediaRangeJelly
      DEF.rdfOffer.entries().asScala should not contain JellyFusekiModule.mediaRangeJelly
      DEF.quadsOffer.entries().asScala should not contain JellyFusekiModule.mediaRangeJelly

      val module = JellyFusekiModule()
      module.prepare(null, null, null)

      val lists = List(DEF.constructOffer, DEF.rdfOffer, DEF.quadsOffer)
      for (list, oldList) <- lists.zip(oldLists) do
        list.entries().asScala should contain (JellyFusekiModule.mediaRangeJelly)
        list.entries().size() should be (oldList.entries().size() + 1)
    }

    "not register the Jelly content type if it's already registered" in {
      val module = JellyFusekiModule()
      module.prepare(null, null, null)
      DEF.rdfOffer.entries().asScala should contain (JellyFusekiModule.mediaRangeJelly)
      val size1 = DEF.rdfOffer.entries().size()

      module.prepare(null, null, null)
      DEF.rdfOffer.entries().asScala should contain (JellyFusekiModule.mediaRangeJelly)
      val size2 = DEF.rdfOffer.entries().size()
      size2 should be (size1)
    }
  }
