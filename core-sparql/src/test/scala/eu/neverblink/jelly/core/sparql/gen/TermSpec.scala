package eu.neverblink.jelly.core.sparql.gen

/** Description of an RDF term that does not depend on any RDF library.
  *
  * The generator produces these, and a [[TermFactory]] turns them into the node type of a concrete
  * library. That way the same generated data can drive the core encoder (with the mock node model),
  * the Jena integration, and the benchmarks – and the three can be compared against each other.
  */
enum TermSpec:
  case Iri(value: String)
  case BNode(label: String)
  case PlainLiteral(lex: String)
  case LangLiteral(lex: String, lang: String)
  case DtLiteral(lex: String, datatype: String)

/** Materializes [[TermSpec]]s into the node type of a concrete RDF library. */
trait TermFactory[TNode]:
  def make(spec: TermSpec): TNode

  /** A row buffer with the right runtime component type – the encoder needs a real `TNode[]`, so
    * only the implementation can allocate it.
    */
  def newRow(size: Int): Array[TNode]

  /** Materializes generated rows into node arrays that can be fed straight to the encoder. Unbound
    * cells become nulls.
    */
  final def materializeRows(rows: Seq[Seq[TermSpec | Null]]): IndexedSeq[Array[TNode]] =
    rows.iterator.map { row =>
      val out = newRow(row.size)
      var i = 0
      for cell <- row do
        out(i) = (cell match
          case null => null
          case spec: TermSpec => make(spec)
        ).asInstanceOf[TNode]
        i += 1
      out
    }.toIndexedSeq
