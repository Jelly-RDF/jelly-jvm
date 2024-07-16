package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.*

object ProtoTestCases:
  type RowValue = RdfStreamOptions | RdfDatatypeEntry | RdfPrefixEntry | RdfNameEntry | RdfTriple | RdfQuad |
    RdfGraphStart | RdfGraphEnd

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
        RdfTerm.Iri(RdfIri(1, 0)),
        RdfTerm.Iri(RdfIri(0, 0)),
        RdfTerm.Iri(RdfIri(2, 0)),
      ),
      RdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      RdfTriple(
        RdfTerm.Empty,
        RdfTerm.Empty,
        RdfTerm.Literal(RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1))),
      ),
      RdfPrefixEntry(0, ""),
      RdfNameEntry(0, "b"),
      RdfNameEntry(0, "c"),
      RdfTriple(
        RdfTerm.Empty,
        RdfTerm.Empty,
        RdfTerm.TripleTerm(RdfTriple(
          RdfTerm.Iri(RdfIri(1, 1)),
          RdfTerm.Iri(RdfIri(3, 4)),
          RdfTerm.Iri(RdfIri(0, 0)),
        ))
      ),
      RdfTriple(
        RdfTerm.Iri(RdfIri(1, 2)),
        RdfTerm.Iri(RdfIri(0, 1)),
        RdfTerm.Empty,
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
        RdfTerm.Iri(RdfIri(1, 0)),
        RdfTerm.Iri(RdfIri(0, 0)),
        RdfTerm.Literal(RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb"))),
        RdfTerm.Iri(RdfIri(2, 0)),
      ),
      RdfQuad(
        RdfTerm.Empty,
        RdfTerm.Bnode("blank"),
        RdfTerm.Literal(RdfLiteral(
          "test", RdfLiteral.LiteralKind.Empty
        )),
        RdfTerm.Empty,
      ),
      RdfQuad(
        RdfTerm.Empty,
        RdfTerm.Empty,
        RdfTerm.Empty,
        RdfTerm.Bnode("blank"),
      ),
      RdfQuad(
        RdfTerm.Empty,
        RdfTerm.Empty,
        RdfTerm.Empty,
        RdfTerm.Literal(RdfLiteral(
          "test", RdfLiteral.LiteralKind.Empty
        )),
      ),
    ))

  object Quads2RepeatDefault extends TestCase[Quad]:
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
        RdfTerm.Iri(RdfIri(1, 0)),
        RdfTerm.Iri(RdfIri(0, 0)),
        RdfTerm.Literal(RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb"))),
        RdfTerm.DefaultGraph(RdfDefaultGraph()),
      ),
      RdfQuad(
        RdfTerm.Empty,
        RdfTerm.Bnode("blank"),
        RdfTerm.Literal(RdfLiteral(
          "test", RdfLiteral.LiteralKind.Empty
        )),
        RdfTerm.Empty,
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
      RdfGraphStart(
        RdfTerm.DefaultGraph(RdfDefaultGraph())
      ),
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "subject"),
      RdfNameEntry(0, "predicate"),
      RdfPrefixEntry(0, "https://test.org/ns2/"),
      RdfNameEntry(0, "object"),
      RdfTriple(
        RdfTerm.Iri(RdfIri(1, 0)),
        RdfTerm.Iri(RdfIri(0, 0)),
        RdfTerm.Iri(RdfIri(2, 0)),
      ),
      RdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      RdfTriple(
        RdfTerm.Empty,
        RdfTerm.Empty,
        RdfTerm.Literal(RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1))),
      ),
      RdfGraphEnd(),
      RdfPrefixEntry(0, "https://test.org/ns3/"),
      RdfNameEntry(0, "graph"),
      RdfGraphStart(
        RdfTerm.Iri(RdfIri(3, 0))
      ),
      RdfTriple(
        RdfTerm.Empty,
        RdfTerm.Empty,
        RdfTerm.Iri(RdfIri(2, 3)),
      ),
      RdfGraphEnd(),
    ))
