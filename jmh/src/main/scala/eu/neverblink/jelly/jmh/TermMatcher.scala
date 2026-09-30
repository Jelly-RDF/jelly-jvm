package eu.neverblink.jelly.jmh

import org.apache.jena.graph.Node
import org.eclipse.rdf4j.model.{BNode, TripleTerm}

import scala.collection.mutable

/** Compares Jena or RDF4J terms, for the round-trip checks. Blank nodes may be renamed: they are
  * matched by a bijection that is built as it goes.
  */
final class TermMatcher:
  private val forward = mutable.HashMap.empty[AnyRef, AnyRef]
  private val backward = mutable.HashMap.empty[AnyRef, AnyRef]

  private def sameBlank(a: AnyRef, b: AnyRef): Boolean =
    (forward.get(a), backward.get(b)) match
      case (None, None) =>
        forward(a) = b
        backward(b) = a
        true
      case (Some(fb), Some(ba)) => fb == b && ba == a
      case _ => false

  def same(expected: AnyRef, actual: AnyRef): Boolean = (expected, actual) match
    case (null, null) => true
    case (null, _) | (_, null) => false
    case (a: Node, b: Node) if a.isBlank && b.isBlank => sameBlank(a, b)
    case (a: Node, b: Node) if a.isTripleTerm && b.isTripleTerm =>
      val (ta, tb) = (a.getTriple, b.getTriple)
      same(ta.getSubject, tb.getSubject) && same(ta.getPredicate, tb.getPredicate) &&
      same(ta.getObject, tb.getObject)
    case (a: BNode, b: BNode) => sameBlank(a, b)
    case (a: TripleTerm, b: TripleTerm) =>
      same(a.getSubject, b.getSubject) && same(a.getPredicate, b.getPredicate) &&
      same(a.getObject, b.getObject)
    case (a, b) => a == b

object TermMatcher:
  /** A term for an error message, cut short if it is long. */
  def show(term: AnyRef): String =
    val s = String.valueOf(term)
    if s.length > 160 then s.take(160) + "…" else s
