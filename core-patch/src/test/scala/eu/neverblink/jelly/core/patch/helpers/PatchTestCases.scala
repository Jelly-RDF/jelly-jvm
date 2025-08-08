package eu.neverblink.jelly.core.patch.helpers

import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.patch.helpers.PatchAdapter.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.patch.helpers.Mpl.*
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.patch.*

import scala.annotation.experimental

@experimental
object PatchTestCases:

  val testCases: Seq[(String, PatchTestCase, PatchStatementType)] = Seq(
    ("a triple patch", Triples1, PatchStatementType.TRIPLES),
    ("a triple patch with namespace declarations", Triples2NsDecl, PatchStatementType.TRIPLES),
    ("a quad patch", Quads1, PatchStatementType.QUADS),
    (
      "a quad patch with generalized namespace declarations",
      Quads2NsDeclOnly,
      PatchStatementType.QUADS,
    ),
    ("nonsensical transaction commands", MalformedTransactions, PatchStatementType.TRIPLES),
  )

  trait PatchTestCase:
    def mrl: Seq[PatchStatement]
    def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow]
    final def encodedFull(opt: RdfPatchOptions, groupByN: Int): Seq[RdfPatchFrame] =
      encoded(opt)
        .grouped(groupByN)
        .map(rows => rdfPatchFrame(rows))
        .toSeq

  object Triples1 extends PatchTestCase:
    val mrl: Seq[PatchStatement] = Seq(
      Header("key", Iri("https://test.org/test/subject")),
      TxStart,
      Add(
        Triple(
          Iri("https://test.org/test/subject"),
          Iri("https://test.org/test/predicate"),
          Iri("https://test.org/ns2/object"),
        ),
      ),
      Add(
        Triple(
          Iri("https://test.org/test/subject"),
          Iri("https://test.org/test/predicate"),
          DtLiteral("123", Datatype("https://test.org/xsd/integer")),
        ),
      ),
      Delete(
        Triple(
          Iri("https://test.org/test/subject"),
          Iri("https://test.org/test/predicate"),
          Iri("https://test.org/ns2/object"),
        ),
      ),
      TxCommit,
      TxStart,
      Add(
        Triple(
          Iri("https://test.org/test/subject"),
          Iri("https://test.org/test/predicate"),
          TripleNode(Iri("https://test.org/test/subject"), Iri("b"), Iri("c")),
        ),
      ),
      TxAbort,
      TxStart,
      Add(
        Triple(
          Iri("https://test.org/test/predicate"),
          Iri("https://test.org/test/subject"),
          TripleNode(Iri("https://test.org/test/subject"), Iri("b"), Iri("c")),
        ),
      ),
      Delete(
        Triple(
          Iri("https://test.org/test/predicate"),
          Iri("https://test.org/test/subject"),
          TripleNode(Iri("https://test.org/test/subject"), Iri("b"), Iri("c")),
        ),
      ),
      TxCommit,
    )

    def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow] = Seq(
      rdfPatchRow(opt),
      rdfPatchRow(rdfPrefixEntry(0, "https://test.org/test/")),
      rdfPatchRow(rdfNameEntry(0, "subject")),
      rdfPatchRow(rdfPatchHeader("key", rdfIri(1, 0))),
      rdfPatchRow(rdfPatchTransactionStart()),
      rdfPatchRow(rdfNameEntry(0, "predicate")),
      rdfPatchRow(rdfPrefixEntry(0, "https://test.org/ns2/")),
      rdfPatchRow(rdfNameEntry(0, "object")),
      rdfPatchRowAdd(
        rdfQuad(
          rdfIri(0, 1),
          rdfIri(0, 0),
          rdfIri(2, 0),
          null,
        ),
      ),
      rdfPatchRow(rdfDatatypeEntry(0, "https://test.org/xsd/integer")),
      rdfPatchRowAdd(
        rdfQuad(
          null,
          null,
          rdfLiteral("123", 1),
        ),
      ),
      rdfPatchRowDelete(
        rdfQuad(
          null,
          null,
          rdfIri(0, 3),
          null,
        ),
      ),
      rdfPatchRow(rdfPatchTransactionCommit()),
      rdfPatchRow(rdfPatchTransactionStart()),
      rdfPatchRow(rdfPrefixEntry(0, "")),
      rdfPatchRow(rdfNameEntry(0, "b")),
      rdfPatchRow(rdfNameEntry(0, "c")),
      rdfPatchRowAdd(
        rdfQuad(
          null,
          null,
          rdfTriple(
            rdfIri(1, 1),
            rdfIri(3, 4),
            rdfIri(0, 0),
          ),
        ),
      ),
      rdfPatchRow(rdfPatchTransactionAbort()),
      rdfPatchRow(rdfPatchTransactionStart()),
      rdfPatchRowAdd(
        rdfQuad(
          rdfIri(1, 2),
          rdfIri(0, 1),
          null,
        ),
      ),
      rdfPatchRowDelete(
        rdfQuad(
          null,
          null,
          null,
        ),
      ),
      rdfPatchRow(rdfPatchTransactionCommit()),
    )

  object Triples2NsDecl extends PatchTestCase:
    val mrl: Seq[PatchStatement] = Seq(
      Header("note", LangLiteral("chrząszcz", "pl")), // Polish is a beautiful language
      Header("id", BlankNode("b1")),
      TxStart,
      Add(NsDecl("test", Iri("https://test.org/test/"))),
      Add(NsDecl("ns2", Iri("https://test.org/ns2/"))),
      Add(
        Triple(
          Iri("https://test.org/test/subject"),
          Iri("https://test.org/test/predicate"),
          Iri("https://test.org/ns2/object"),
        ),
      ),
      Add(NsDecl("test2", Iri("https://test.org/test2/"))),
      Delete(NsDecl("test2", Iri("https://test.org/test2/"))),
      TxCommit,
      TxStart,
      Delete(
        Triple(
          Iri("https://test.org/test/subject"),
          Iri("https://test.org/test/predicate"),
          Iri("https://test.org/ns2/object"),
        ),
      ),
      Delete(NsDecl("test", Iri("https://test.org/test/"))),
      Delete(NsDecl("ns2")),
      TxCommit,
    )

    override def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow] = Seq(
      rdfPatchRow(opt),
      rdfPatchRow(rdfPatchHeader("note", rdfLiteral("chrząszcz", "pl"))),
      rdfPatchRow(rdfPatchHeader("id", "b1")),
      rdfPatchRow(rdfPatchTransactionStart()),
      rdfPatchRow(rdfPrefixEntry(0, "https://test.org/test/")),
      rdfPatchRow(rdfNameEntry(0, "")),
      rdfPatchRowAdd(rdfPatchNamespace("test", rdfIri(1, 0))),
      rdfPatchRow(rdfPrefixEntry(0, "https://test.org/ns2/")),
      rdfPatchRowAdd(rdfPatchNamespace("ns2", rdfIri(2, 1))),
      rdfPatchRow(rdfNameEntry(0, "subject")),
      rdfPatchRow(rdfNameEntry(0, "predicate")),
      rdfPatchRow(rdfNameEntry(0, "object")),
      rdfPatchRowAdd(
        rdfQuad(
          rdfIri(1, 0),
          rdfIri(0, 0),
          rdfIri(2, 0),
        ),
      ),
      rdfPatchRow(rdfPrefixEntry(0, "https://test.org/test2/")),
      rdfPatchRowAdd(rdfPatchNamespace("test2", rdfIri(3, 1))),
      rdfPatchRowDelete(rdfPatchNamespace("test2", rdfIri(0, 1))),
      rdfPatchRow(rdfPatchTransactionCommit()),
      rdfPatchRow(rdfPatchTransactionStart()),
      rdfPatchRowDelete(
        rdfQuad(
          null,
          null,
          null,
        ),
      ),
      rdfPatchRowDelete(rdfPatchNamespace("test", rdfIri(1, 1))),
      // IRI not set this time
      rdfPatchRowDelete(rdfPatchNamespace("ns2")),
      rdfPatchRow(rdfPatchTransactionCommit()),
    )

  object Quads1 extends PatchTestCase:
    val mrl: Seq[PatchStatement] = Seq(
      TxStart,
      Add(NsDecl("test", Iri("https://test.org/test/"), Iri("https://test.org/test/"))),
      Add(NsDecl("test", Iri("https://test.org/test/"), DefaultGraphNode())),
      Add(NsDecl("test2", Iri("https://test.org/test/"), DefaultGraphNode())),
      Add(
        Quad(
          Iri("https://test.org/test/subject"),
          Iri("https://test.org/test/predicate"),
          LangLiteral("test", "en-gb"),
          Iri("https://test.org/ns3/graph"),
        ),
      ),
      Add(
        Quad(
          Iri("https://test.org/test/subject"),
          BlankNode("blank"),
          SimpleLiteral("test"),
          Iri("https://test.org/ns3/graph"),
        ),
      ),
      Add(
        Quad(
          Iri("https://test.org/test/subject"),
          BlankNode("blank"),
          SimpleLiteral("test"),
          BlankNode("blank"),
        ),
      ),
      Delete(
        Quad(
          Iri("https://test.org/test/subject"),
          BlankNode("blank"),
          SimpleLiteral("test"),
          BlankNode("blank"),
        ),
      ),
      TxCommit,
      TxStart,
      Delete(
        Quad(
          Iri("https://test.org/test/subject"),
          BlankNode("blank"),
          SimpleLiteral("test"),
          Iri("https://test.org/ns3/graph"),
        ),
      ),
      Add(
        Quad(
          Iri("https://test.org/test/subject"),
          BlankNode("blank"),
          SimpleLiteral("test"),
          SimpleLiteral("test"),
        ),
      ),
      Delete(NsDecl("test", null, Iri("https://test.org/test/"))),
      Delete(NsDecl("test", null, DefaultGraphNode())),
      Delete(NsDecl("test2", null, DefaultGraphNode())),
      TxAbort,
    )

    override def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow] = Seq(
      rdfPatchRow(opt),
      rdfPatchRow(rdfPatchTransactionStart()),
      rdfPatchRow(rdfPrefixEntry(0, "https://test.org/test/")),
      rdfPatchRow(rdfNameEntry(0, "")),
      rdfPatchRowAdd(rdfPatchNamespace("test", rdfIri(1, 0)).setGraph(rdfIri(0, 1))),
      rdfPatchRowAdd(rdfPatchNamespace("test", rdfIri(0, 1)).setGraph(rdfDefaultGraph())),
      rdfPatchRowAdd(rdfPatchNamespace("test2", rdfIri(0, 1))),
      rdfPatchRow(rdfNameEntry(0, "subject")),
      rdfPatchRow(rdfNameEntry(0, "predicate")),
      rdfPatchRow(rdfPrefixEntry(0, "https://test.org/ns3/")),
      rdfPatchRow(rdfNameEntry(0, "graph")),
      rdfPatchRowAdd(
        rdfQuad(
          rdfIri(0, 0),
          rdfIri(0, 0),
          rdfLiteral("test", "en-gb"),
          rdfIri(2, 0),
        ),
      ),
      rdfPatchRowAdd(
        rdfQuad(
          null,
          "blank",
          rdfLiteral("test"),
          null,
        ),
      ),
      rdfPatchRowAdd(
        rdfQuad(
          null,
          null,
          null,
          "blank",
        ),
      ),
      rdfPatchRowDelete(
        rdfQuad(
          null,
          null,
          null,
          null,
        ),
      ),
      rdfPatchRow(rdfPatchTransactionCommit()),
      rdfPatchRow(rdfPatchTransactionStart()),
      rdfPatchRowDelete(
        rdfQuad(
          null,
          null,
          null,
          rdfIri(0, 4),
        ),
      ),
      rdfPatchRowAdd(
        rdfQuad(
          null,
          null,
          null,
          rdfLiteral("test"),
        ),
      ),
      rdfPatchRowDelete(rdfPatchNamespace("test", null).setGraph(rdfIri(1, 1))),
      rdfPatchRowDelete(rdfPatchNamespace("test", null).setGraph(rdfDefaultGraph())),
      rdfPatchRowDelete(rdfPatchNamespace("test2", null)),
      rdfPatchRow(rdfPatchTransactionAbort()),
    )

  /** Tests for edge cases with namespace declarations in QUADS streams.
    */
  object Quads2NsDeclOnly extends PatchTestCase:
    val mrl: Seq[PatchStatement] = Seq(
      Add(NsDecl("test", Iri("https://test.org/test/"), DefaultGraphNode())),
      Add(NsDecl("test", Iri("https://test.org/test/"), Iri("https://test.org/test/"))),
      Add(NsDecl("test2", Iri("https://test.org/test/"), BlankNode("b1"))),
      Add(
        Quad(
          Iri("https://test.org/test/"),
          Iri("https://test.org/test/"),
          Iri("https://test.org/test/"),
          BlankNode("b1"),
        ),
      ),
      Delete(NsDecl("test2", Iri("https://test.org/test/"), BlankNode("b1"))),
      Add(NsDecl("test7", Iri("https://test.org/test7/"), LangLiteral("test", "en-gb"))),
      Delete(
        NsDecl(
          "test7",
          Iri("https://test.org/test7/"),
          DtLiteral("test", Datatype("https://test.org/test7/")),
        ),
      ),
      Delete(
        NsDecl(
          "test7",
          null,
          DtLiteral("test", Datatype("https://test.org/test7/")),
        ),
      ),
    )

    override def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow] = Seq(
      rdfPatchRow(opt),
      rdfPatchRow(rdfPrefixEntry(0, "https://test.org/test/")),
      rdfPatchRow(rdfNameEntry(0, "")),
      rdfPatchRowAdd(rdfPatchNamespace("test", rdfIri(1, 0)).setGraph(rdfDefaultGraph())),
      rdfPatchRowAdd(rdfPatchNamespace("test", rdfIri(0, 1)).setGraph(rdfIri(0, 1))),
      rdfPatchRowAdd(rdfPatchNamespace("test2", rdfIri(0, 1)).setGraph("b1")),
      rdfPatchRowAdd(
        rdfQuad(
          rdfIri(0, 1),
          rdfIri(0, 1),
          rdfIri(0, 1),
          null,
        ),
      ),
      rdfPatchRowDelete(rdfPatchNamespace("test2", rdfIri(0, 1))),
      rdfPatchRow(rdfPrefixEntry(0, "https://test.org/test7/")),
      rdfPatchRowAdd(
        rdfPatchNamespace("test7", rdfIri(2, 1)).setGraph(rdfLiteral("test", "en-gb")),
      ),
      rdfPatchRow(rdfDatatypeEntry(0, "https://test.org/test7/")),
      rdfPatchRowDelete(
        rdfPatchNamespace(
          "test7",
          rdfIri(0, 1),
        ).setGraph(rdfLiteral("test", 1)),
      ),
      rdfPatchRowDelete(rdfPatchNamespace("test7", null)),
    )

  /** Some nonsensical transactions that should be simply ignored and encoded as usual.
    *
    * The validity of transactions is not checked on this layer. Jelly only does the serialization.
    */
  object MalformedTransactions extends PatchTestCase:
    val mrl: Seq[PatchStatement] = Seq(
      TxAbort,
      TxAbort,
      TxAbort,
      TxStart,
      TxStart,
      TxCommit,
      TxCommit,
      TxAbort,
      TxStart,
    )

    override def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow] = Seq(
      rdfPatchRow(opt),
      rdfPatchRow(rdfPatchTransactionAbort()),
      rdfPatchRow(rdfPatchTransactionAbort()),
      rdfPatchRow(rdfPatchTransactionAbort()),
      rdfPatchRow(rdfPatchTransactionStart()),
      rdfPatchRow(rdfPatchTransactionStart()),
      rdfPatchRow(rdfPatchTransactionCommit()),
      rdfPatchRow(rdfPatchTransactionCommit()),
      rdfPatchRow(rdfPatchTransactionAbort()),
      rdfPatchRow(rdfPatchTransactionStart()),
    )
