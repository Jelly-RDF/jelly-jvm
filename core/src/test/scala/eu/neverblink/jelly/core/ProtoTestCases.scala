package eu.neverblink.jelly.core

import com.google.protobuf.ByteString
import eu.neverblink.jelly.core.{JellyConstants, NamespaceDeclaration}
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.proto.v1.*

object ProtoTestCases:
  def wrapEncoded(rows: Seq[RdfStreamRowValue]): Seq[RdfStreamRow] = rows
    .map {
      case v: RdfStreamOptions => v.getVersion match
        // If the version is not set, set it to the current version
        case 0 => v.clone
          .setVersion(JellyConstants.PROTO_VERSION)
        // Otherwise assume we are checking version compatibility
        case _ => v
      case v => v
    }
    .map(rdfStreamRowFromValue)

  trait TestCase[+TStatement]:
    def mrl: Seq[TStatement]
    def encoded(opt: RdfStreamOptions): Seq[RdfStreamRow]
    def encodedFull(
      opt: RdfStreamOptions, groupByN: Int, metadata: Map[String, ByteString] = Map.empty
    ): Seq[RdfStreamFrame] =
      encoded(opt)
        .grouped(groupByN)
        .map(rows => rdfStreamFrame(rows, metadata = metadata))
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
        TripleNode(Iri("https://test.org/test/subject"), Iri("b"), Iri("c")),
      ),
      Triple(
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/test/subject"),
        TripleNode(Iri("https://test.org/test/subject"), Iri("b"), Iri("c")),
      ),
    )

    def encoded(opt: RdfStreamOptions) = wrapEncoded(Seq(
      opt,
      rdfPrefixEntry(0, "https://test.org/test/"),
      rdfNameEntry(0, "subject"),
      rdfNameEntry(0, "predicate"),
      rdfPrefixEntry(0, "https://test.org/ns2/"),
      rdfNameEntry(0, "object"),
      rdfTriple(
        rdfIri(1, 0),
        rdfIri(0, 0),
        rdfIri(2, 0),
      ),
      rdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      rdfTriple(
        null,
        null,
        rdfLiteral("123", 1),
      ),
      rdfPrefixEntry(0, ""),
      rdfNameEntry(0, "b"),
      rdfNameEntry(0, "c"),
      rdfTriple(
        null,
        null,
        rdfTriple(
          rdfIri(1, 1),
          rdfIri(3, 4),
          rdfIri(0, 0),
        )
      ),
      rdfTriple(
        rdfIri(1, 2),
        rdfIri(0, 1),
        null,
      ),
    ))

  object Triples2NsDecl extends TestCase[Triple | NamespaceDeclaration]:
    val mrl = Seq(
      NamespaceDeclaration("test", "https://test.org/test/"),
      Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
      ),
      NamespaceDeclaration("ns2", "https://test.org/ns2/"),
      Triple(
        Iri("https://test.org/ns2/object"),
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
      ),
    )

    def encoded(opt: RdfStreamOptions) = wrapEncoded(Seq(
      opt,
      rdfPrefixEntry(0, "https://test.org/test/"),
      rdfNameEntry(0, ""),
      rdfNamespaceDeclaration("test", rdfIri(1, 0)),
      rdfNameEntry(0, "subject"),
      rdfNameEntry(0, "predicate"),
      rdfPrefixEntry(0, "https://test.org/ns2/"),
      rdfNameEntry(0, "object"),
      rdfTriple(
        rdfIri(0, 0),
        rdfIri(0, 0),
        rdfIri(2, 0),
      ),
      rdfNamespaceDeclaration("ns2", rdfIri(0, 1)),
      rdfTriple(
        rdfIri(0, 4),
        rdfIri(1, 2),
        rdfIri(0, 0),
      ),
    ))

  object Triples3LongStrings extends TestCase[Triple]:
    val mrl = Seq(
      Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        SimpleLiteral("a" * 1000),
      ),
      Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        SimpleLiteral("b" * 1000),
      ),
    )

    def encoded(opt: RdfStreamOptions) = wrapEncoded(Seq(
      opt,
      rdfPrefixEntry(0, "https://test.org/test/"),
      rdfNameEntry(0, "subject"),
      rdfNameEntry(0, "predicate"),
      rdfTriple(
        rdfIri(1, 0),
        rdfIri(0, 0),
        rdfLiteral("a" * 1000),
      ),
      rdfTriple(
        null,
        null,
        rdfLiteral("b" * 1000),
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
      rdfPrefixEntry(0, "https://test.org/test/"),
      rdfNameEntry(0, "subject"),
      rdfNameEntry(0, "predicate"),
      rdfPrefixEntry(0, "https://test.org/ns3/"),
      rdfNameEntry(0, "graph"),
      rdfQuad(
        rdfIri(1, 0),
        rdfIri(0, 0),
        rdfLiteral("test", "en-gb"),
        rdfIri(2, 0),
      ),
      rdfQuad(
        null,
        "blank",
        rdfLiteral("test"),
        null,
      ),
      rdfQuad(
        null,
        null,
        null,
        "blank",
      ),
      rdfQuad(
        null,
        null,
        null,
        rdfLiteral("test"),
      ),
    ))

  object Quads2RepeatDefault extends TestCase[Quad]:
    val mrl = Seq(
      Quad(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        LangLiteral("test", "en-gb"),
        DefaultGraphNode(),
      ),
      Quad(
        Iri("https://test.org/test/subject"),
        BlankNode("blank"),
        SimpleLiteral("test"),
        DefaultGraphNode(),
      ),
    )

    def encoded(opt: RdfStreamOptions) = wrapEncoded(Seq(
      opt,
      rdfPrefixEntry(0, "https://test.org/test/"),
      rdfNameEntry(0, "subject"),
      rdfNameEntry(0, "predicate"),
      rdfQuad(
        rdfIri(1, 0),
        rdfIri(0, 0),
        rdfLiteral("test", "en-gb"),
        rdfDefaultGraph(),
      ),
      rdfQuad(
        null,
        "blank",
        rdfLiteral("test"),
        null,
      ),
    ))

  object Graphs1 extends TestCase[(Node, Iterable[Triple])]:
    val mrl = Seq(
      (
        DefaultGraphNode(),
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
        DefaultGraphNode()
      ),
      Quad(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        DtLiteral("123", Datatype("https://test.org/xsd/integer")),
        DefaultGraphNode()
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
      rdfGraphStart(
        rdfDefaultGraph()
      ),
      rdfPrefixEntry(0, "https://test.org/test/"),
      rdfNameEntry(0, "subject"),
      rdfNameEntry(0, "predicate"),
      rdfPrefixEntry(0, "https://test.org/ns2/"),
      rdfNameEntry(0, "object"),
      rdfTriple(
        rdfIri(1, 0),
        rdfIri(0, 0),
        rdfIri(2, 0),
      ),
      rdfDatatypeEntry(0, "https://test.org/xsd/integer"),
      rdfTriple(
        null,
        null,
        rdfLiteral("123", 1),
      ),
      rdfGraphEnd(),
      rdfPrefixEntry(0, "https://test.org/ns3/"),
      rdfNameEntry(0, "graph"),
      rdfGraphStart(
        rdfIri(3, 0)
      ),
      rdfTriple(
        null,
        null,
        rdfIri(2, 3),
      ),
      rdfGraphEnd(),
    ))
