package eu.neverblink.jelly.convert.rdf4j.sparql.gen

import eu.neverblink.jelly.core.sparql.gen.{TermFactory, TermSpec}
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.impl.SimpleValueFactory

/** Materializes generated terms into RDF4J values. */
object Rdf4jTermFactory extends TermFactory[Value]:

  private val vf = SimpleValueFactory.getInstance()

  override def make(spec: TermSpec): Value = spec match
    case TermSpec.Iri(value) => vf.createIRI(value)
    case TermSpec.BNode(label) => vf.createBNode(label)
    case TermSpec.PlainLiteral(lex) => vf.createLiteral(lex)
    case TermSpec.LangLiteral(lex, lang) => vf.createLiteral(lex, lang)
    case TermSpec.DtLiteral(lex, datatype) => vf.createLiteral(lex, vf.createIRI(datatype))

  override def newRow(size: Int): Array[Value] = new Array[Value](size)
