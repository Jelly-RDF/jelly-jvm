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

  // Test body
  "a TriplesDecoder" should {
    "decode triple statements" in {
      val decoder = MockConverterFactory.triplesDecoder
      val decoded = Triples1
        .encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES))
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Triples1.mrl)
    }

    "decode triple statements (norepeat)" in {
      val decoder = MockConverterFactory.triplesDecoder
      val decoded = Triples2NoRepeat
        .encoded(JellyOptions.smallGeneralized
          .withStreamType(RdfStreamType.TRIPLES)
          .withUseRepeat(false)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Triples2NoRepeat.mrl)
    }

    "throw exception on a quad in a TRIPLES stream" in {
      val decoder = MockConverterFactory.triplesDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES),
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
      val decoder = MockConverterFactory.triplesDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES),
        JellyOptions.smallGeneralized
          .withStreamType(RdfStreamType.TRIPLES)
          .withUseRepeat(false),
      ))

      decoder.ingestRow(data.head)
      decoder.ingestRow(data(1))
      decoder.getStreamOpt.isDefined should be (true)
      decoder.getStreamOpt.get.useRepeat should be (true)
    }

    "throw exception on RdfRepeat without preceding value" in {
      val decoder = MockConverterFactory.triplesDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES),
        RdfTriple(TERM_REPEAT, TERM_REPEAT, TERM_REPEAT),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("RdfRepeat without previous term")
    }

    "throw exception on RdfRepeat in a quoted triple" in {
      val decoder = MockConverterFactory.triplesDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES),
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
      val decoder = MockConverterFactory.triplesDecoder
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(RdfStreamRow(RdfStreamRow.Row.Empty))
      }
      error.getMessage should include ("Row kind is not set")
    }

    "throw exception on unset term kind" in {
      val decoder = MockConverterFactory.triplesDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES),
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

    "throw exception on unset literal kind" in {
      val decoder = MockConverterFactory.triplesDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES),
        RdfTriple(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.Literal(RdfLiteral("test", RdfLiteral.LiteralKind.Empty))),
        ),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Literal kind is not set")
    }
  }

  "a QuadsDecoder" should {
    "decode quad statements" in {
      val decoder = MockConverterFactory.quadsDecoder
      val decoded = Quads1
        .encoded(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.QUADS)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Quads1.mrl)
    }

    "decode quad statements (norepeat)" in {
      val decoder = MockConverterFactory.quadsDecoder
      val decoded = Quads2NoRepeat
        .encoded(
          JellyOptions.smallGeneralized
            .withStreamType(RdfStreamType.QUADS)
            .withUseRepeat(false)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Quads2NoRepeat.mrl)
    }

    "decode quad statements (repeated default graph)" in {
      val decoder = MockConverterFactory.quadsDecoder
      val decoded = Quads3RepeatDefault
        .encoded(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.QUADS)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Quads3RepeatDefault.mrl)
    }

    "throw exception on a triple in a QUADS stream" in {
      val decoder = MockConverterFactory.quadsDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.QUADS),
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
      val decoder = MockConverterFactory.quadsDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.QUADS),
        RdfGraphStart(RdfGraph(RdfGraph.Graph.DefaultGraph(RdfDefaultGraph()))),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Unexpected start of graph in stream")
    }

    "throw exception on a graph end in a QUADS stream" in {
      val decoder = MockConverterFactory.quadsDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.QUADS),
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
      val decoder = MockConverterFactory.graphsDecoder
      val decoded = Graphs1
        .encoded(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.GRAPHS)
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
      val decoder = MockConverterFactory.graphsDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.GRAPHS),
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
      val decoder = MockConverterFactory.graphsDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.GRAPHS),
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
      val decoder = MockConverterFactory.graphsDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.GRAPHS),
        RdfGraphStart(RdfGraph(RdfGraph.Graph.Repeat(RdfRepeat()))),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Invalid usage of graph term repeat in a GRAPHS stream")
    }

    "throw exception on unset graph term type" in {
      val decoder = MockConverterFactory.graphsDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.GRAPHS),
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
      val decoder = MockConverterFactory.graphsAsQuadsDecoder
      val decoded = Graphs1
        .encoded(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.GRAPHS)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Graphs1.mrlQuads)
    }

    "throw exception on a triple before a graph start" in {
      val decoder = MockConverterFactory.graphsAsQuadsDecoder
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.GRAPHS),
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
      (Triples1, RdfStreamType.TRIPLES, "triples", Triples1.mrl),
      (Quads1, RdfStreamType.QUADS, "quads", Quads1.mrl),
      (Graphs1, RdfStreamType.GRAPHS, "graphs", Graphs1.mrlQuads),
    )

    for ((testCase, streamType, streamName, expected) <- cases) do
      s"decode $streamName" in {
        val opts = JellyOptions.smallGeneralized.withStreamType(streamType)
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
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES),
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("Stream options are already set")
    }
  }

  val streamTypeCases = Seq(
    (MockConverterFactory.triplesDecoder, "Triples", RdfStreamType.QUADS),
    (MockConverterFactory.quadsDecoder, "Quads", RdfStreamType.TRIPLES),
    (MockConverterFactory.graphsDecoder, "Graphs", RdfStreamType.QUADS),
    (MockConverterFactory.graphsAsQuadsDecoder, "GraphsAsQuads", RdfStreamType.TRIPLES),
    (MockConverterFactory.anyStatementDecoder, "AnyStatement", RdfStreamType.UNSPECIFIED),
  )

  for (decoder, decName, streamType) <- streamTypeCases do
    s"a ${decName}Decoder" should {
      "throw exception on an empty stream type" in {
        val data = wrapEncodedFull(Seq(JellyOptions.smallGeneralized))
        val error = intercept[RdfProtoDeserializationError] {
          decoder.ingestRow(data.head)
        }
        error.getMessage should include ("stream type is not")
      }

      "throw exception on an invalid stream type" in {
        val data = wrapEncodedFull(Seq(
          JellyOptions.smallGeneralized.withStreamType(streamType),
        ))
        val error = intercept[RdfProtoDeserializationError] {
          decoder.ingestRow(data.head)
        }
        error.getMessage should include ("stream type is not")
      }
    }
