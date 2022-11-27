package pl.ostrzyciel.jelly.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.ostrzyciel.jelly.core.helpers.Mrl.*
import pl.ostrzyciel.jelly.core.proto.*

import scala.collection.immutable.{AbstractSeq, LinearSeq}

class ProtoEncoderSpec extends AnyWordSpec, Matchers:
  type RowValue = RdfStreamOptions | RdfDatatypeEntry | RdfPrefixEntry | RdfNameEntry | RdfTriple | RdfQuad

  // Mock implementation of ProtoEncoder
  class MockProtoEncoder(override val options: JellyOptions)
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


  def assertEncoded(observed: Seq[RdfStreamRow], expected: Seq[RowValue]): Unit =
    val expectedRows: Seq[RdfStreamRow.Row] = expected map {
      case v: RdfStreamOptions => RdfStreamRow.Row.Options(v)
      case v: RdfDatatypeEntry => RdfStreamRow.Row.Datatype(v)
      case v: RdfPrefixEntry => RdfStreamRow.Row.Prefix(v)
      case v: RdfNameEntry => RdfStreamRow.Row.Name(v)
      case v: RdfTriple => RdfStreamRow.Row.Triple(v)
      case v: RdfQuad => RdfStreamRow.Row.Quad(v)
    }

    observed.size should be (expected.size)
    var ix = 0
    for obsRow <- observed do
      withClue(s"Row $ix ${obsRow.row}") {
        obsRow.row should be (expectedRows(ix))
      }
      ix += 1

  // Test body
  "a ProtoEncoder" when {
    "using default settings" should {
      lazy val encoder = MockProtoEncoder(JellyOptions())

      "encode triple statements" in {
        val encoded = encoder.addTripleStatement(Triple(
          Iri("https://test.org/test/subject"),
          Iri("https://test.org/test/predicate"),
          Iri("https://test.org/ns2/object"),
        )).toSeq ++ encoder.addTripleStatement(Triple(
          Iri("https://test.org/test/subject"),
          Iri("https://test.org/test/predicate"),
          DtLiteral("123", Datatype("https://test.org/xsd/integer")),
        )).toSeq

        assertEncoded(encoded, Seq(
          encoder.options.toProto,
          RdfPrefixEntry(1, "https://test.org/test/"),
          RdfNameEntry(1, "subject"),
          RdfNameEntry(2, "predicate"),
          RdfPrefixEntry(2, "https://test.org/ns2/"),
          RdfNameEntry(3, "object"),
          RdfTriple(
            RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 1))),
            RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 2))),
            RdfTerm(RdfTerm.Term.Iri(RdfIri(2, 3))),
          ),
          RdfDatatypeEntry(1, "https://test.org/xsd/integer"),
          RdfTriple(
            RdfTerm(RdfTerm.Term.Repeat(RdfRepeat())),
            RdfTerm(RdfTerm.Term.Repeat(RdfRepeat())),
            RdfTerm(RdfTerm.Term.Literal(RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1)))),
          ),
        ))
      }

      "encode quad statements" in {

      }
    }
  }
