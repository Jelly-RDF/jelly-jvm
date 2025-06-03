package eu.neverblink.jelly.integration_tests.rdf.helpers

import com.apicatalog.rdf.*
import com.apicatalog.rdf.api.RdfQuadConsumer

import scala.jdk.CollectionConverters.*

/**
 * Helper to emit the contents of a Titanium dataset to an RdfQuadConsumer.
 */
object TitaniumDatasetEmitter:
  def emitDatasetTo(ds: RdfDataset, output: RdfQuadConsumer): Unit =
    for q <- ds.toList.asScala do
      val s = transformResource(q.getSubject)
      val p = transformResource(q.getPredicate)
      val g = if q.getGraphName.isEmpty then null
      else transformResource(q.getGraphName.get())

      if q.getObject.isLiteral then
        val lit = q.getObject.asLiteral
        if lit.getLanguage.isPresent then
          output.quad(s, p, lit.getValue, lit.getDatatype, lit.getLanguage.get(), null, g)
        else
          output.quad(s, p, lit.getValue, lit.getDatatype, null, null, g)
      else
        val o = transformResource(q.getObject.asInstanceOf[RdfResource])
        output.quad(s, p, o, null, null, null, g)
  
  def emitDatasetTo(ds: Seq[RdfNQuad], output: RdfQuadConsumer): Unit =
    for q <- ds do
      val s = transformResource(q.getSubject)
      val p = transformResource(q.getPredicate)
      val g = if q.getGraphName.isEmpty then null
      else transformResource(q.getGraphName.get())

      if q.getObject.isLiteral then
        val lit = q.getObject.asLiteral
        if lit.getLanguage.isPresent then
          output.quad(s, p, lit.getValue, lit.getDatatype, lit.getLanguage.get(), null, g)
        else
          output.quad(s, p, lit.getValue, lit.getDatatype, null, null, g)
      else
        val o = transformResource(q.getObject.asInstanceOf[RdfResource])
        output.quad(s, p, o, null, null, null, g)

  private def transformResource(res: RdfResource): String =
    if res.isIRI then res.getValue
    else "_:".concat(res.getValue)
