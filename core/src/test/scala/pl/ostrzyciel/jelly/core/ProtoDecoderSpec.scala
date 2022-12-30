package pl.ostrzyciel.jelly.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.ostrzyciel.jelly.core.helpers.Mrl.*
import pl.ostrzyciel.jelly.core.proto.*

class ProtoDecoderSpec extends AnyWordSpec, Matchers:
  import ProtoDecoderImpl.*
  import ProtoTestCases.*

  // Mock implementation of ProtoDecoder
  class MockProtoDecoderConverter extends ProtoDecoderConverter[Node, Datatype, Triple, Quad]:
    def makeSimpleLiteral(lex: String) = SimpleLiteral(lex)
    def makeLangLiteral(lex: String, lang: String) = LangLiteral(lex, lang)
    def makeDtLiteral(lex: String, dt: Datatype) = DtLiteral(lex, dt)
    def makeDatatype(dt: String) = Datatype(dt)
    def makeBlankNode(label: String) = BlankNode(label)
    def makeIriNode(iri: String) = Iri(iri)
    def makeTripleNode(s: Node, p: Node, o: Node) = TripleNode(Triple(s, p, o))
    def makeDefaultGraphNode() = null
    def makeTriple(s: Node, p: Node, o: Node) = Triple(s, p, o)
    def makeQuad(s: Node, p: Node, o: Node, g: Node) = Quad(s, p, o, g)

  // Helper method
  def assertDecoded(observed: Seq[Statement], expected: Seq[Statement]): Unit =
    for ix <- 0 until observed.size.max(expected.size) do
      val obsRow = observed.applyOrElse(ix, null)
      withClue(s"Row $ix:") {
        obsRow should be (expected.applyOrElse(ix, null))
      }

  // Test body
  "a ProtoDecoder" should {
    "decode triple statements" in {
      val decoder = new TriplesDecoder(new MockProtoDecoderConverter())
      val decoded = Triples1
        .encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES))
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Triples1.mrl)
    }

    "decode triple statements (norepeat)" in {
      val decoder = new TriplesDecoder(new MockProtoDecoderConverter())
      val decoded = Triples2NoRepeat
        .encoded(JellyOptions.smallGeneralized
          .withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES)
          .withUseRepeat(false)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Triples2NoRepeat.mrl)
    }

    // The following cases are for the [[ProtoDecoder]] base class – but tested on the child.
    // The code is the same in quads, triples, or graphs decoders, so this is fine.
    // Code coverage checks out.
    "throw exception on RdfRepeat without preceding value" in {
      val decoder = new TriplesDecoder(new MockProtoDecoderConverter())
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES),
        RdfTriple(TERM_REPEAT, TERM_REPEAT, TERM_REPEAT),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("RdfRepeat without previous term")
    }

    "throw exception on RdfRepeat in a quoted triple" in {
      val decoder = new TriplesDecoder(new MockProtoDecoderConverter())
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES),
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
      val decoder = new TriplesDecoder(new MockProtoDecoderConverter())
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(RdfStreamRow(RdfStreamRow.Row.Empty))
      }
      error.getMessage should include ("Row kind is not set")
    }

    "throw exception on unset term kind" in {
      val decoder = new TriplesDecoder(new MockProtoDecoderConverter())
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES),
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
      val decoder = new TriplesDecoder(new MockProtoDecoderConverter())
      val data = wrapEncodedFull(Seq(
        JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES),
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
      val decoder = new QuadsDecoder(new MockProtoDecoderConverter())
      val decoded = Quads1
        .encoded(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_QUADS)
        )
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Quads1.mrl)
    }
  }

