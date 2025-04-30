package eu.ostrzyciel.jelly.convert.rdf4j

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.base.CoreDatatype

private[rdf4j] final case class Rdf4jDatatype(dt: IRI, coreDt: CoreDatatype)
