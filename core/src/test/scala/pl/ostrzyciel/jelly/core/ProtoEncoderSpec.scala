package pl.ostrzyciel.jelly.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.ostrzyciel.jelly.core.helpers.Mrl.*
import pl.ostrzyciel.jelly.core.proto.*

import scala.collection.immutable.{AbstractSeq, LinearSeq}

class ProtoEncoderSpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*

  // Mock implementation of ProtoEncoder
  class MockProtoEncoder(override val options: RdfStreamOptions)
    extends ProtoEncoder[Node, Triple, Quad, Triple](options):

    protected inline def getTstS(triple: Triple) = triple.s
    protected inline def getTstP(triple: Triple) = triple.p
    protected inline def getTstO(triple: Triple) = triple.o

    protected inline def getQstS(quad: Quad) = quad.s
    protected inline def getQstP(quad: Quad) = quad.p
    protected inline def getQstO(quad: Quad) = quad.o
    protected inline def getQstG(quad: Quad) = quad.g

    protected inline def getQuotedS(triple: Triple) = triple.s
    protected inline def getQuotedP(triple: Triple) = triple.p
    protected inline def getQuotedO(triple: Triple) = triple.o

    protected def nodeToProto(node: Node): RdfTerm = node match
      case Iri(iri) => makeIriNode(iri)
      case SimpleLiteral(lex) => makeSimpleLiteral(lex)
      case LangLiteral(lex, lang) => makeLangLiteral(lex, lang)
      case DtLiteral(lex, dt) => makeDtLiteral(lex, dt.dt)
      case TripleNode(t) => makeTripleNode(t)
      case BlankNode(label) => makeBlankNode(label)

  // Helper method
  def assertEncoded(observed: Seq[RdfStreamRow], expected: Seq[RdfStreamRow.Row]): Unit =
    for ix <- 0 until observed.size.max(expected.size) do
      val obsRow = observed.applyOrElse(ix, null)
      withClue(s"Row $ix:") {
        obsRow.row should be (expected.applyOrElse(ix, null))
      }

  // Test body
  "a ProtoEncoder" should {
    "encode triple statements" in {
      val encoder = MockProtoEncoder(RdfStreamOptions())
      val encoded = Triples1.mrl.flatMap(triple => encoder.addTripleStatement(triple).toSeq)
      assertEncoded(encoded, Triples1.encoded(encoder.options))
    }

    "encode triple statements (norepeat)" in {
      val encoder = MockProtoEncoder(RdfStreamOptions(useRepeat = false))
      val encoded = Triples2NoRepeat.mrl.flatMap(triple => encoder.addTripleStatement(triple).toSeq)
      assertEncoded(encoded, Triples2NoRepeat.encoded(encoder.options))
    }

    "encode quad statements" in {
      val encoder = MockProtoEncoder(RdfStreamOptions())
      val encoded = Quads1.mrl.flatMap(quad => encoder.addQuadStatement(quad).toSeq)
      assertEncoded(encoded, Quads1.encoded(encoder.options))
    }
  }
