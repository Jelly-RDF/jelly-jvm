package eu.neverblink.jelly.convert.jena.sparql.gen

import eu.neverblink.jelly.core.sparql.gen.{TermFactory, TermSpec}
import org.apache.jena.datatypes.TypeMapper
import org.apache.jena.graph.{Node, NodeFactory}

/** Materializes generated terms into Jena nodes. */
object JenaTermFactory extends TermFactory[Node]:

  override def make(spec: TermSpec): Node = spec match
    case TermSpec.Iri(value) => NodeFactory.createURI(value)
    case TermSpec.BNode(label) => NodeFactory.createBlankNode(label)
    case TermSpec.PlainLiteral(lex) => NodeFactory.createLiteralString(lex)
    case TermSpec.LangLiteral(lex, lang) => NodeFactory.createLiteralLang(lex, lang)
    case TermSpec.DtLiteral(lex, datatype) =>
      NodeFactory.createLiteralDT(lex, TypeMapper.getInstance().getSafeTypeByName(datatype))

  override def newRow(size: Int): Array[Node] = new Array[Node](size)
