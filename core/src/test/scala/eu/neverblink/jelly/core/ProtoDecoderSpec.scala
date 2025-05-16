package eu.neverblink.jelly.core

import eu.neverblink.jelly.core.{JellyConstants, JellyOptions, RdfProtoDeserializationError}
import eu.neverblink.jelly.core.helpers.Assertions.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.helpers.{MockConverterFactory, ProtoCollector}
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProtoDecoderSpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*
  import eu.neverblink.jelly.core.internal.ProtoDecoderImpl.*

  private val defaultOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS

  "checkLogicalStreamType" should {
    val decoderFactories = Seq(
      ("TriplesDecoder", (MockConverterFactory.triplesDecoder, PhysicalStreamType.TRIPLES)),
      ("QuadsDecoder", (MockConverterFactory.quadsDecoder, PhysicalStreamType.QUADS)),
      ("GraphsAsQuadsDecoder", (MockConverterFactory.graphsAsQuadsDecoder, PhysicalStreamType.GRAPHS)),
      ("GraphsDecoder", (MockConverterFactory.graphsDecoder, PhysicalStreamType.GRAPHS)),
    ).toMap
    val logicalStreamTypeSets = Seq(
      (
        Seq(LogicalStreamType.FLAT_TRIPLES),
        Seq("TriplesDecoder")
      ),
      (
        Seq(LogicalStreamType.FLAT_QUADS),
        Seq("QuadsDecoder", "GraphsAsQuadsDecoder")
      ),
      (
        Seq(
          LogicalStreamType.GRAPHS,
          LogicalStreamType.SUBJECT_GRAPHS,
        ),
        Seq("TriplesDecoder")
      ),
      (
        Seq(
          LogicalStreamType.DATASETS,
          LogicalStreamType.NAMED_GRAPHS,
          LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS,
        ),
        Seq("QuadsDecoder", "GraphsDecoder", "GraphsAsQuadsDecoder")
      ),
      (
        Seq(
          LogicalStreamType.NAMED_GRAPHS,
          LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS,
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
          defaultOptions.clone.setLogicalType(lst)
        )

        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(pst)
            .setLogicalType(LogicalStreamType.UNSPECIFIED)
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
            defaultOptions.clone.setLogicalType(lst)
          )

          val data = wrapEncoded(Seq(
            JellyOptions.SMALL_GENERALIZED.clone
              .setPhysicalType(pst)
              .setLogicalType(lstOfStream)
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
        val decoder = decoderF(collector, defaultOptions)

        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(pst)
            .setLogicalType(lstOfStream)
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
        defaultOptions.clone
          .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
      )

      Triples1
        .encoded(
          JellyOptions.SMALL_GENERALIZED
            .clone
            .setPhysicalType(PhysicalStreamType.TRIPLES)
            .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Triples1.mrl)
    }

    "decode triple statements with unset expected logical stream type" in {
      val collector = ProtoCollector()
      val decoder = MockConverterFactory.triplesDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      Triples1
        .encoded(
          JellyOptions.SMALL_GENERALIZED
            .clone
            .setPhysicalType(PhysicalStreamType.TRIPLES)
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Triples1.mrl)
    }

    "decode triple statements with namespace declarations" in {
      val collector = ProtoCollector()
      val decoder = MockConverterFactory.triplesDecoder(
        collector,
        defaultOptions.clone
          .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
      )

      Triples2NsDecl
        .encoded(
          JellyOptions.SMALL_GENERALIZED
            .clone
            .setPhysicalType(PhysicalStreamType.TRIPLES)
            .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Triples2NsDecl.mrl.filter(_.isInstanceOf[Triple]).asInstanceOf[Seq[Triple]])
      collector.namespaces.toSeq should be (Seq(
        ("test", Iri("https://test.org/test/")),
        ("ns2", Iri("https://test.org/ns2/")),
      ))
    }

    "ignore namespace declarations by default" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(
        collector,
        defaultOptions.clone
          .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
      )

      Triples2NsDecl
        .encoded(
          JellyOptions.SMALL_GENERALIZED
            .clone
            .setPhysicalType(PhysicalStreamType.TRIPLES)
            .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Triples2NsDecl.mrl.filter(_.isInstanceOf[Triple]).asInstanceOf[Seq[Triple]])
    }

    "throw exception on unset logical stream type" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(
        collector,
        defaultOptions.clone
          .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
      )

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED
          .clone
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setLogicalType(LogicalStreamType.UNSPECIFIED)
      ))
      
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data.head)
      }
      
      error.getMessage should include ("Expected logical stream type")
    }

    "throw exception on a quad in a TRIPLES stream" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      
      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES),
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

      val decoder = MockConverterFactory.triplesDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES),
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setRdfStar(true),
      ))

      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))
      decoder.getStreamOptions should not be null
      decoder.getStreamOptions.getRdfStar should be (false)
    }

    "throw exception on unset term without preceding value" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      
      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES),
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

      val decoder = MockConverterFactory.triplesDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES),
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

      val decoder = MockConverterFactory.triplesDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(rdfStreamRow())
      }

      error.getMessage should include ("Row kind is not set")
    }

    "interpret unset literal kind as a simple literal" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES),
        rdfTriple("1", "2", rdfLiteral("test")),
      ))

      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))

      val r = collector.statements.head.asInstanceOf[Triple]
      r.o should be (a[SimpleLiteral])
    }

    // The tests for this logic are in internal.NameDecoderSpec
    // Here we are just testing if the exceptions are rethrown correctly.
    "throw exception on an invalid IRI term" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.triplesDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES),
        rdfPrefixEntry(0, "a"),
        rdfNameEntry(0, "b"),
        rdfTriple("1", "2", rdfIri(2, 2)),
      ))

      decoder.ingestRow(data.head)

      decoder.ingestRow(data(1))
      decoder.ingestRow(data(2))
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(3))
      }

      error.getMessage should include ("Error while decoding term")
      error.getCause shouldBe a [NullPointerException]
    }
  }

  "a QuadsDecoder" should {
    "decode quad statements" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.quadsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      Quads1
        .encoded(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(PhysicalStreamType.QUADS),
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Quads1.mrl)
    }

    "decode quad statements (repeated default graph)" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.quadsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      Quads2RepeatDefault
        .encoded(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(PhysicalStreamType.QUADS),
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Quads2RepeatDefault.mrl)
    }

    "throw exception on a triple in a QUADS stream" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.quadsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.QUADS),
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

      val decoder = MockConverterFactory.quadsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.QUADS),
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

      val decoder = MockConverterFactory.quadsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.QUADS),
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

      val decoder = MockConverterFactory.graphsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      Graphs1
        .encoded(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(PhysicalStreamType.GRAPHS),
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

      val decoder = MockConverterFactory.graphsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.GRAPHS),
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

      val decoder = MockConverterFactory.graphsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.GRAPHS),
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

      val decoder = MockConverterFactory.graphsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.GRAPHS),
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

      val decoder = MockConverterFactory.graphsAsQuadsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      Graphs1
        .encoded(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(PhysicalStreamType.GRAPHS),
        )
        .foreach(row => decoder.ingestRow(row))

      assertDecoded(collector.statements.toSeq, Graphs1.mrlQuads)
    }

    "throw exception on a triple before a graph start" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.graphsAsQuadsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.GRAPHS),
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
    "throw exception on an invalid IRI term" in {
      val collector = ProtoCollector()
      val decoder = MockConverterFactory.graphsAsQuadsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.GRAPHS),
        rdfPrefixEntry(0, "a"),
        rdfNameEntry(0, "b"),
        rdfGraphStart(rdfDefaultGraph()),
        rdfTriple("1", "2", rdfIri(2, 2)),
      ))

      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))
      decoder.ingestRow(data(2))
      decoder.ingestRow(data(3))
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(4))
      }

      error.getMessage should include("Error while decoding term")
      error.getCause shouldBe a[NullPointerException]
    }
  }

  "an AnyStatementDecoder" should {
    val cases = Seq(
      (Triples1, PhysicalStreamType.TRIPLES, "triples", Triples1.mrl),
      (Quads1, PhysicalStreamType.QUADS, "quads", Quads1.mrl),
      (Graphs1, PhysicalStreamType.GRAPHS, "graphs", Graphs1.mrlQuads),
    )

    for ((testCase, streamType, streamName, expected) <- cases) do
      s"decode $streamName" in {
        val collector = ProtoCollector()

        val opts = JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(streamType)
          .setVersion(JellyConstants.PROTO_VERSION)

        val decoder = MockConverterFactory.anyStatementDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

        testCase
          .encoded(opts)
          .foreach(row => decoder.ingestRow(row))

        assertDecoded(collector.statements.toSeq, expected)
        decoder.getStreamOptions should be (opts)
      }

    "should return None when retrieving stream options on an empty stream" in {
      val collector = ProtoCollector()
      val decoder = MockConverterFactory.anyStatementDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      decoder.getStreamOptions should be (null)
    }

    "should throw when decoding a row without preceding options" in {
      val collector = ProtoCollector()

      val decoder = MockConverterFactory.anyStatementDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

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

      val decoder = MockConverterFactory.anyStatementDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      val data = wrapEncoded(Seq(
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES),
        JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES),
        rdfTriple("1", "2", "3"),
      ))

      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))
      decoder.ingestRow(data(2))

      collector.statements.head should be (a[Triple])
    }
  }

  private val streamTypeCases = Seq(
    (
      (o: Option[RdfStreamOptions]) => MockConverterFactory.triplesDecoder(
        ProtoCollector(),
        o.orElse(Some(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)).get
      ),
      "Triples",
      PhysicalStreamType.TRIPLES,
      PhysicalStreamType.QUADS
    ),
    (
      (o: Option[RdfStreamOptions]) => MockConverterFactory.quadsDecoder(
        ProtoCollector(),
        o.orElse(Some(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)).get
      ),
      "Quads",
      PhysicalStreamType.QUADS,
      PhysicalStreamType.GRAPHS
    ),
    (
      (o: Option[RdfStreamOptions]) => MockConverterFactory.graphsDecoder(
        ProtoCollector(),
        o.orElse(Some(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)).get
      ),
      "Graphs",
      PhysicalStreamType.GRAPHS,
      PhysicalStreamType.QUADS
    ),
    (
      (o: Option[RdfStreamOptions]) => MockConverterFactory.graphsAsQuadsDecoder(
        ProtoCollector(),
        o.orElse(Some(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)).get
      ),
      "GraphsAsQuads",
      PhysicalStreamType.GRAPHS,
      PhysicalStreamType.TRIPLES
    ),
    (
      (o: Option[RdfStreamOptions]) => MockConverterFactory.anyStatementDecoder(
        ProtoCollector(),
        o.orElse(Some(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)).get
      ),
      "AnyStatement",
      PhysicalStreamType.TRIPLES,
      PhysicalStreamType.UNSPECIFIED
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
            .clone
            .setPhysicalType(invalidStreamType)
        ))

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(None).ingestRow(data.head)
        }

        error.getMessage should include ("stream type is not")
      }

      "throw exception on an unsupported proto version" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED
            .clone
            .setPhysicalType(streamType)
            .setVersion(JellyConstants.PROTO_VERSION + 1)
        ))

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(None).ingestRow(data.head)
        }

        error.getMessage should include("Unsupported proto version")
      }

      "throw exception on a proto version higher than marked by the user as supported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(streamType)
            .setVersion(JellyConstants.PROTO_VERSION)
        ))

        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone
          .setVersion(JellyConstants.PROTO_VERSION - 1)

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("Unsupported proto version")
      }

      "throw exception on a stream with generalized statements if marked as unsupported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(streamType)
        ))

        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone
          .setGeneralizedStatements(false)

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("stream uses generalized statements")
      }

      "throw exception on a stream with RDF-star if marked as unsupported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_RDF_STAR.clone
            .setPhysicalType(streamType)
        ))

        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone
          .setRdfStar(false)
        
        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("stream uses RDF-star")
      }

      "throw exception on a stream with a name table size larger than supported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(streamType)
            .setMaxNameTableSize(100)
        ))

        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone
          .setMaxNameTableSize(80)

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("name table size of 100")
        error.getMessage should include("larger than the maximum supported size of 80")
      }

      "throw exception on a stream with a prefix table size larger than supported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(streamType)
            .setMaxPrefixTableSize(100)
        ))
        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone
          .setMaxPrefixTableSize(80)

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("prefix table size of 100")
        error.getMessage should include("larger than the maximum supported size of 80")
      }

      "throw exception on a stream with a datatype table size larger than supported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(streamType)
            .setMaxDatatypeTableSize(100)
        ))

        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone
          .setMaxDatatypeTableSize(80)

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(Some(opt)).ingestRow(data.head)
        }

        error.getMessage should include("datatype table size of 100")
        error.getMessage should include("larger than the maximum supported size of 80")
      }

      "throw exception on a stream with a name table size smaller than supported" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(streamType)
            .setMaxNameTableSize(2) // 8 is the minimum
        ))

        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory(None).ingestRow(data.head)
        }

        error.getMessage should include("name table size of 2")
        error.getMessage should include("smaller than the minimum supported size of 8")
      }

      "accept a datatype table size = 0" in {
        val data = wrapEncoded(Seq(
          JellyOptions.SMALL_GENERALIZED.clone
            .setPhysicalType(streamType)
            .setMaxDatatypeTableSize(0)
        ))

        decoderFactory(None).ingestRow(data.head) // should not throw
      }
    }
