package eu.neverblink.jelly.core.sparql.gen

import eu.neverblink.jelly.core.helpers.Mrl

/** Materializes generated terms into the mock RDF library, for exercising the core encoder and
  * decoder without any real RDF library in the way.
  */
object MrlTermFactory extends TermFactory[Mrl.Node & Object]:

  override def make(spec: TermSpec): Mrl.Node & Object = spec match
    case TermSpec.Iri(value) => Mrl.Iri(value)
    case TermSpec.BNode(label) => Mrl.BlankNode(label)
    case TermSpec.PlainLiteral(lex) => Mrl.SimpleLiteral(lex)
    case TermSpec.LangLiteral(lex, lang) => Mrl.LangLiteral(lex, lang)
    case TermSpec.DtLiteral(lex, datatype) => Mrl.DtLiteral(lex, Mrl.Datatype(datatype))

  // The encoder takes a Java TNode[], which Scala sees as Array[Node & Object]
  override def newRow(size: Int): Array[Mrl.Node & Object] =
    new Array[Mrl.Node](size).asInstanceOf[Array[Mrl.Node & Object]]
