package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.NodeEncoder.DependentNode
import eu.ostrzyciel.jelly.core.helpers.Mrl
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer
import scala.util.Random

class NodeEncoderSpec extends AnyWordSpec, Inspectors, Matchers:
  def smallOptions(prefixTableSize: Int) = RdfStreamOptions(
    maxNameTableSize = 4,
    maxPrefixTableSize = prefixTableSize,
    maxDatatypeTableSize = 8,
  )

  private def getEncoder(prefixTableSize: Int = 8): (NodeEncoder[Mrl.Node], ListBuffer[RdfStreamRow]) =
    val buffer = new ListBuffer[RdfStreamRow]()
    (NodeEncoder[Mrl.Node](smallOptions(prefixTableSize), 16, 16), buffer)

  "A NodeEncoder" when {
//    "encoding datatype literals" should {
//      "encode a datatype literal" in {
//        val (encoder, buffer) = getEncoder()
//        val dt = encoder.encodeDtLiteral(
//          Mrl.DtLiteral("v1", Mrl.Datatype("dt1")),
//          "v1", "dt1",
//          buffer,
//        )
//        dn.encoded should be (null)
//        dt.literal should be (1)
//        dn.lookupSerial1 should be (1)
//        buffer.size should be (1)
//        buffer.head.row.isDatatype should be (true)
//        val dtEntry = buffer.head.row.datatype
//        dtEntry.value should be ("dt1")
//        dtEntry.id should be (0)
//      }
//
//      "encode multiple datatype literals and reuse existing datatypes" in {
//        val (encoder, buffer) = getEncoder()
//        for i <- 1 to 4 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
//            buffer,
//            () => s"dt$i"
//          )
//          dn.encoded should be (null)
//          dn.lookupPointer1 should be (i)
//          dn.lookupSerial1 should be (1)
//          dn.encoded = RdfLiteral(s"v$i", RdfLiteral.LiteralKind.Datatype(dn.lookupPointer1))
//
//        // "dt3" datatype should be reused
//        val dn = encoder.encodeDtLiteral(
//          Mrl.DtLiteral(s"v1000", Mrl.Datatype(s"dt3")),
//          buffer,
//          () => "dt3"
//        )
//        dn.encoded should be (null)
//        dn.lookupPointer1 should be (3)
//        dn.lookupSerial1 should be (1)
//
//        // "v2"^^<dt2> should be reused
//        val dn2 = encoder.encodeDtLiteral(
//          Mrl.DtLiteral("v2", Mrl.Datatype("dt2")),
//          buffer,
//          () => "dt2"
//        )
//        dn2.encoded should be (RdfLiteral("v2", RdfLiteral.LiteralKind.Datatype(2)))
//        dn2.lookupPointer1 should be (2)
//        dn2.lookupSerial1 should be (1)
//
//        buffer.size should be (4)
//        buffer.map(_.row.datatype) should contain only (
//          RdfDatatypeEntry(0, "dt1"),
//          RdfDatatypeEntry(0, "dt2"),
//          RdfDatatypeEntry(0, "dt3"),
//          RdfDatatypeEntry(0, "dt4"),
//        )
//      }
//
//      "encode datatype literals while evicting old datatypes" in {
//        val (encoder, buffer) = getEncoder()
//        for i <- 1 to 12 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
//            buffer,
//            () => s"dt$i"
//          )
//          // first 4 datatypes should be evicted
//          dn.lookupPointer1 should be ((i - 1) % 8 + 1)
//          dn.lookupSerial1 should be ((i - 1) / 8 + 1)
//
//        for i <- 9 to 12 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
//            buffer,
//            () => s"dt$i"
//          )
//          dn.lookupPointer1 should be (i - 8)
//          dn.lookupSerial1 should be (2)
//
//        for i <- 5 to 8 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
//            buffer,
//            () => s"dt$i"
//          )
//          dn.lookupPointer1 should be (i)
//          dn.lookupSerial1 should be (1)
//
//        // 5–8 were used last, so they should be evicted last
//        for i <- 13 to 16 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
//            buffer,
//            () => s"dt$i"
//          )
//          dn.lookupPointer1 should be (i - 12) // 1–4
//          dn.lookupSerial1 should be (3)
//
//        buffer.size should be (16)
//        val expectedIds = Array.from(
//          Iterable.fill(8)(0) ++ Seq(1) ++ Iterable.fill(3)(0) ++ Seq(1) ++ Iterable.fill(3)(0)
//        )
//        for (r, i) <- buffer.zipWithIndex do
//          val dt = r.row.datatype
//          dt.id should be (expectedIds(i))
//          dt.value should be (s"dt${i + 1}")
//      }
//
//      "reuse already encoded literals, evicting old ones" in {
//        val (encoder, buffer) = getEncoder()
//        for i <- 1 to 4; j <- 1 to 4 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$j")),
//            buffer,
//            () => s"dt$j"
//          )
//          dn.encoded should be (null)
//          dn.lookupPointer1 should be (j)
//          dn.lookupSerial1 should be (1)
//          dn.encoded = RdfLiteral(s"v$i", RdfLiteral.LiteralKind.Datatype(dn.lookupPointer1))
//
//        for _ <- 1 to 10 do
//          for i <- Random.shuffle(1 to 4); j <- Random.shuffle(1 to 4) do
//            val dn = encoder.encodeDtLiteral(
//              Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$j")),
//              buffer,
//              () => s"dt$j"
//            )
//            dn.encoded should be (RdfLiteral(s"v$i", RdfLiteral.LiteralKind.Datatype(j)))
//            dn.lookupPointer1 should be (j)
//            dn.lookupSerial1 should be (1)
//
//        // Add more literals to evict the old ones
//        for j <- 101 to 104 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v100", Mrl.Datatype(s"dt${j - 100}")),
//            buffer,
//            () => s"dt${j - 100}"
//          )
//          dn.encoded should be (null)
//          dn.lookupPointer1 should be (j - 100)
//          dn.lookupSerial1 should be (1)
//          dn.encoded = RdfLiteral(s"v100", RdfLiteral.LiteralKind.Datatype(dn.lookupPointer1))
//
//        // These entries should have been evicted, we will get nulls here
//        for j <- 1 to 4 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v1", Mrl.Datatype(s"dt$j")),
//            buffer,
//            () => s"dt$j"
//          )
//          dn.encoded should be (null)
//          dn.lookupPointer1 should be (j)
//          dn.lookupSerial1 should be (1)
//      }
//
//      "invalidate cached datatype literals when their datatypes are evicted" in {
//        val (encoder, buffer) = getEncoder()
//        for i <- 1 to 4 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
//            buffer,
//            () => s"dt$i"
//          )
//          dn.encoded should be (null)
//          dn.lookupPointer1 should be (i)
//          dn.lookupSerial1 should be (1)
//          dn.encoded = RdfLiteral(s"v$i", RdfLiteral.LiteralKind.Datatype(dn.lookupPointer1))
//
//        for i <- 5 to 12 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
//            buffer,
//            () => s"dt$i"
//          )
//          dn.encoded should be (null)
//          dn.lookupPointer1 should be ((i - 1) % 8 + 1)
//          dn.lookupSerial1 should be ((i - 1) / 8 + 1)
//          dn.encoded = RdfLiteral(s"v$i", RdfLiteral.LiteralKind.Datatype(dn.lookupPointer1))
//
//        for i <- 1 to 4 do
//          val dn = encoder.encodeDtLiteral(
//            Mrl.DtLiteral(s"v$i", Mrl.Datatype(s"dt$i")),
//            buffer,
//            () => s"dt$i"
//          )
//          dn.encoded should be (null)
//          dn.lookupPointer1 should be (i + 4)
//          dn.lookupSerial1 should be (2)
//      }
//    }

    "encoding IRIs" should {
      "add a full IRI" in {
        val (encoder, buffer) = getEncoder()
        val iri = encoder.encodeIri("https://test.org/Cake", buffer).asInstanceOf[RdfIri]
        iri.nameId should be (0)
        iri.prefixId should be (1)

        buffer.size should be (2)
        buffer should contain (RdfStreamRow(RdfStreamRow.Row.Prefix(
          RdfPrefixEntry(id = 0, value = "https://test.org/")
        )))
        buffer should contain (RdfStreamRow(RdfStreamRow.Row.Name(
          RdfNameEntry(id = 0, value = "Cake")
        )))
      }

      "add a prefix-only IRI" in {
        val (encoder, buffer) = getEncoder()
        val iri = encoder.encodeIri("https://test.org/test/", buffer).asInstanceOf[RdfIri]
        iri.nameId should be (0)
        iri.prefixId should be (1)

        // an empty name entry still has to be allocated
        buffer.size should be (2)
        buffer should contain (RdfStreamRow(RdfStreamRow.Row.Prefix(
          RdfPrefixEntry(id = 0, value = "https://test.org/test/")
        )))
        buffer should contain(RdfStreamRow(RdfStreamRow.Row.Name(
          RdfNameEntry(id = 0, value = "")
        )))
      }

      "add a name-only IRI" in {
        val (encoder, buffer) = getEncoder()
        val iri = encoder.encodeIri("testTestTest", buffer).asInstanceOf[RdfIri]
        iri.nameId should be (0)
        iri.prefixId should be (1)

        // in the mode with the prefix table enabled, an empty prefix entry still has to be allocated
        buffer.size should be (2)
        buffer should contain(RdfStreamRow(RdfStreamRow.Row.Prefix(
          RdfPrefixEntry(id = 0, value = "")
        )))
        buffer should contain (RdfStreamRow(RdfStreamRow.Row.Name(
          RdfNameEntry(id = 0, value = "testTestTest")
        )))
      }

      "add a full IRI in no-prefix table mode" in {
        val (encoder, buffer) = getEncoder(0)
        val iri = encoder.encodeIri("https://test.org/Cake", buffer).asInstanceOf[RdfIri]
        iri.nameId should be (0)
        iri.prefixId should be (0)

        // in the no prefix mode, there must be no prefix entries
        buffer.size should be (1)
        buffer should contain (RdfStreamRow(RdfStreamRow.Row.Name(
          RdfNameEntry(id = 0, value = "https://test.org/Cake")
        )))
      }

      "add IRIs while evicting old ones" in {
        val (encoder, buffer) = getEncoder(3)
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
          ("https://test.org/other/Cake5", 0, 1),
          ("https://test.org/other/Cake5", 0, 1),
          ("https://test.org#Cake2", 2, 0),
          ("https://test.org#Cake5", 0, 1),
          // prefix "" evicts the previous number #1
          ("Cake2", 1, 0),
        )

        for (sIri, ePrefix, eName) <- data do
          val iri = encoder.encodeIri(sIri, buffer).asInstanceOf[RdfIri]
          iri.prefixId should be (ePrefix)
          iri.nameId should be (eName)

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
          (false, 1, "Cake5"),
          (true, 1, ""),
        )

        buffer.size should be (expectedBuffer.size)
        for ((isPrefix, eId, eVal), row) <- expectedBuffer.zip(buffer) do
          if isPrefix then
            row.row.isPrefix should be (true)
            val prefix = row.row.prefix
            prefix.id should be (eId)
            prefix.value should be (eVal)
          else
            row.row.isName should be (true)
            val name = row.row.name
            name.id should be (eId)
            name.value should be (eVal)
      }
    }
  }
