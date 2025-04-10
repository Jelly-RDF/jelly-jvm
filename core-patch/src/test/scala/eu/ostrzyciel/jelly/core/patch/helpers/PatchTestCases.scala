package eu.ostrzyciel.jelly.core.patch.helpers

import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*

import scala.annotation.experimental

@experimental
object PatchTestCases:
  import Mpl.*
  import RdfPatchRow as R

  val testCases: Seq[(String, PatchTestCase, PatchStatementType.Recognized)] = Seq(
    ("a triple patch", Triples1, PatchStatementType.TRIPLES),
    ("a triple patch with namespace declarations", Triples2NsDecl, PatchStatementType.TRIPLES),
    ("a quad patch", Quads1, PatchStatementType.QUADS),
    ("nonsensical transaction commands", MalformedTransactions, PatchStatementType.TRIPLES),
  )

  trait PatchTestCase:
    def mrl: Seq[PatchStatement]
    def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow]
    final def encodedFull(opt: RdfPatchOptions, groupByN: Int): Seq[RdfPatchFrame] =
      encoded(opt)
        .grouped(groupByN)
        .map(rows => RdfPatchFrame(rows))
        .toSeq
      
  object Triples1 extends PatchTestCase:
    val mrl = Seq(
      Header("key", Iri("https://test.org/test/subject")),
      TxStart,
      Add(Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
      )),
      Add(Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        DtLiteral("123", Datatype("https://test.org/xsd/integer")),
      )),
      Delete(Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
      )),
      TxCommit,
      TxStart,
      Add(Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        TripleNode(Triple(Iri("https://test.org/test/subject"), Iri("b"), Iri("c"))),
      )),
      TxAbort,
      TxStart,
      Add(Triple(
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/test/subject"),
        TripleNode(Triple(Iri("https://test.org/test/subject"), Iri("b"), Iri("c"))),
      )),
      Delete(Triple(
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/test/subject"),
        TripleNode(Triple(Iri("https://test.org/test/subject"), Iri("b"), Iri("c"))),
      )),
      TxCommit,
    )

    def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow] = Seq(
      R.ofOptions(opt),
      R.ofPrefix(RdfPrefixEntry(0, "https://test.org/test/")),
      R.ofName(RdfNameEntry(0, "subject")),
      R.ofHeader(RdfPatchHeader("key", RdfIri(1, 0))),
      R.ofTransactionStart,
      R.ofName(RdfNameEntry(0, "predicate")),
      R.ofPrefix(RdfPrefixEntry(0, "https://test.org/ns2/")),
      R.ofName(RdfNameEntry(0, "object")),
      R.ofTripleAdd(RdfTriple(
        RdfIri(0, 1),
        RdfIri(0, 0),
        RdfIri(2, 0),
      )),
      R.ofDatatype(RdfDatatypeEntry(0, "https://test.org/xsd/integer")),
      R.ofTripleAdd(RdfTriple(
        null,
        null,
        RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1)),
      )),
      R.ofTripleDelete(RdfTriple(
        null,
        null,
        RdfIri(0, 3),
      )),
      R.ofTransactionCommit,
      R.ofTransactionStart,
      R.ofPrefix(RdfPrefixEntry(0, "")),
      R.ofName(RdfNameEntry(0, "b")),
      R.ofName(RdfNameEntry(0, "c")),
      R.ofTripleAdd(RdfTriple(
        null,
        null,
        RdfTriple(
          RdfIri(1, 1),
          RdfIri(3, 4),
          RdfIri(0, 0),
        ),
      )),
      R.ofTransactionAbort,
      R.ofTransactionStart,
      R.ofTripleAdd(RdfTriple(
        RdfIri(1, 2),
        RdfIri(0, 1),
        null,
      )),
      R.ofTripleDelete(RdfTriple(
        null,
        null,
        null,
      )),
      R.ofTransactionCommit,
    )

  object Triples2NsDecl extends PatchTestCase:
    val mrl = Seq(
      Header("note", LangLiteral("chrząszcz", "pl")), // Polish is a beautiful language
      Header("id", BlankNode("b1")),
      TxStart,
      Add(NsDecl("test", Iri("https://test.org/test/"))),
      Add(NsDecl("ns2", Iri("https://test.org/ns2/"))),
      Add(Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
      )),
      Add(NsDecl("test2", Iri("https://test.org/test2/"))),
      Delete(NsDecl("test2", Iri("https://test.org/test2/"))),
      TxCommit,
      TxStart,
      Delete(Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
      )),
      Delete(NsDecl("test", Iri("https://test.org/test/"))),
      Delete(NsDecl("ns2", Iri("https://test.org/ns2/"))),
      TxCommit,
    )

    override def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow] = Seq(
      R.ofOptions(opt),
      R.ofHeader(RdfPatchHeader("note", RdfLiteral("chrząszcz", RdfLiteral.LiteralKind.Langtag("pl")))),
      R.ofHeader(RdfPatchHeader("id", RdfTerm.Bnode("b1"))),
      R.ofTransactionStart,
      R.ofPrefix(RdfPrefixEntry(0, "https://test.org/test/")),
      R.ofName(RdfNameEntry(0, "")),
      R.ofNamespaceAdd(RdfNamespaceDeclaration("test", RdfIri(1, 0))),
      R.ofPrefix(RdfPrefixEntry(0, "https://test.org/ns2/")),
      R.ofNamespaceAdd(RdfNamespaceDeclaration("ns2", RdfIri(2, 1))),
      R.ofName(RdfNameEntry(0, "subject")),
      R.ofName(RdfNameEntry(0, "predicate")),
      R.ofName(RdfNameEntry(0, "object")),
      R.ofTripleAdd(RdfTriple(
        RdfIri(1, 0),
        RdfIri(0, 0),
        RdfIri(2, 0),
      )),
      R.ofPrefix(RdfPrefixEntry(0, "https://test.org/test2/")),
      R.ofNamespaceAdd(RdfNamespaceDeclaration("test2", RdfIri(3, 1))),
      R.ofNamespaceDelete(RdfNamespaceDeclaration("test2", RdfIri(0, 1))),
      R.ofTransactionCommit,
      R.ofTransactionStart,
      R.ofTripleDelete(RdfTriple(
        null,
        null,
        null,
      )),
      R.ofNamespaceDelete(RdfNamespaceDeclaration("test", RdfIri(1, 1))),
      R.ofNamespaceDelete(RdfNamespaceDeclaration("ns2", RdfIri(2, 1))),
      R.ofTransactionCommit,
    )

  object Quads1 extends PatchTestCase:
    val mrl = Seq(
      TxStart,
      Add(NsDecl("test", Iri("https://test.org/test/"))),
      Add(Quad(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        LangLiteral("test", "en-gb"),
        Iri("https://test.org/ns3/graph"),
      )),
      Add(Quad(
        Iri("https://test.org/test/subject"),
        BlankNode("blank"),
        SimpleLiteral("test"),
        Iri("https://test.org/ns3/graph"),
      )),
      Add(Quad(
        Iri("https://test.org/test/subject"),
        BlankNode("blank"),
        SimpleLiteral("test"),
        BlankNode("blank"),
      )),
      Delete(Quad(
        Iri("https://test.org/test/subject"),
        BlankNode("blank"),
        SimpleLiteral("test"),
        BlankNode("blank"),
      )),
      TxCommit,
      TxStart,
      Delete(Quad(
        Iri("https://test.org/test/subject"),
        BlankNode("blank"),
        SimpleLiteral("test"),
        Iri("https://test.org/ns3/graph"),
      )),
      Add(Quad(
        Iri("https://test.org/test/subject"),
        BlankNode("blank"),
        SimpleLiteral("test"),
        SimpleLiteral("test"),
      )),
      Delete(NsDecl("test", Iri("https://test.org/test/"))),
      TxAbort,
    )

    override def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow] = Seq(
      R.ofOptions(opt),
      R.ofTransactionStart,
      R.ofPrefix(RdfPrefixEntry(0, "https://test.org/test/")),
      R.ofName(RdfNameEntry(0, "")),
      R.ofNamespaceAdd(RdfNamespaceDeclaration("test", RdfIri(1, 0))),
      R.ofName(RdfNameEntry(0, "subject")),
      R.ofName(RdfNameEntry(0, "predicate")),
      R.ofPrefix(RdfPrefixEntry(0, "https://test.org/ns3/")),
      R.ofName(RdfNameEntry(0, "graph")),
      R.ofQuadAdd(RdfQuad(
        RdfIri(0, 0),
        RdfIri(0, 0),
        RdfLiteral("test", RdfLiteral.LiteralKind.Langtag("en-gb")),
        RdfIri(2, 0),
      )),
      R.ofQuadAdd(RdfQuad(
        null,
        RdfTerm.Bnode("blank"),
        RdfLiteral("test"),
        null,
      )),
      R.ofQuadAdd(RdfQuad(
        null,
        null,
        null,
        RdfTerm.Bnode("blank"),
      )),
      R.ofQuadDelete(RdfQuad(
        null,
        null,
        null,
        null,
      )),
      R.ofTransactionCommit,
      R.ofTransactionStart,
      R.ofQuadDelete(RdfQuad(
        null,
        null,
        null,
        RdfIri(0, 4),
      )),
      R.ofQuadAdd(RdfQuad(
        null,
        null,
        null,
        RdfLiteral("test"),
      )),
      R.ofNamespaceDelete(RdfNamespaceDeclaration("test", RdfIri(1, 1))),
      R.ofTransactionAbort,
    )

  /**
   * Some nonsensical transactions that should be simply ignored and encoded as usual.
   *
   * The validity of transactions is not checked on this layer. Jelly only does the serialization.
   */
  object MalformedTransactions extends PatchTestCase:
    val mrl = Seq(
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
      R.ofOptions(opt),
      R.ofTransactionAbort,
      R.ofTransactionAbort,
      R.ofTransactionAbort,
      R.ofTransactionStart,
      R.ofTransactionStart,
      R.ofTransactionCommit,
      R.ofTransactionCommit,
      R.ofTransactionAbort,
      R.ofTransactionStart,
    )
