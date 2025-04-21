package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.ProtoHandler.AnyProtoHandler
import eu.neverblink.jelly.core.helpers.Mrl.*

import java.util
import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters
import scala.jdk.javaapi.CollectionConverters.asScala

final class ProtoCollector extends AnyProtoHandler[Node]:
  val namespaces: mutable.ListBuffer[(String, Node)] = mutable.ListBuffer.empty
  val statements: mutable.ListBuffer[Node] = mutable.ListBuffer.empty

  override def handleNamespace(prefix: String, namespace: Node): Unit =
    namespaces += ((prefix, namespace))

  override def handleTriple(subject: Node, predicate: Node, `object`: Node): Unit =
    statements += Triple(subject, predicate, `object`)

  override def handleQuad(subject: Node, predicate: Node, `object`: Node, graph: Node): Unit =
    statements += Quad(subject, predicate, `object`, graph)

  override def handleGraph(graph: Node, triples: util.Collection[Node]): Unit =
    statements += Graph(graph, asScala(triples).toSeq)

  def clear(): Unit =
    namespaces.clear()
    statements.clear()
