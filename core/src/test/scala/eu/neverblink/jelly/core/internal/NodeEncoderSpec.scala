package eu.neverblink.jelly.core.internal

import eu.neverblink.jelly.core.helpers.Mrl
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.{RdfProtoSerializationError, RdfBufferAppender}
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer
import scala.util.Random

class NodeEncoderSpec extends AnyWordSpec, Inspectors, Matchers:
  def smallOptions(prefixTableSize: Int): RdfStreamOptions = rdfStreamOptions(
    maxNameTableSize = 8,
    maxPrefixTableSize = prefixTableSize,
    maxDatatypeTableSize = 8,
  )

  type RdfTerm = RdfIri | String | RdfLiteral | (Mrl.Node, Mrl.Node, Mrl.Node) | RdfDefaultGraph

  private def getEncoder(prefixTableSize: Int = 8): 
  (NodeEncoderImpl[Mrl.Node], ListBuffer[RdfStreamRow], ListBuffer[RdfTerm]) =
    val entryBuffer = new ListBuffer[RdfStreamRow]()
    val termBuffer = new ListBuffer[RdfTerm]()
    val appender: RdfBufferAppender[Mrl.Node] = new RdfBufferAppender {
      def appendNameEntry(entry: RdfNameEntry): Unit = entryBuffer += rdfStreamRow(entry)
      def appendPrefixEntry(entry: RdfPrefixEntry): Unit = entryBuffer += rdfStreamRow(entry)
      def appendDatatypeEntry(entry: RdfDatatypeEntry): Unit = entryBuffer += rdfStreamRow(entry)

      override def appendIri(iri: RdfIri): Unit = termBuffer += iri
      override def appendBlankNode(label: String): Unit = termBuffer += label
      override def appendLiteral(literal: RdfLiteral): Unit = termBuffer += literal
      override def appendQuotedTriple(subject: Mrl.Node, predicate: Mrl.Node, `object`: Mrl.Node): Unit =
        termBuffer += ((subject, predicate, `object`))
      override def appendDefaultGraph(): Unit = termBuffer += RdfDefaultGraph.EMPTY
    }
    (NodeEncoderImpl[Mrl.Node](
      prefixTableSize, 8, 8,
      16, 16, 16, 
      appender
    ), entryBuffer, termBuffer)

  "A NodeEncoder" when {
    "encoding datatype literals" should {
      "encode a datatype literal" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder()
        encoder.makeDtLiteral(Mrl.DtLiteral("v1", Mrl.Datatype("dt1")), "v1", "dt1")
        
        val node = termBuffer.head.asInstanceOf[RdfLiteral]
        node.getLex should be ("v1")
        node.getDatatype should be (1)

        entryBuffer.size should be (1)
        entryBuffer.head.hasDatatype should be (true)
        val dtEntry = entryBuffer.head.getDatatype
        dtEntry.getValue should be ("dt1")
        dtEntry.getId should be (0)
      }

      "encode multiple datatype literals and reuse existing datatypes" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder()
        for i <- 1 to 4 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
            s"v$i", s"dt$i"
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be (s"v$i")
          node.getDatatype should be (i)

        // "dt3" datatype should be reused
        encoder.makeDtLiteral(
          Mrl.DtLiteral(s"v1000", Mrl.Datatype(s"dt3")),
          "v1000", "dt3",
        )
        val node = termBuffer.head.asInstanceOf[RdfLiteral]
        termBuffer.clear()
        node.getLex should be ("v1000")
        node.getDatatype should be (3)

        // "v2"^^<dt2> should be reused
        encoder.makeDtLiteral(
          Mrl.DtLiteral("v2", Mrl.Datatype("dt2")),
          "v2", "dt2",
        )
        val node2 = termBuffer.head.asInstanceOf[RdfLiteral]
        termBuffer.clear()
        node2.getLex should be ("v2")
        node2.getDatatype should be (2)

        entryBuffer.size should be (4)
        entryBuffer.map(_.getDatatype) should contain only (
          rdfDatatypeEntry(0, "dt1"),
          rdfDatatypeEntry(0, "dt2"),
          rdfDatatypeEntry(0, "dt3"),
          rdfDatatypeEntry(0, "dt4"),
        )
      }

      "not evict datatype IRIs used recently" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder()
        for i <- 1 to 8 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
            s"v$i", s"dt$i",
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be(s"v$i")
          node.getDatatype should be(i)

        // use literal 1 again
        encoder.makeDtLiteral(
          Mrl.DtLiteral("v1", Mrl.Datatype("dt1")),
          "v1", "dt1",
        )
        val node = termBuffer.head.asInstanceOf[RdfLiteral]
        termBuffer.clear()
        node.getLex should be("v1")
        node.getDatatype should be(1)

        // now add a new DT and see which DT is evicted
        encoder.makeDtLiteral(
          Mrl.DtLiteral("v9", Mrl.Datatype("dt9")),
          "v9", "dt9",
        )
        val node2 = termBuffer.head.asInstanceOf[RdfLiteral]
        termBuffer.clear()
        node2.getLex should be("v9")
        node2.getDatatype should be(2)
      }

      "encode datatype literals while evicting old datatypes" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder()
        for i <- 1 to 12 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
            s"v$i", s"dt$i",
          )
          // first 4 datatypes should be evicted
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be (s"v$i")
          node.getDatatype should be ((i - 1) % 8 + 1)

        for i <- 9 to 12 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
            s"v$i", s"dt$i",
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be (s"v$i")
          node.getDatatype should be (i - 8)

        for i <- 5 to 8 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
            s"v$i", s"dt$i",
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be (s"v$i")
          node.getDatatype should be (i)

        // 5–8 were used last, so they should be evicted last
        for i <- 13 to 16 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
            s"v$i", s"dt$i",
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be (s"v$i")
          node.getDatatype should be (i - 12) // 1–4

        entryBuffer.size should be (16)
        val expectedIds = Array.from(
          Iterable.fill(8)(0) ++ Seq(1) ++ Iterable.fill(3)(0) ++ Seq(1) ++ Iterable.fill(3)(0)
        )
        for (r, i) <- entryBuffer.zipWithIndex do
          val dt = r.getDatatype
          dt.getId should be (expectedIds(i))
          dt.getValue should be (s"dt${i + 1}")
      }

      "reuse already encoded literals, evicting old ones" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder()
        for i <- 1 to 4; j <- 1 to 4 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$j")),
            s"v$i", s"dt$j",
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be (s"v$i")
          node.getDatatype should be (j)

        for _ <- 1 to 10 do
          for i <- Random.shuffle(1 to 4); j <- Random.shuffle(1 to 4) do
            encoder.makeDtLiteral(
              Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$j")),
              s"v$i", s"dt$j",
            )
            val node = termBuffer.head.asInstanceOf[RdfLiteral]
            termBuffer.clear()
            node.getLex should be (s"v$i")
            node.getDatatype should be (j)

        // Add more literals to evict the old ones
        for j <- 101 to 104 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v100", Mrl.Datatype(s"dt${j - 100}")),
            s"v100", s"dt${j - 100}",
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be ("v100")
          node.getDatatype should be (j - 100)

        // These entries should have been evicted
        for j <- 1 to 4 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v1", Mrl.Datatype(s"dt$j")),
            s"v1", s"dt$j",
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be ("v1")
          node.getDatatype should be (j)
      }

      "invalidate cached datatype literals when their datatypes are evicted" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder()
        for i <- 1 to 4 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
            s"v$i", s"dt$i",
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be (s"v$i")
          node.getDatatype should be (i)

        for i <- 5 to 12 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
            s"v$i", s"dt$i",
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be (s"v$i")
          node.getDatatype should be ((i - 1) % 8 + 1)

        for i <- 1 to 4 do
          encoder.makeDtLiteral(
            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
            s"v$i", s"dt$i",
          )
          val node = termBuffer.head.asInstanceOf[RdfLiteral]
          termBuffer.clear()
          node.getLex should be (s"v$i")
          node.getDatatype should be (i + 4)
      }

      "throw exception if datatype table size = 0" in {
        val encoder = NodeEncoderImpl[Mrl.Node](
          16, 16, 0, 16, 16, 16, null
        )
        val e = intercept[RdfProtoSerializationError] {
          encoder.makeDtLiteral(
            Mrl.DtLiteral("v1", Mrl.Datatype("dt1")),
            "v1", "dt1",
          )
        }
        e.getMessage should include ("Datatype literals cannot be encoded when the datatype table")
      }
    }

    "encoding IRIs" should {
      "add a full IRI" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder()
        encoder.makeIri("https://test.org/Cake")
        val iri = termBuffer.head.asInstanceOf[RdfIri]
        iri.getNameId should be (0)
        iri.getPrefixId should be (1)

        entryBuffer.size should be (2)
        entryBuffer should contain (rdfStreamRow(
          rdfPrefixEntry(id = 0, value = "https://test.org/")
        ))
        entryBuffer should contain (rdfStreamRow(
          rdfNameEntry(id = 0, value = "Cake")
        ))
      }

      "add a prefix-only IRI" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder()
        encoder.makeIri("https://test.org/test/")
        val iri = termBuffer.head.asInstanceOf[RdfIri]
        iri.getNameId should be (0)
        iri.getPrefixId should be (1)

        // an empty name entry still has to be allocated
        entryBuffer.size should be (2)
        entryBuffer should contain (rdfStreamRow(
          rdfPrefixEntry(id = 0, value = "https://test.org/test/")
        ))
        entryBuffer should contain(rdfStreamRow(
          rdfNameEntry(id = 0, value = "")
        ))
      }

      "add a name-only IRI" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder()
        encoder.makeIri("testTestTest")
        val iri = termBuffer.head.asInstanceOf[RdfIri]
        iri.getNameId should be (0)
        iri.getPrefixId should be (1)

        // in the mode with the prefix table enabled, an empty prefix entry still has to be allocated
        entryBuffer.size should be (2)
        entryBuffer should contain (rdfStreamRow(
          rdfPrefixEntry(id = 0, value = "")
        ))
        entryBuffer should contain (rdfStreamRow(
          rdfNameEntry(id = 0, value = "testTestTest")
        ))
      }

      "add a full IRI in no-prefix table mode" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder(0)
        encoder.makeIri("https://test.org/Cake")
        val iri = termBuffer.head.asInstanceOf[RdfIri]
        iri.getNameId should be (0)
        iri.getPrefixId should be (0)

        // in the no prefix mode, there must be no prefix entries
        entryBuffer.size should be (1)
        entryBuffer should contain (rdfStreamRow(
          rdfNameEntry(id = 0, value = "https://test.org/Cake")
        ))
      }

      "add IRIs while evicting old ones" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder(3)
        val data = Seq(
          // IRI, expected prefix ID, expected name ID
          ("https://test.org/Cake1", 1, 0),
          ("https://test.org/Cake1", 0, 1),
          ("https://test.org/Cake1", 0, 1),
          ("https://test.org#Cake1", 2, 1),
          ("https://test.org/test/Cake1", 3, 1),
          ("https://test.org/Cake2", 1, 0),
          ("https://test.org#Cake2", 2, 2),
          ("https://test.org/other/Cake1", 3, 1),
          ("https://test.org/other/Cake2", 0, 0),
          ("https://test.org/other/Cake3", 0, 0),
          ("https://test.org/other/Cake4", 0, 0),
          ("https://test.org/other/Cake1", 0, 1),
          ("https://test.org/other/Cake2", 0, 0),
          ("https://test.org/other/Cake3", 0, 0),
          ("https://test.org/other/Cake4", 0, 0),
          ("https://test.org/other/Cake5", 0, 0),
          ("https://test.org/other/Cake6", 0, 0),
          ("https://test.org/other/Cake7", 0, 0),
          ("https://test.org/other/Cake8", 0, 0),
          ("https://test.org/other/Cake9", 0, 1),
          ("https://test.org/other/Cake9", 0, 1),
          ("https://test.org#Cake2", 2, 0),
          ("https://test.org#Cake9", 0, 1),
          // prefix "" evicts the previous number #1
          ("Cake2", 1, 0),
        )

        for (sIri, ePrefix, eName) <- data do
          encoder.makeIri(sIri)
          val iri = termBuffer.head.asInstanceOf[RdfIri]
          termBuffer.clear()
          iri.getPrefixId should be (ePrefix)
          iri.getNameId should be (eName)

        val expectedBuffer = Seq(
          // Prefix? (name otherwise), ID, value
          (true, 0, "https://test.org/"),
          (false, 0, "Cake1"),
          (true, 0, "https://test.org#"),
          (true, 0, "https://test.org/test/"),
          (false, 0, "Cake2"),
          (true, 3, "https://test.org/other/"),
          (false, 0, "Cake3"),
          (false, 0, "Cake4"),
          (false, 0, "Cake5"),
          (false, 0, "Cake6"),
          (false, 0, "Cake7"),
          (false, 0, "Cake8"),
          (false, 1, "Cake9"),
          (true, 1, ""),
        )

        entryBuffer.size should be (expectedBuffer.size)
        for ((isPrefix, eId, eVal), row) <- expectedBuffer.zip(entryBuffer) do
          if isPrefix then
            row.hasPrefix should be (true)
            val prefix = row.getPrefix
            prefix.getId should be (eId)
            prefix.getValue should be (eVal)
          else
            row.hasName should be (true)
            val name = row.getName
            name.getId should be (eId)
            name.getValue should be (eVal)
      }

      "add IRIs while evicting old ones (2: detecting invalidated prefix entries)" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder(3)
        val data = Seq(
          // IRI, expected prefix ID, expected name ID
          ("https://test.org/1/Cake1", 1, 0),
          ("https://test.org/2/Cake1", 2, 1),
          ("https://test.org/3/Cake1", 3, 1),
          ("https://test.org/3/Cake2", 0, 0),
          // Evict the /1/ prefix
          ("https://test.org/4/Cake2", 1, 2),
          // Try to get the first IRI
          ("https://test.org/1/Cake1", 2, 1),
        )

        for (sIri, ePrefix, eName) <- data do
          encoder.makeIri(sIri)
          val iri = termBuffer.head.asInstanceOf[RdfIri]
          termBuffer.clear()
          iri.getPrefixId should be(ePrefix)
          iri.getNameId should be(eName)

        val expectedBuffer = Seq(
          // Prefix? (name otherwise), ID, value
          (true, 0, "https://test.org/1/"),
          (false, 0, "Cake1"),
          (true, 0, "https://test.org/2/"),
          (true, 0, "https://test.org/3/"),
          (false, 0, "Cake2"),
          (true, 1, "https://test.org/4/"),
          (true, 0, "https://test.org/1/"),
        )

        entryBuffer.size should be(expectedBuffer.size)
        for ((isPrefix, eId, eVal), row) <- expectedBuffer.zip(entryBuffer) do
          if isPrefix then
            row.hasPrefix should be (true)
            val prefix = row.getPrefix
            prefix.getId should be(eId)
            prefix.getValue should be(eVal)
          else
            row.hasName should be (true)
            val name = row.getName
            name.getId should be(eId)
            name.getValue should be(eVal)
      }

      "not evict IRI prefixes used recently" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder(3)
        val data = Seq(
          // IRI, expected prefix ID, expected name ID
          ("https://test.org/1/Cake1", 1, 0),
          ("https://test.org/2/Cake2", 2, 0),
          ("https://test.org/3/Cake3", 3, 0),
          ("https://test.org/3/Cake3", 0, 3),
          ("https://test.org/2/Cake2", 2, 2),
          ("https://test.org/1/Cake1", 1, 1),
          // Evict something -- this must not be /1/ because it was used last
          // this tests if .onAccess() is called correctly
          ("https://test.org/4/Cake4", 3, 4),
        )

        for (sIri, ePrefix, eName) <- data do
          encoder.makeIri(sIri)
          val iri = termBuffer.head.asInstanceOf[RdfIri]
          termBuffer.clear()
          iri.getPrefixId should be(ePrefix)
          iri.getNameId should be(eName)
      }

      "add IRIs while evicting old ones, without a prefix table" in {
        val (encoder, entryBuffer, termBuffer) = getEncoder(0)
        val data = Seq(
          // IRI, expected name ID
          ("https://test.org/Cake1", 0),
          ("https://test.org/Cake1", 1),
          ("https://test.org/Cake1", 1),
          ("https://test.org#Cake1", 0),
          ("https://test.org/test/Cake1", 0),
          ("https://test.org/Cake2", 0),
          ("https://test.org/Cake3", 0),
          ("https://test.org/Cake4", 0),
          ("https://test.org/Cake5", 0),
          ("https://test.org/Cake6", 0),
          ("https://test.org#Cake2", 1),
          ("https://test.org/other/Cake1", 0),
          ("https://test.org/other/Cake2", 0),
          ("https://test.org/other/Cake3", 0),
          ("https://test.org/other/Cake1", 2),
          ("https://test.org/other/Cake2", 0),
          ("https://test.org/other/Cake3", 0),
          ("https://test.org/other/Cake3_1", 0),
          ("https://test.org/other/Cake3_2", 0),
          ("https://test.org/other/Cake3_3", 0),
          ("https://test.org/other/Cake3_4", 0),
          ("https://test.org/other/Cake4", 1),
          ("https://test.org/other/Cake5", 0),
          ("https://test.org/other/Cake5", 2),
          ("https://test.org/other/Cake3", 4),
        )

        for (sIri, eName) <- data do
          encoder.makeIri(sIri)
          val iri = termBuffer.head.asInstanceOf[RdfIri]
          termBuffer.clear()
          iri.getPrefixId should be(0)
          iri.getNameId should be(eName)
      }

      "throw exception if name table size = 1" in {
        val e = intercept[RdfProtoSerializationError] {
          NodeEncoderImpl[Mrl.Node](
            16, 1, 16, 16, 16, 16, null
          )
        }
        e.getMessage should include("Requested name table size of 1 is too small. The minimum is 8.")
      }
    }
  }
