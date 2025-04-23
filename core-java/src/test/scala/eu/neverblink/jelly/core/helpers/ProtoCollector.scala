package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.RdfHandler.AnyRdfHandler
import eu.neverblink.jelly.core.helpers.Mrl.*

import java.util
import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters
import scala.jdk.javaapi.CollectionConverters.asScala

final class ProtoCollector extends AnyRdfHandler[Node]:
  val namespaces: mutable.ListBuffer[(String, Node)] = mutable.ListBuffer.empty
  val statements: mutable.ListBuffer[Statement] = mutable.ListBuffer.empty

  private var currentGraph: Option[Node] = None
  private val currentGraphTripleBuffer = mutable.ListBuffer.empty[Triple]

  override def handleNamespace(prefix: String, namespace: Node): Unit =
    namespaces += ((prefix, namespace))

  override def handleTriple(subject: Node, predicate: Node, `object`: Node): Unit =
    if currentGraph.isDefined then
      currentGraphTripleBuffer += Triple(subject, predicate, `object`)
    else
      statements += Triple(subject, predicate, `object`)

  override def handleQuad(subject: Node, predicate: Node, `object`: Node, graph: Node): Unit =
    statements += Quad(subject, predicate, `object`, graph)

  override def handleGraphStart(graph: Node): Unit =
    currentGraph = Some(graph)

  override def handleGraphEnd(): Unit =
    if currentGraphTripleBuffer.nonEmpty then
      statements += Graph(currentGraph.get, currentGraphTripleBuffer.toSeq)
      currentGraphTripleBuffer.clear()
      currentGraph = None

  def clear(): Unit =
    namespaces.clear()
    statements.clear()
