package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.helpers.ProtoCollector
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.helpers.RdfAdapter.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ArrayBuffer

class ProtoDecoderSpec extends AnyWordSpec, Matchers:
  import eu.ostrzyciel.jelly.core.internal.ProtoDecoderImpl.*
  import ProtoTestCases.*

  private val defaultOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS

  "checkLogicalStreamType" should {
    val decoderFactories = Seq(
      ("TriplesDecoder", (MockConverterFactory.triplesDecoder, PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)),
      ("QuadsDecoder", (MockConverterFactory.quadsDecoder, PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)),
      ("GraphsAsQuadsDecoder", (MockConverterFactory.graphsAsQuadsDecoder, PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)),
      ("GraphsDecoder", (MockConverterFactory.graphsDecoder, PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)),
    ).toMap
    val logicalStreamTypeSets = Seq(
      (
        Seq(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES),
        Seq("TriplesDecoder")
      ),
      (
        Seq(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_QUADS),
        Seq("QuadsDecoder", "GraphsAsQuadsDecoder")
      ),
      (
        Seq(
          LogicalStreamType.LOGICAL_STREAM_TYPE_GRAPHS,
          LogicalStreamType.LOGICAL_STREAM_TYPE_SUBJECT_GRAPHS,
        ),
        Seq("TriplesDecoder")
      ),
      (
        Seq(
          LogicalStreamType.LOGICAL_STREAM_TYPE_DATASETS,
          LogicalStreamType.LOGICAL_STREAM_TYPE_NAMED_GRAPHS,
          LogicalStreamType.LOGICAL_STREAM_TYPE_TIMESTAMPED_NAMED_GRAPHS,
        ),
        Seq("QuadsDecoder", "GraphsDecoder", "GraphsAsQuadsDecoder")
      ),
      (
        Seq(
          LogicalStreamType.LOGICAL_STREAM_TYPE_NAMED_GRAPHS,
          LogicalStreamType.LOGICAL_STREAM_TYPE_TIMESTAMPED_NAMED_GRAPHS,
        ),
        Seq("GraphsDecoder")
      )
    )

    for
      (logicalStreamTypeSet, decoders) <- logicalStreamTypeSets
      decoderName <- decoders
    do
      val lst = logicalStreamTypeSet.head
      val (decoderF, pst) = decoderFactories(decoderName)

      f"throw exception when expecting logical type $lst on a stream with no logical type, with $decoderName" in {
        val collector = ProtoCollector()
        val decoder = decoderF(
          collector,
          defaultOptions.toBuilder.setLogicalType(lst).build(),
          (_, _) => ()
        )

        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(pst)
            .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_UNSPECIFIED)
            .build()
        ))
        val error = intercept[RdfProtoDeserializationError] {
          decoder.ingestRow(data.head)
        }
        error.getMessage should include("Expected logical stream type")
      }

      for lstOfStream <- logicalStreamTypeSet do
        f"accept stream with logical type $lstOfStream when expecting $lst, with $decoderName" in {
          val collector = ProtoCollector()
          val decoder = decoderF(
            collector,
            defaultOptions.toBuilder.setLogicalType(lst).build(),
            (_, _) => ()
          )

          val data = wrapEncoded(Seq(
            JellyOptions.SMALL_GENERALIZED.toBuilder
              .setPhysicalType(pst)
              .setLogicalType(lstOfStream)
              .build()
          ))

          decoder.ingestRow(data.head)
          decoder.getStreamOptions.getLogicalType should be (lstOfStream)
        }

    for
      (pst, decs) <- decoderFactories.groupBy(_._2._2)
      (decoderName, (decoderF, _)) <- decs
      (lstSet, _) <- logicalStreamTypeSets.take(4).filterNot(x => x._2.exists(y => decs.exists(z => z._1 == y)))
      lstOfStream <- lstSet
    do
      f"throw exception that a stream with logical type $lstOfStream is incompatible with $pst, with $decoderName" in {
        val collector = ProtoCollector()
        val decoder = decoderF(collector, defaultOptions, (_, _) => ())

        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(pst)
            .setLogicalType(lstOfStream)
            .build()
        ))

        val error = intercept[RdfProtoDeserializationError] {
          decoder.ingestRow(data.head)
        }

        error.getMessage should include("is incompatible with physical stream type")
      }
  }

  // Test body
  "a TriplesDecoder" should {
    "decode triple statements" in {
      val collector = ProtoCollector()
      val decoder = MockConverterFactory.triplesDecoder(
        collector,
        defaultOptions.toBuilder
          .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES)
          .build()
      )

      Triples1
        .encoded(
          JellyOptions.SMALL_GENERALIZED
            .toBuilder
            .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
            .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES)
            .build()
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Triples1.mrl)
    }

    "decode triple statements with unset expected logical stream type" in {
      val collector = ProtoCollector()
      val decoder = MockConverterFactory.triplesDecoder(collector)
      Triples1
        .encoded(
          JellyOptions.SMALL_GENERALIZED
            .toBuilder
            .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
            .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES)
            .build()
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Triples1.mrl)
    }

    "decode triple statements with namespace declarations" in {
      val namespaces = ArrayBuffer[(String, Node)]()
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(
        collector,
        defaultOptions.toBuilder
          .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES)
          .build(),
        (name, iri) => namespaces.append((name, iri))
      )

      Triples2NsDecl
        .encoded(
          JellyOptions.SMALL_GENERALIZED
            .toBuilder
            .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
            .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES)
            .build()
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Triples2NsDecl.mrl.filter(_.isInstanceOf[Triple]).asInstanceOf[Seq[Triple]])
      namespaces.toSeq should be (Seq(
        ("test", Iri("https://test.org/test/")),
        ("ns2", Iri("https://test.org/ns2/")),
      ))
    }

    "ignore namespace declarations by default" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(
        collector,
        defaultOptions.toBuilder
          .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES)
          .build()
      )

      Triples2NsDecl
        .encoded(
          JellyOptions.SMALL_GENERALIZED
            .toBuilder
            .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
            .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES)
            .build()
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Triples2NsDecl.mrl.filter(_.isInstanceOf[Triple]).asInstanceOf[Seq[Triple]])
    }

    "throw exception on unset logical stream type" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(
        collector,
        defaultOptions.toBuilder
          .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES)
          .build()
      )

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED
          .toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
          .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_UNSPECIFIED)
          .build()
      ))
      
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data.head)
      }
      
      error.getMessage should include ("Expected logical stream type")
    }

    "throw exception on a quad in a TRIPLES stream" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector)
      
      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
          .build(),
        rdfQuad("1", "2", "3", "4"),
      ))

      decoder.ingestRow(data.head)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }

      error.getMessage should include ("Unexpected quad row in stream")
    }

    // The following cases are for the [[ProtoDecoder]] base class – but tested on the child.
    // The code is the same in quads, triples, or graphs decoders, so this is fine.
    // Code coverage checks out.
    "ignore duplicate stream options" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
          .build(),
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
          .setRdfStar(true)
          .build(),
      ))

      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))
      decoder.getStreamOptions should not be null
      decoder.getStreamOptions.getRdfStar should be (false)
    }

    "throw exception on unset term without preceding value" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector)
      
      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
          .build(),
        rdfTriple(null, null, null),
      ))
      
      decoder.ingestRow(data.head)
      
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      
      error.getMessage should include ("Empty term without previous term")
    }

    "throw exception on an empty term in a quoted triple" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
          .build(),
        rdfTriple("1", "2", rdfTriple(null, null, null))
      ))

      decoder.ingestRow(data.head)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }

      error.getMessage should include ("Term value is not set inside a quoted triple")
    }

    "throw exception on unset row kind" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(rdfStreamRow())
      }

      error.getMessage should include ("Row kind is not set")
    }

    "interpret unset literal kind as a simple literal" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
          .build(),
        rdfTriple("1", "2", rdfLiteral("test")),
      ))

      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))

      val r = collector.statements.head.asInstanceOf[Triple]
      r.o should be (a[SimpleLiteral])
    }

    // The tests for this logic are in internal.NameDecoderSpec
    // Here we are just testing if the exceptions are rethrown correctly.
    "throw exception on out-of-bounds references to lookups" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
          .build(),
        rdfTriple("1", "2", rdfIri(10000, 0)),
      ))

      decoder.ingestRow(data.head)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }

      error.getMessage should include ("Error while decoding term")
      error.getCause shouldBe a [ArrayIndexOutOfBoundsException]
    }
  }

  "a QuadsDecoder" should {
    "decode quad statements" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.quadsDecoder(collector)

      Quads1
        .encoded(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)
            .build(),
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Quads1.mrl)
    }

    "decode quad statements (repeated default graph)" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.quadsDecoder(collector)

      Quads2RepeatDefault
        .encoded(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)
            .build(),
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Quads2RepeatDefault.mrl)
    }

    "throw exception on a triple in a QUADS stream" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.quadsDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)
          .build(),
        rdfTriple("1", "2", "3"),
      ))

      decoder.ingestRow(data.head)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }

      error.getMessage should include ("Unexpected triple row in stream")
    }

    "throw exception on a graph start in a QUADS stream" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.quadsDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)
          .build(),
        rdfGraphStart(rdfDefaultGraph()),
      ))

      decoder.ingestRow(data.head)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }

      error.getMessage should include ("Unexpected start of graph in stream")
    }

    "throw exception on a graph end in a QUADS stream" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.quadsDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)
          .build(),
        rdfGraphEnd(),
      ))

      decoder.ingestRow(data.head)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }

      error.getMessage should include ("Unexpected end of graph in stream")
    }
  }

  "a GraphsDecoder" should {
    "decode graphs" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.graphsDecoder(collector)

      Graphs1
        .encoded(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)
            .build(),
        )
        .foreach(row => decoder.ingestRow(row))

      for ix <- 0 until collector.statements.size.max(Graphs1.mrl.size) do
        val obsRow = collector.statements.applyOrElse(ix, null)
        val expRow = Graphs1.mrl.applyOrElse(ix, null)

        withClue(s"Graph row $ix:") {
          obsRow should not be null
          expRow should not be null

          val obsRowGraph = obsRow.asInstanceOf[Graph]
          obsRowGraph.graph should be (expRow._1)
          assertDecoded(obsRowGraph.triples.toSeq, expRow._2.toSeq)
        }
    }

    "throw exception on a quad in a GRAPHS stream" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.graphsDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)
          .build(),
        rdfQuad("1", "2", "3", "4"),
      ))

      decoder.ingestRow(data.head)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }

      error.getMessage should include ("Unexpected quad row in stream")
    }

    "throw exception on a graph end before a graph start" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.graphsDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)
          .build(),
        rdfTriple("1", "2", "3"),
        rdfGraphEnd(),
      ))

      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(2))
      }

      error.getMessage should include ("End of graph encountered before a start")
    }

    // The following cases are for the [[ProtoDecoder]] base class – but tested on the child.
    "throw exception on unset graph term in a GRAPHS stream" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.graphsDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)
          .build(),
        rdfGraphStart(),
      ))

      decoder.ingestRow(data.head)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }

      error.getMessage should include ("Empty graph term encountered")
    }
  }

  "a GraphsAsQuadsDecoder" should {
    "decode graphs as quads" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.graphsAsQuadsDecoder(collector)

      Graphs1
        .encoded(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)
            .build(),
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Graphs1.mrlQuads)
    }

    "throw exception on a triple before a graph start" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.graphsAsQuadsDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)
          .build(),
        rdfTriple("1", "2", "3"),
      ))

      decoder.ingestRow(data.head)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }

      error.getMessage should include ("Triple in stream without preceding graph start")
    }

    // The tests for this logic are in internal.NameDecoderSpec
    // Here we are just testing if the exceptions are rethrown correctly.
    "throw exception on out-of-bounds references to lookups (graph term)" in {
      val collector = ProtoCollector()
      val decoder = MockConverterFactory.graphsAsQuadsDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)
          .build(),
        rdfGraphStart(rdfIri(10000, 0)),
      ))

      decoder.ingestRow(data.head)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }

      error.getMessage should include ("Error while decoding graph term")
      error.getCause shouldBe a [ArrayIndexOutOfBoundsException]
    }
  }

  "an AnyStatementDecoder" should {
    val cases = Seq(
      (Triples1, PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES, "triples", Triples1.mrl),
      (Quads1, PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS, "quads", Quads1.mrl),
      (Graphs1, PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS, "graphs", Graphs1.mrlQuads),
    )

    for ((testCase, streamType, streamName, expected) <- cases) do
      s"decode $streamName" in {
        val collector = ProtoCollector()

        val opts = JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(streamType)
          .setVersion(JellyConstants.PROTO_VERSION)
          .build()

        val decoder = MockConverterFactory.anyDecoder(collector)

        testCase
          .encoded(opts)
          .foreach(row => decoder.ingestRow(row))

        assertDecoded(collector.statements.toSeq, expected)
        decoder.getStreamOptions should be (opts)
      }

    "should return None when retrieving stream options on an empty stream" in {
      val collector = ProtoCollector()
      val decoder = MockConverterFactory.anyDecoder(collector)
      decoder.getStreamOptions should be (null)
    }

    "should throw when decoding a row without preceding options" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.anyDecoder(collector)

      val data = wrapEncoded(Seq(
        rdfTriple("1", "2", "3"),
      ))

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data.head)
      }

      error.getMessage should include ("Stream options are not set")
    }

    "should ignore multiple stream options" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.anyDecoder(collector)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
          .build(),
        JellyOptions.SMALL_GENERALIZED.toBuilder
          .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
          .build(),
        rdfTriple("1", "2", "3"),
      ))

      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))
      decoder.ingestRow(data(2))

      collector.statements(1) should be (a[Triple])
    }
  }

  private val streamTypeCases = Seq(
    (
      (o: Option[RdfStreamOptions]) => MockConverterFactory.triplesDecoder(
        ProtoCollector(),
        o.orElse(Some(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)).get
      ),
      "Triples",
      PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES,
      PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS
    ),
    (
      (o: Option[RdfStreamOptions]) => MockConverterFactory.quadsDecoder(
        ProtoCollector(),
        o.orElse(Some(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)).get
      ),
      "Quads",
      PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS,
      PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS
    ),
    (
      (o: Option[RdfStreamOptions]) => MockConverterFactory.graphsDecoder(
        ProtoCollector(),
        o.orElse(Some(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)).get
      ),
      "Graphs",
      PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS,
      PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS
    ),
    (
      (o: Option[RdfStreamOptions]) => MockConverterFactory.graphsAsQuadsDecoder(
        ProtoCollector(),
        o.orElse(Some(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)).get
      ),
      "GraphsAsQuads",
      PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS,
      PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES
    ),
    (
      (o: Option[RdfStreamOptions]) => MockConverterFactory.anyDecoder(
        ProtoCollector(),
        o.orElse(Some(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)).get
      ),
      "AnyStatement",
      PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES,
      PhysicalStreamType.PHYSICAL_STREAM_TYPE_UNSPECIFIED
    ),
  )

  for (decoderFactory, decName, streamType, invalidStreamType) <- streamTypeCases do
    s"a ${decName}Decoder" should {
      "throw exception on an empty stream type" in {
        val data = wrapEncoded(Seq(JellyOptions.SMALL_GENERALIZED))

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(None).ingestRow(data.head)
        }

        error.getMessage should include ("stream type is not")
      }

      "throw exception on an invalid stream type" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED
            .toBuilder
            .setPhysicalType(invalidStreamType)
            .build()
        ))

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(None).ingestRow(data.head)
        }

        error.getMessage should include ("stream type is not")
      }

      "throw exception on an unsupported proto version" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED
            .toBuilder
            .setPhysicalType(streamType)
            .setVersion(JellyConstants.PROTO_VERSION + 1)
            .build()
        ))

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(None).ingestRow(data.head)
        }

        error.getMessage should include("Unsupported proto version")
      }

      "throw exception on a proto version higher than marked by the user as supported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(streamType)
            .setVersion(JellyConstants.PROTO_VERSION)
            .build()
        ))

        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.toBuilder
          .setVersion(JellyConstants.PROTO_VERSION - 1)
          .build()

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("Unsupported proto version")
      }

      "throw exception on a stream with generalized statements if marked as unsupported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(streamType)
            .build()
        ))

        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.toBuilder
          .setGeneralizedStatements(false)
          .build()

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("stream uses generalized statements")
      }

      "throw exception on a stream with RDF-star if marked as unsupported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_RDF_STAR.toBuilder
            .setPhysicalType(streamType)
            .build()
        ))

        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.toBuilder
          .setRdfStar(false)
          .build()
        
        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("stream uses RDF-star")
      }

      "throw exception on a stream with a name table size larger than supported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(streamType)
            .setMaxNameTableSize(100)
            .build()
        ))

        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.toBuilder
          .setMaxNameTableSize(80)
          .build()

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("name table size of 100")
        error.getMessage should include("larger than the maximum supported size of 80")
      }

      "throw exception on a stream with a prefix table size larger than supported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(streamType)
            .setMaxPrefixTableSize(100)
            .build()
        ))
        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.toBuilder
          .setMaxPrefixTableSize(80)
          .build()

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("prefix table size of 100")
        error.getMessage should include("larger than the maximum supported size of 80")
      }

      "throw exception on a stream with a datatype table size larger than supported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(streamType)
            .setMaxDatatypeTableSize(100)
            .build()
        ))

        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.toBuilder
          .setMaxDatatypeTableSize(80)
          .build()

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("datatype table size of 100")
        error.getMessage should include("larger than the maximum supported size of 80")
      }

      "throw exception on a stream with a name table size smaller than supported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(streamType)
            .setMaxNameTableSize(2) // 8 is the minimum
            .build()
        ))

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(None).ingestRow(data.head)
        }

        error.getMessage should include("name table size of 2")
        error.getMessage should include("smaller than the minimum supported size of 8")
      }

      "accept a datatype table size = 0" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.toBuilder
            .setPhysicalType(streamType)
            .setMaxDatatypeTableSize(0)
            .build()
        ))

        decoderFactory(None).ingestRow(data.head) should be (None)
      }
    }
