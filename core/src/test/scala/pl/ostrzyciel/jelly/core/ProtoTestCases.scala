package pl.ostrzyciel.jelly.core

import pl.ostrzyciel.jelly.core.helpers.Mrl.*
import pl.ostrzyciel.jelly.core.proto.*

object ProtoTestCases:
  type RowValue = RdfStreamOptions | RdfDatatypeEntry | RdfPrefixEntry | RdfNameEntry | RdfTriple | RdfQuad |
    RdfGraphStart | RdfGraphEnd
  
  val TERM_REPEAT: RdfTerm = RdfTerm(RdfTerm.Term.Repeat(RdfRepeat()))
  val GRAPH_REPEAT: RdfGraph = RdfGraph(RdfGraph.Graph.Repeat(RdfRepeat()))

  def wrapEncoded(rows: Seq[RowValue]): Seq[RdfStreamRow.Row] = rows map {
    case v: RdfStreamOptions => RdfStreamRow.Row.Options(v)
    case v: RdfDatatypeEntry => RdfStreamRow.Row.Datatype(v)
    case v: RdfPrefixEntry => RdfStreamRow.Row.Prefix(v)
    case v: RdfNameEntry => RdfStreamRow.Row.Name(v)
    case v: RdfTriple => RdfStreamRow.Row.Triple(v)
    case v: RdfQuad => RdfStreamRow.Row.Quad(v)
    case v: RdfGraphStart => RdfStreamRow.Row.GraphStart(v)
    case v: RdfGraphEnd => RdfStreamRow.Row.GraphEnd(v)
  }

  def wrapEncodedFull(rows: Seq[RowValue]): Seq[RdfStreamRow] =
    wrapEncoded(rows).map(row => RdfStreamRow(row))

  trait TestCase[TStatement]:
    def mrl: Seq[TStatement]
    def encoded(opt: RdfStreamOptions): Seq[RdfStreamRow.Row]

  object Triples1 extends TestCase[Triple]:
    val mrl = Seq(
      Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
      ),
      Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        DtLiteral("123", Datatype("https://test.org/xsd/integer")),
      ),
      Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        TripleNode(Triple(Iri("https://test.org/test/subject"), Iri("b"), Iri("c"))),
      ),
      Triple(
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/test/subject"),
        TripleNode(Triple(Iri("https://test.org/test/subject"), Iri("b"), Iri("c"))),
      ),
    )

    def encoded(opt: RdfStreamOptions) = wrapEncoded(Seq(
      opt,
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
        TERM_REPEAT,
        TERM_REPEAT,
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1)))),
      ),
      RdfNameEntry(4, "b"),
      RdfNameEntry(5, "c"),
      RdfTriple(
        TERM_REPEAT,
        TERM_REPEAT,
        RdfTerm(RdfTerm.Term.TripleTerm(RdfTriple(
          RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 1))),
          RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 4))),
          RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 5))),
        )))
      ),
      RdfTriple(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 2))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 1))),
        TERM_REPEAT,
      ),
    ))

  object Triples2NoRepeat extends TestCase[Triple]:
    val mrl = Seq(
      Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
      ),
      Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        DtLiteral("123", Datatype("https://test.org/xsd/integer")),
      ),
    )

    def encoded(opt: RdfStreamOptions) = wrapEncoded(Seq(
      opt,
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
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 1))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 2))),
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1)))),
      ),
    ))

  object Quads1 extends TestCase[Quad]:
    val mrl = Seq(
      Quad(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        LangLiteral("test", "en-gb"),
        Iri("https://test.org/ns3/graph"),
      ),
      Quad(
        Iri("https://test.org/test/subject"),
        BlankNode("blank"),
        SimpleLiteral("test"),
        Iri("https://test.org/ns3/graph"),
      ),
    )

    def encoded(opt: RdfStreamOptions) = wrapEncoded(Seq(
      opt,
      RdfPrefixEntry(1, "https://test.org/test/"),
      RdfNameEntry(1, "subject"),
      RdfNameEntry(2, "predicate"),
      RdfPrefixEntry(2, "https://test.org/ns3/"),
      RdfNameEntry(3, "graph"),
      RdfQuad(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 1))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 2))),
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb")))),
        RdfGraph(RdfGraph.Graph.Iri(RdfIri(2, 3))),
      ),
      RdfQuad(
        TERM_REPEAT,
        RdfTerm(RdfTerm.Term.Bnode("blank")),
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral("test", RdfLiteral.LiteralKind.Simple(true)))),
        GRAPH_REPEAT,
      ),
    ))
