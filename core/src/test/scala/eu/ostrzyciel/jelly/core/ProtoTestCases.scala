package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.*

object ProtoTestCases:
  type RowValue = RdfStreamOptions | RdfDatatypeEntry | RdfPrefixEntry | RdfNameEntry | RdfTriple | RdfQuad |
    RdfGraphStart | RdfGraphEnd
  
  val TERM_REPEAT: RdfTerm = RdfTerm(RdfTerm.Term.Repeat(RdfRepeat()))
  val GRAPH_REPEAT: RdfGraph = RdfGraph(RdfGraph.Graph.Repeat(RdfRepeat()))

  def wrapEncoded(rows: Seq[RowValue]): Seq[RdfStreamRow.Row] = rows map {
    case v: RdfStreamOptions => v.version match
      // If the version is not set, set it to the current version
      case 0 => RdfStreamRow.Row.Options(v.withVersion(Constants.protoVersion))
      // Otherwise assume we are checking version compatibility
      case _ => RdfStreamRow.Row.Options(v)
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
    def encodedFull(opt: RdfStreamOptions, groupByN: Int) =
      encoded(opt)
        .map(row => RdfStreamRow(row))
        .grouped(groupByN)
        .map(rows => RdfStreamFrame(rows))
        .toSeq

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
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "subject"),
      RdfNameEntry(0, "predicate"),
      RdfPrefixEntry(0, "https://test.org/ns2/"),
      RdfNameEntry(0, "object"),
      RdfTriple(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 0))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 0))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(2, 0))),
      ),
      RdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      RdfTriple(
        TERM_REPEAT,
        TERM_REPEAT,
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1)))),
      ),
      RdfPrefixEntry(0, ""),
      RdfNameEntry(0, "b"),
      RdfNameEntry(0, "c"),
      RdfTriple(
        TERM_REPEAT,
        TERM_REPEAT,
        RdfTerm(RdfTerm.Term.TripleTerm(RdfTriple(
          RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 1))),
          RdfTerm(RdfTerm.Term.Iri(RdfIri(3, 4))),
          RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 0))),
        )))
      ),
      RdfTriple(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 2))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 1))),
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
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "subject"),
      RdfNameEntry(0, "predicate"),
      RdfPrefixEntry(0, "https://test.org/ns2/"),
      RdfNameEntry(0, "object"),
      RdfTriple(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 0))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 0))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(2, 0))),
      ),
      RdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      RdfTriple(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 1))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 0))),
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
      // Generalized quads
      Quad(
        Iri("https://test.org/test/subject"),
        BlankNode("blank"),
        SimpleLiteral("test"),
        BlankNode("blank"),
      ),
      Quad(
        Iri("https://test.org/test/subject"),
        BlankNode("blank"),
        SimpleLiteral("test"),
        SimpleLiteral("test"),
      ),
    )

    def encoded(opt: RdfStreamOptions) = wrapEncoded(Seq(
      opt,
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "subject"),
      RdfNameEntry(0, "predicate"),
      RdfPrefixEntry(0, "https://test.org/ns3/"),
      RdfNameEntry(0, "graph"),
      RdfQuad(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 0))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 0))),
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb")))),
        RdfGraph(RdfGraph.Graph.Iri(RdfIri(2, 0))),
      ),
      RdfQuad(
        TERM_REPEAT,
        RdfTerm(RdfTerm.Term.Bnode("blank")),
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral(
          "test", RdfLiteral.LiteralKind.Simple(RdfLiteralSimple.defaultInstance)
        ))),
        GRAPH_REPEAT,
      ),
      RdfQuad(
        TERM_REPEAT,
        TERM_REPEAT,
        TERM_REPEAT,
        RdfGraph(RdfGraph.Graph.Bnode("blank")),
      ),
      RdfQuad(
        TERM_REPEAT,
        TERM_REPEAT,
        TERM_REPEAT,
        RdfGraph(RdfGraph.Graph.Literal(RdfLiteral(
          "test", RdfLiteral.LiteralKind.Simple(RdfLiteralSimple.defaultInstance)
        ))),
      ),
    ))

  object Quads2NoRepeat extends TestCase[Quad]:
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
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "subject"),
      RdfNameEntry(0, "predicate"),
      RdfPrefixEntry(0, "https://test.org/ns3/"),
      RdfNameEntry(0, "graph"),
      RdfQuad(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 0))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 0))),
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb")))),
        RdfGraph(RdfGraph.Graph.Iri(RdfIri(2, 0))),
      ),
      RdfQuad(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 1))),
        RdfTerm(RdfTerm.Term.Bnode("blank")),
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral(
          "test", RdfLiteral.LiteralKind.Simple(RdfLiteralSimple.defaultInstance)
        ))),
        RdfGraph(RdfGraph.Graph.Iri(RdfIri(2, 3))),
      ),
    ))

  object Quads3RepeatDefault extends TestCase[Quad]:
    val mrl = Seq(
      Quad(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        LangLiteral("test", "en-gb"),
        null,
      ),
      Quad(
        Iri("https://test.org/test/subject"),
        BlankNode("blank"),
        SimpleLiteral("test"),
        null,
      ),
    )

    def encoded(opt: RdfStreamOptions) = wrapEncoded(Seq(
      opt,
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "subject"),
      RdfNameEntry(0, "predicate"),
      RdfQuad(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 0))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 0))),
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb")))),
        RdfGraph(RdfGraph.Graph.DefaultGraph(RdfDefaultGraph())),
      ),
      RdfQuad(
        TERM_REPEAT,
        RdfTerm(RdfTerm.Term.Bnode("blank")),
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral(
          "test", RdfLiteral.LiteralKind.Simple(RdfLiteralSimple.defaultInstance)
        ))),
        GRAPH_REPEAT,
      ),
    ))

  object Graphs1 extends TestCase[(Node, Iterable[Triple])]:
    val mrl = Seq(
      (
        null,
        Seq(
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
      ),
      (
        Iri("https://test.org/ns3/graph"),
        Seq(
          Triple(
            Iri("https://test.org/test/subject"),
            Iri("https://test.org/test/predicate"),
            Iri("https://test.org/ns2/object"),
          ),
        )
      ),
    )

    val mrlQuads = Seq(
      Quad(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
        null
      ),
      Quad(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        DtLiteral("123", Datatype("https://test.org/xsd/integer")),
        null
      ),
      Quad(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
        Iri("https://test.org/ns3/graph"),
      ),
    )

    def encoded(opt: RdfStreamOptions) = wrapEncoded(Seq(
      opt,
      RdfGraphStart(RdfGraph(RdfGraph.Graph.DefaultGraph(RdfDefaultGraph()))),
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "subject"),
      RdfNameEntry(0, "predicate"),
      RdfPrefixEntry(0, "https://test.org/ns2/"),
      RdfNameEntry(0, "object"),
      RdfTriple(
        RdfTerm(RdfTerm.Term.Iri(RdfIri(1, 0))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(0, 0))),
        RdfTerm(RdfTerm.Term.Iri(RdfIri(2, 0))),
      ),
      RdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      RdfTriple(
        TERM_REPEAT,
        TERM_REPEAT,
        RdfTerm(RdfTerm.Term.Literal(RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1)))),
      ),
      RdfGraphEnd(),
      RdfPrefixEntry(0, "https://test.org/ns3/"),
      RdfNameEntry(0, "graph"),
      RdfGraphStart(RdfGraph(RdfGraph.Graph.Iri(RdfIri(3, 0)))),
      RdfTriple(
        TERM_REPEAT,
        TERM_REPEAT,
        RdfTerm(RdfTerm.Term.Iri(RdfIri(2, 3))),
      ),
      RdfGraphEnd(),
    ))
