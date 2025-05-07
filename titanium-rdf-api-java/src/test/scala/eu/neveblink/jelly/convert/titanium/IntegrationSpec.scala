package eu.neveblink.jelly.convert.titanium

import com.apicatalog.rdf.api.RdfQuadConsumer
import eu.neverblink.jelly.convert.titanium.TitaniumJellyReader
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * Integration tests for some Titanium-specific APIs.
 */
class IntegrationSpec extends AnyWordSpec, Matchers:
  val testFrame = RdfStreamFrame.newInstance()
    .addRows(
      RdfStreamRow.newInstance()
        .setOptions(JellyOptions.SMALL_STRICT.clone().setPhysicalType(PhysicalStreamType.QUADS))
    )
    .addRows(
      RdfStreamRow.newInstance()
        .setName(RdfNameEntry.newInstance().setValue("http://example.org/iri"))
    )
    .addRows(
      RdfStreamRow.newInstance()
        .setQuad(
          RdfQuad.newInstance()
            .setSubject(RdfIri.newInstance().setPrefixId(0).setNameId(1), RdfQuad.S_IRI)
            .setPredicate(RdfIri.newInstance().setPrefixId(0).setNameId(1), RdfQuad.P_IRI)
            .setObject(RdfIri.newInstance().setPrefixId(0).setNameId(1), RdfQuad.O_IRI)
            .setGraph(RdfIri.newInstance().setPrefixId(0).setNameId(1), RdfQuad.G_IRI)
        )
    )

  val testFrame2 = RdfStreamFrame.newInstance()
    .addRows(
      RdfStreamRow.newInstance()
        .setQuad(
          RdfQuad.newInstance()
            .setSubject(RdfIri.newInstance().setPrefixId(0).setNameId(1), RdfQuad.S_IRI)
            .setPredicate(RdfIri.newInstance().setPrefixId(0).setNameId(1), RdfQuad.P_IRI)
            .setObject(RdfIri.newInstance().setPrefixId(0).setNameId(1), RdfQuad.O_IRI)
            .setGraph(RdfIri.newInstance().setPrefixId(0).setNameId(1), RdfQuad.G_IRI)
        )
    )

  class CollectorConsumer extends RdfQuadConsumer:
    var quads = Seq.empty[(String, String, String, String, String, String, String)]

    override def quad(
      subject: String, predicate: String, `object`: String,
      datatype: String, language: String, direction: String, graph: String
    ): RdfQuadConsumer =
      quads = quads :+ (subject, predicate, `object`, datatype, language, direction, graph)
      this

  "TitaniumJellyReader" should {
    "parse a single non-delimited frame" in {
      val is = ByteArrayInputStream(testFrame.toByteArray)
      val reader = TitaniumJellyReader.factory()
      val cons = CollectorConsumer()
      reader.parseFrame(cons, is)
      cons.quads should have size 1
    }

    "parse a single non-delimited frame with parseAll" in {
      val is = ByteArrayInputStream(testFrame.toByteArray)
      val reader = TitaniumJellyReader.factory()
      val cons = CollectorConsumer()
      reader.parseAll(cons, is)
      cons.quads should have size 1
    }

    "parse a few non-delimited frames with parseFrame" in {
      val reader = TitaniumJellyReader.factory()
      val cons = CollectorConsumer()
      reader.parseFrame(cons, ByteArrayInputStream(testFrame.toByteArray))
      reader.parseFrame(cons, ByteArrayInputStream(testFrame2.toByteArray))
      reader.parseFrame(cons, ByteArrayInputStream(testFrame2.toByteArray))
      cons.quads should have size 3
    }

    "parse a single delimited frame" in {
      val os = ByteArrayOutputStream()
      testFrame.writeDelimitedTo(os)
      val is = ByteArrayInputStream(os.toByteArray)
      val reader = TitaniumJellyReader.factory()
      val cons = CollectorConsumer()
      reader.parseFrame(cons, is)
      cons.quads should have size 1
    }
  }
