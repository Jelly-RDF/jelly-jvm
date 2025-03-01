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
//      TxStart,
//      Add(Triple(
//        Iri("https://test.org/test/subject"),
//        Iri("https://test.org/test/predicate"),
//        TripleNode(Triple(Iri("https://test.org/test/subject"), Iri("b"), Iri("c"))),
//      )),
//      TxAbort,
//      TxStart,
//      Add(Triple(
//        Iri("https://test.org/test/predicate"),
//        Iri("https://test.org/test/subject"),
//        TripleNode(Triple(Iri("https://test.org/test/subject"), Iri("b"), Iri("c"))),
//      )),
//      Delete(Triple(
//        Iri("https://test.org/test/predicate"),
//        Iri("https://test.org/test/subject"),
//        TripleNode(Triple(Iri("https://test.org/test/subject"), Iri("b"), Iri("c"))),
//      )),
//      TxCommit,
    )

    def encoded(opt: RdfPatchOptions): Seq[RdfPatchRow] = Seq(
      R.ofOptions(opt),
      R.ofPrefix(RdfPrefixEntry(0, "https://test.org/test/")),
      R.ofName(RdfNameEntry(0, "subject")),
      R.ofHeader(RdfPatchHeader("key", RdfIri(0, 0))),
      R.ofTransactionStart,
      R.ofName(RdfNameEntry(0, "predicate")),
      R.ofPrefix(RdfPrefixEntry(0, "https://test.org/ns2/")),
      R.ofName(RdfNameEntry(0, "object")),
      R.ofTripleAdd(RdfTriple(
        RdfIri(0, 1),
        RdfIri(0, 0),
        RdfIri(2, 3),
      )),
      R.ofDatatype(RdfDatatypeEntry(0, "https://test.org/xsd/integer")),
      R.ofTripleAdd(RdfTriple(
        RdfIri(1, 1),
        RdfIri(0, 0),
        RdfLiteral("123", RdfLiteral.LiteralKind.Datatype(1)),
      )),
      R.ofTripleDelete(RdfTriple(
        RdfIri(0, 1),
        RdfIri(0, 0),
        RdfIri(2, 3),
      )),
      R.ofTransactionCommit,
    )
    
