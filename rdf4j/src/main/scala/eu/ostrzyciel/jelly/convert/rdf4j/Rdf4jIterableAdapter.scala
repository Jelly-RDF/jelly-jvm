package eu.ostrzyciel.jelly.convert.rdf4j

import eu.ostrzyciel.jelly.core.{IterableAdapter, NamespaceDeclaration}
import eu.ostrzyciel.jelly.core.IterableAdapter.IterableFromIterator
import org.eclipse.rdf4j.model.{Model, Statement, Value}

import scala.collection.immutable
import scala.jdk.CollectionConverters.*

object Rdf4jIterableAdapter extends IterableAdapter[Value, Statement, Statement, Model, Model]:
  extension (model: Model)
    override def asTriples: immutable.Iterable[Statement] =
      IterableFromIterator[Statement](() => model.iterator().asScala)

    override def asQuads: immutable.Iterable[Statement] =
      IterableFromIterator[Statement](() => model.iterator().asScala)

    override def asGraphs: immutable.Iterable[(Value, immutable.Iterable[Statement])] =
      IterableFromIterator[(Value, immutable.Iterable[Statement])](() => {
        model.contexts().iterator().asScala.map(context => {
          (
            context,
            IterableFromIterator[Statement](
              () => model.getStatements(null, null, null, context).iterator().asScala
            )
          )
        })
      })
      
    override def namespaceDeclarations: immutable.Iterable[NamespaceDeclaration] =
      IterableFromIterator[NamespaceDeclaration](() => {
        model.getNamespaces.iterator().asScala.map(ns => 
          NamespaceDeclaration(prefix = ns.getPrefix, iri = ns.getName)
        )
      })
