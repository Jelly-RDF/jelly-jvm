package eu.ostrzyciel.jelly.core.patch.helpers

import eu.ostrzyciel.jelly.core.NamespaceDeclaration
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.patch.helpers.Mpl.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*

object PatchTestCases:
  import RdfPatchRow as R

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
      Add(NamespaceDeclaration("test", "https://test.org/test/")),
      Add(NamespaceDeclaration("ns2", "https://test.org/ns2/")),
      Add(Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
      )),
      Add(NamespaceDeclaration("test2", "https://test.org/test2/")),
      Delete(NamespaceDeclaration("test2", "https://test.org/test2/")),
      TxCommit,
      TxStart,
      Delete(Triple(
        Iri("https://test.org/test/subject"),
        Iri("https://test.org/test/predicate"),
        Iri("https://test.org/ns2/object"),
      )),
      Delete(NamespaceDeclaration("test", "https://test.org/test/")),
      Delete(NamespaceDeclaration("ns2", "https://test.org/ns2/")),
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
