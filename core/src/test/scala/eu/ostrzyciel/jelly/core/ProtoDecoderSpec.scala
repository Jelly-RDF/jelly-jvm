package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProtoDecoderSpec extends AnyWordSpec, Matchers:
  import ProtoDecoderImpl.*
  import ProtoTestCases.*

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
        val decoder = decoderF(Some(lst))
        val data = wrapEncodedFull(Seq(
          JellyOptions.smallGeneralized
            .withPhysicalType(pst)
            .withLogicalType(LogicalStreamType.UNSPECIFIED)
        ))
        val error = intercept[RdfProtoDeserializationError] {
          decoder.ingestRow(data.head)
        }
        error.getMessage should include("Expected logical stream type")
      }

      for lstOfStream <- logicalStreamTypeSet do
        f"accept stream with logical type $lstOfStream when expecting $lst, with $decoderName" in {
          val decoder = decoderF(Some(lst))
          val data = wrapEncodedFull(Seq(
            JellyOptions.smallGeneralized
              .withPhysicalType(pst)
              .withLogicalType(lstOfStream)
          ))
          decoder.ingestRow(data.head)
          decoder.getStreamOpt.get.logicalType should be (lstOfStream)
        }

    for
      (pst, decs) <- decoderFactories.groupBy(_._2._2)
      (decoderName, (decoderF, _)) <- decs
      (lstSet, _) <- logicalStreamTypeSets.take(4).filterNot(x => x._2.exists(y => decs.exists(z => z._1 == y)))
      lstOfStream <- lstSet
    do
      f"throw exception that a stream with logical type $lstOfStream is incompatible with $pst, with $decoderName" in {
        val decoder = decoderF(None)
        val data = wrapEncodedFull(Seq(
          JellyOptions.smallGeneralized
            .withPhysicalType(pst)
            .withLogicalType(lstOfStream)
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
      val decoder = MockConverterFactory.triplesDecoder(Some(LogicalStreamType.FLAT_TRIPLES))
      val decoded = Triples1
        .encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Triples1.mrl)
    }

    "decode triple statements with unset expected logical stream type" in {
      val decoder = MockConverterFactory.triplesDecoder(None)
      val decoded = Triples1
        .encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Triples1.mrl)
    }

    "throw exception on unset logical stream type" in {
      val decoder = MockConverterFactory.triplesDecoder(Some(LogicalStreamType.FLAT_TRIPLES))
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.UNSPECIFIED)
      ))
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data.head)
      }
      error.getMessage should include ("Expected logical stream type")
    }

    "decode triple statements (norepeat)" in {
      val decoder = MockConverterFactory.triplesDecoder(Some(LogicalStreamType.FLAT_TRIPLES))
      val decoded = Triples2NoRepeat
        .encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .withUseRepeat(false)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Triples2NoRepeat.mrl)
    }

    "throw exception on a quad in a TRIPLES stream" in {
      val decoder = MockConverterFactory.triplesDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
        RdfQuad(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.Bnode("3")),
          RdfGraph(RdfGraph.Graph.Bnode("4")),
        ),
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
      val decoder = MockConverterFactory.triplesDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
        JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withUseRepeat(false),
      ))

      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))
      decoder.getStreamOpt.isDefined should be (true)
      decoder.getStreamOpt.get.useRepeat should be (true)
    }

    "throw exception on RdfRepeat without preceding value" in {
      val decoder = MockConverterFactory.triplesDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
        RdfTriple(TERM_REPEAT, TERM_REPEAT, TERM_REPEAT),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("RdfRepeat without previous term")
    }

    "throw exception on RdfRepeat in a quoted triple" in {
      val decoder = MockConverterFactory.triplesDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
        RdfTriple(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.TripleTerm(RdfTriple(
            TERM_REPEAT, TERM_REPEAT, TERM_REPEAT
          ))),
        )
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("RdfRepeat used inside a quoted triple")
    }

    "throw exception on unset row kind" in {
      val decoder = MockConverterFactory.triplesDecoder(None)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(RdfStreamRow(RdfStreamRow.Row.Empty))
      }
      error.getMessage should include ("Row kind is not set")
    }

    "throw exception on unset term kind" in {
      val decoder = MockConverterFactory.triplesDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
        RdfTriple(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.Empty)
        ),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Term kind is not set")
    }

    "interpret unset literal kind as a simple literal" in {
      val decoder = MockConverterFactory.triplesDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
        RdfTriple(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.Literal(RdfLiteral("test", RdfLiteral.LiteralKind.Empty))),
        ),
      ))
      decoder.ingestRow(data.head)
      val r = decoder.ingestRow(data(1))
      r.get.o should be (a[SimpleLiteral])
    }
  }

  "a QuadsDecoder" should {
    "decode quad statements" in {
      val decoder = MockConverterFactory.quadsDecoder(None)
      val decoded = Quads1
        .encoded(
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Quads1.mrl)
    }

    "decode quad statements (norepeat)" in {
      val decoder = MockConverterFactory.quadsDecoder(None)
      val decoded = Quads2NoRepeat
        .encoded(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.QUADS)
            .withUseRepeat(false)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Quads2NoRepeat.mrl)
    }

    "decode quad statements (repeated default graph)" in {
      val decoder = MockConverterFactory.quadsDecoder(None)
      val decoded = Quads3RepeatDefault
        .encoded(
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Quads3RepeatDefault.mrl)
    }

    "throw exception on a triple in a QUADS stream" in {
      val decoder = MockConverterFactory.quadsDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS),
        RdfTriple(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.Bnode("3")),
        ),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Unexpected triple row in stream")
    }

    "throw exception on a graph start in a QUADS stream" in {
      val decoder = MockConverterFactory.quadsDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS),
        RdfGraphStart(RdfGraph(RdfGraph.Graph.DefaultGraph(RdfDefaultGraph()))),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Unexpected start of graph in stream")
    }

    "throw exception on a graph end in a QUADS stream" in {
      val decoder = MockConverterFactory.quadsDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS),
        RdfGraphEnd(),
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
      val decoder = MockConverterFactory.graphsDecoder(None)
      val decoded = Graphs1
        .encoded(
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))

      for ix <- 0 until decoded.size.max(Graphs1.mrl.size) do
        val obsRow = decoded.applyOrElse(ix, null)
        val expRow = Graphs1.mrl.applyOrElse(ix, null)

        withClue(s"Graph row $ix:") {
          obsRow should not be null
          expRow should not be null
          obsRow._1 should be (expRow._1)
          assertDecoded(obsRow._2.toSeq, expRow._2.toSeq)
        }
    }

    "throw exception on a quad in a GRAPHS stream" in {
      val decoder = MockConverterFactory.graphsDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS),
        RdfQuad(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.Bnode("3")),
          RdfGraph(RdfGraph.Graph.Bnode("4")),
        ),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Unexpected quad row in stream")
    }

    "throw exception on a graph end before a graph start" in {
      val decoder = MockConverterFactory.graphsDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS),
        RdfTriple(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.Bnode("3")),
        ),
        RdfGraphEnd(),
      ))
      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(2))
      }
      error.getMessage should include ("End of graph encountered before a start")
    }

    // The following cases are for the [[ProtoDecoder]] base class – but tested on the child.
    "throw exception on graph term repeat in graph name" in {
      val decoder = MockConverterFactory.graphsDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS),
        RdfGraphStart(RdfGraph(RdfGraph.Graph.Repeat(RdfRepeat()))),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Invalid usage of graph term repeat in a GRAPHS stream")
    }

    "throw exception on unset graph term type" in {
      val decoder = MockConverterFactory.graphsDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS),
        RdfGraphStart(),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Graph term kind is not set")
    }
  }

  "a GraphsAsQuadsDecoder" should {
    "decode graphs as quads" in {
      val decoder = MockConverterFactory.graphsAsQuadsDecoder(None)
      val decoded = Graphs1
        .encoded(
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Graphs1.mrlQuads)
    }

    "throw exception on a triple before a graph start" in {
      val decoder = MockConverterFactory.graphsAsQuadsDecoder(None)
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS),
        RdfTriple(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.Bnode("3")),
        ),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Triple in stream without preceding graph start")
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
        val opts = JellyOptions.smallGeneralized
          .withPhysicalType(streamType)
          .withVersion(Constants.protoVersion)
        val decoder = MockConverterFactory.anyStatementDecoder
        val decoded = testCase
          .encoded(opts)
          .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
        assertDecoded(decoded, expected)
        decoder.getStreamOpt should be (Some(opts))
      }

    "should return None when retrieving stream options on an empty stream" in {
      val decoder = MockConverterFactory.anyStatementDecoder
      decoder.getStreamOpt should be (None)
    }

    "should throw when decoding a row without preceding options" in {
      val decoder = MockConverterFactory.anyStatementDecoder
      val data = wrapEncodedFull(Seq(
        RdfTriple(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.Bnode("3")),
        ),
      ))
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data.head)
      }
      error.getMessage should include ("Stream options are not set")
    }

    "should throw when encountering stream options twice" in {
      val decoder = MockConverterFactory.anyStatementDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Stream options are already set")
    }
  }

  val streamTypeCases = Seq(
    (() => MockConverterFactory.triplesDecoder(None), "Triples", PhysicalStreamType.TRIPLES, PhysicalStreamType.QUADS),
    (() => MockConverterFactory.quadsDecoder(None), "Quads", PhysicalStreamType.QUADS, PhysicalStreamType.GRAPHS),
    (() => MockConverterFactory.graphsDecoder(None), "Graphs", PhysicalStreamType.GRAPHS, PhysicalStreamType.QUADS),
    (() => MockConverterFactory.graphsAsQuadsDecoder(None), "GraphsAsQuads", PhysicalStreamType.GRAPHS, PhysicalStreamType.TRIPLES),
    (() => MockConverterFactory.anyStatementDecoder, "AnyStatement", PhysicalStreamType.TRIPLES, PhysicalStreamType.UNSPECIFIED),
  )

  for (decoderFactory, decName, streamType, invalidStreamType) <- streamTypeCases do
    s"a ${decName}Decoder" should {
      "throw exception on an empty stream type" in {
        val data = wrapEncodedFull(Seq(JellyOptions.smallGeneralized))
        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory().ingestRow(data.head)
        }
        error.getMessage should include ("stream type is not")
      }

      "throw exception on an invalid stream type" in {
        val data = wrapEncodedFull(Seq(
          JellyOptions.smallGeneralized.withPhysicalType(invalidStreamType),
        ))
        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory().ingestRow(data.head)
        }
        error.getMessage should include ("stream type is not")
      }

      "throw exception on an unsupported proto version" in {
        val data = wrapEncodedFull(Seq(
          JellyOptions.smallGeneralized
            .withPhysicalType(streamType)
            .withVersion(Constants.protoVersion + 1)
        ))
        val error = intercept[RdfProtoDeserializationError] {
          decoderFactory().ingestRow(data.head)
        }
        error.getMessage should include("Unsupported proto version")
      }
    }
