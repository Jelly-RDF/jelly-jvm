package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.*

object ProtoTestCases:
  def wrapEncoded(rows: Seq[RdfStreamRowValue]): Seq[RdfStreamRowValue] = rows map {
    case v: RdfStreamOptions => v.version match
      // If the version is not set, set it to the current version
      case 0 => v.withVersion(Constants.protoVersion)
      // Otherwise assume we are checking version compatibility
      case _ => v
    case v => v
  }

  def wrapEncodedFull(rows: Seq[RdfStreamRowValue]): Seq[RdfStreamRow] =
    wrapEncoded(rows).map(row => RdfStreamRow(row))

  trait TestCase[TStatement]:
    def mrl: Seq[TStatement]
    def encoded(opt: RdfStreamOptions): Seq[RdfStreamRowValue]
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
        RdfIri(1, 0),
        RdfIri(0, 0),
        RdfIri(2, 0),
      ),
      RdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      RdfTriple(
        null,
        null,
        RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1)),
      ),
      RdfPrefixEntry(0, ""),
      RdfNameEntry(0, "b"),
      RdfNameEntry(0, "c"),
      RdfTriple(
        null,
        null,
        RdfTriple(
          RdfIri(1, 1),
          RdfIri(3, 4),
          RdfIri(0, 0),
        )
      ),
      RdfTriple(
        RdfIri(1, 2),
        RdfIri(0, 1),
        null,
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
        RdfIri(1, 0),
        RdfIri(0, 0),
        RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb")),
        RdfIri(2, 0),
      ),
      RdfQuad(
        null,
        RdfTerm.Bnode("blank"),
        RdfLiteral(
          "test", RdfLiteral.LiteralKind.Empty
        ),
        null,
      ),
      RdfQuad(
        null,
        null,
        null,
        RdfTerm.Bnode("blank"),
      ),
      RdfQuad(
        null,
        null,
        null,
        RdfLiteral(
          "test", RdfLiteral.LiteralKind.Empty
        ),
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
        RdfIri(1, 0),
        RdfIri(0, 0),
        RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb")),
        RdfDefaultGraph(),
      ),
      RdfQuad(
        null,
        RdfTerm.Bnode("blank"),
        RdfLiteral("test", RdfLiteral.LiteralKind.Empty),
        null,
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
        RdfDefaultGraph()
      ),
      RdfPrefixEntry(0, "https://test.org/test/"),
      RdfNameEntry(0, "subject"),
      RdfNameEntry(0, "predicate"),
      RdfPrefixEntry(0, "https://test.org/ns2/"),
      RdfNameEntry(0, "object"),
      RdfTriple(
        RdfIri(1, 0),
        RdfIri(0, 0),
        RdfIri(2, 0),
      ),
      RdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      RdfTriple(
        null,
        null,
        RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1)),
      ),
      RdfGraphEnd(),
      RdfPrefixEntry(0, "https://test.org/ns3/"),
      RdfNameEntry(0, "graph"),
      RdfGraphStart(
        RdfIri(3, 0)
      ),
      RdfTriple(
        null,
        null,
        RdfIri(2, 3),
      ),
      RdfGraphEnd(),
    ))
