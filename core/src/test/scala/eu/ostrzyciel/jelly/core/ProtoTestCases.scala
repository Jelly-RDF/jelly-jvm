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
      RdfNameEntry(0, "subject"),
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "predicate"),
      RdfNameEntry(0, "object"),
      RdfPrefixEntry(0, "https://test.org/ns2/"),
      RdfTriple(
        RdfTriple.Subject.SIri(RdfIri(1, 0)),
        RdfTriple.Predicate.PIri(RdfIri(0, 0)),
        RdfTriple.Object.OIri(RdfIri(2, 0)),
      ),
      RdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      RdfTriple(
        RdfTriple.Subject.Empty,
        RdfTriple.Predicate.Empty,
        RdfTriple.Object.OLiteral(RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1))),
      ),
      RdfNameEntry(0, "b"),
      RdfPrefixEntry(0, ""),
      RdfNameEntry(0, "c"),
      RdfTriple(
        RdfTriple.Subject.Empty,
        RdfTriple.Predicate.Empty,
        RdfTriple.Object.OTripleTerm(RdfTriple(
          RdfTriple.Subject.SIri(RdfIri(1, 1)),
          RdfTriple.Predicate.PIri(RdfIri(3, 4)),
          RdfTriple.Object.OIri(RdfIri(0, 0)),
        ))
      ),
      RdfTriple(
        RdfTriple.Subject.SIri(RdfIri(1, 2)),
        RdfTriple.Predicate.PIri(RdfIri(0, 1)),
        RdfTriple.Object.Empty,
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
      RdfNameEntry(0, "subject"),
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "predicate"),
      RdfNameEntry(0, "graph"),
      RdfPrefixEntry(0, "https://test.org/ns3/"),
      RdfQuad(
        RdfQuad.Subject.SIri(RdfIri(1, 0)),
        RdfQuad.Predicate.PIri(RdfIri(0, 0)),
        RdfQuad.Object.OLiteral(RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb"))),
        RdfQuad.Graph.GIri(RdfIri(2, 0)),
      ),
      RdfQuad(
        RdfQuad.Subject.Empty,
        RdfQuad.Predicate.PBnode("blank"),
        RdfQuad.Object.OLiteral(RdfLiteral(
          "test", RdfLiteral.LiteralKind.Empty
        )),
        RdfQuad.Graph.Empty,
      ),
      RdfQuad(
        RdfQuad.Subject.Empty,
        RdfQuad.Predicate.Empty,
        RdfQuad.Object.Empty,
        RdfQuad.Graph.GBnode("blank"),
      ),
      RdfQuad(
        RdfQuad.Subject.Empty,
        RdfQuad.Predicate.Empty,
        RdfQuad.Object.Empty,
        RdfQuad.Graph.GLiteral(RdfLiteral(
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
      RdfNameEntry(0, "subject"),
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "predicate"),
      RdfQuad(
        RdfQuad.Subject.SIri(RdfIri(1, 0)),
        RdfQuad.Predicate.PIri(RdfIri(0, 0)),
        RdfQuad.Object.OLiteral(RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb"))),
        RdfQuad.Graph.GDefaultGraph(RdfDefaultGraph()),
      ),
      RdfQuad(
        RdfQuad.Subject.Empty,
        RdfQuad.Predicate.PBnode("blank"),
        RdfQuad.Object.OLiteral(RdfLiteral(
          "test", RdfLiteral.LiteralKind.Empty
        )),
        RdfQuad.Graph.Empty,
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
        RdfGraphStart.Graph.GDefaultGraph(RdfDefaultGraph())
      ),
      RdfNameEntry(0, "subject"),
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "predicate"),
      RdfNameEntry(0, "object"),
      RdfPrefixEntry(0, "https://test.org/ns2/"),
      RdfTriple(
        RdfTriple.Subject.SIri(RdfIri(1, 0)),
        RdfTriple.Predicate.PIri(RdfIri(0, 0)),
        RdfTriple.Object.OIri(RdfIri(2, 0)),
      ),
      RdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      RdfTriple(
        RdfTriple.Subject.Empty,
        RdfTriple.Predicate.Empty,
        RdfTriple.Object.OLiteral(RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1))),
      ),
      RdfGraphEnd(),
      RdfNameEntry(0, "graph"),
      RdfPrefixEntry(0, "https://test.org/ns3/"),
      RdfGraphStart(
        RdfGraphStart.Graph.GIri(RdfIri(3, 0))
      ),
      RdfTriple(
        RdfTriple.Subject.Empty,
        RdfTriple.Predicate.Empty,
        RdfTriple.Object.OIri(RdfIri(2, 3)),
      ),
      RdfGraphEnd(),
    ))
