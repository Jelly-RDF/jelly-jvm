package eu.neverblink.jelly.core.sparql.helpers

import eu.neverblink.jelly.core.helpers.Mrl.Node
import eu.neverblink.jelly.core.sparql.SparqlResultsHandler

import java.util
import scala.annotation.experimental
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

/** Collects the decoded variables and rows for assertions. */
@experimental
final class ResultsCollector extends SparqlResultsHandler[Node]:
  val variables: ListBuffer[String] = ListBuffer[String]()
  val rows: ListBuffer[Seq[Node]] = ListBuffer[Seq[Node]]()
  var variableCalls: Int = 0

  override def handleVariables(vars: util.List[String]): Unit =
    variableCalls += 1
    variables ++= vars.asScala

  override def createRowBuffer(size: Int): Array[Object & Node] =
    new Array[Node](size).asInstanceOf[Array[Object & Node]]

  override def handleRow(row: Array[Object & Node]): Unit =
    // The array is reused by the decoder – copy it
    rows += row.toSeq
