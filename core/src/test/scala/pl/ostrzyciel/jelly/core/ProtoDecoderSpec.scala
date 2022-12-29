package pl.ostrzyciel.jelly.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.ostrzyciel.jelly.core.helpers.Mrl.*
import pl.ostrzyciel.jelly.core.proto.*

class ProtoDecoderSpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*

  // Mock implementation of ProtoDecoder
  class MockProtoDecoder extends ProtoDecoder[Node, Datatype, Triple, Quad]:
    protected def makeSimpleLiteral(lex: String) = SimpleLiteral(lex)
    protected def makeLangLiteral(lex: String, lang: String) = LangLiteral(lex, lang)
    protected def makeDtLiteral(lex: String, dt: Datatype) = DtLiteral(lex, dt)
    protected def makeDatatype(dt: String) = Datatype(dt)
    protected def makeBlankNode(label: String) = BlankNode(label)
    protected def makeIriNode(iri: String) = Iri(iri)
    protected def makeTripleNode(s: Node, p: Node, o: Node) = TripleNode(Triple(s, p, o))
    protected def makeTriple(s: Node, p: Node, o: Node) = Triple(s, p, o)
    protected def makeQuad(s: Node, p: Node, o: Node, g: Node) = Quad(s, p, o, g)

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
      val decoder = MockProtoDecoder()
      val decoded = Triples1.encoded(RdfStreamOptions())
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Triples1.mrl)
    }

    "decode triple statements (norepeat)" in {
      val decoder = MockProtoDecoder()
      val decoded = Triples2NoRepeat.encoded(RdfStreamOptions(useRepeat = false))
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Triples2NoRepeat.mrl)
    }

    "decode quad statements" in {
      val decoder = MockProtoDecoder()
      val decoded = Quads1.encoded(RdfStreamOptions())
        .flatMap(row => decoder.ingestRow(RdfStreamRow(row)))
      assertDecoded(decoded, Quads1.mrl)
    }

    "throw exception on RdfRepeat without preceding value" in {
      val decoder = MockProtoDecoder()
      val data = wrapEncodedFull(Seq(
        RdfStreamOptions().toProto,
        RdfTriple(REPEAT, REPEAT, REPEAT),
      ))
      decoder.ingestRow(data.head)
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(data(1))
      }
      error.getMessage should include ("RdfRepeat without previous term")
    }

    "throw exception on RdfRepeat in a quoted triple" in {
      val decoder = MockProtoDecoder()
      val data = wrapEncodedFull(Seq(
        RdfStreamOptions().toProto,
        RdfTriple(
          RdfTerm(RdfTerm.Term.Bnode("1")),
          RdfTerm(RdfTerm.Term.Bnode("2")),
          RdfTerm(RdfTerm.Term.TripleTerm(RdfTriple(
            REPEAT, REPEAT, REPEAT
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
      val decoder = MockProtoDecoder()
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(RdfStreamRow(RdfStreamRow.Row.Empty))
      }
      error.getMessage should include ("Row kind is not set")
    }

    "throw exception on unset term kind" in {
      val decoder = MockProtoDecoder()
      val data = wrapEncodedFull(Seq(
        RdfStreamOptions().toProto,
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
      val decoder = MockProtoDecoder()
      val data = wrapEncodedFull(Seq(
        RdfStreamOptions().toProto,
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

