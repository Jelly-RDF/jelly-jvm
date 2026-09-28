package eu.neverblink.jelly.jmh.sparql

import org.apache.jena.graph.Node
import org.eclipse.rdf4j.model.{BNode, TripleTerm}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/** Writes datasets with every method that can read, reads them back, and compares every value with
  * the original. This ensures that the format's encoding is lossless and the benchmark is fair.
  *
  * Blank nodes labels may be renamed.
  *
  * Run with:
  * {{{sbt "jmh/runMain eu.neverblink.jelly.jmh.sparql.SparqlRoundTripCheck -r 100000 nanopubs"}}}
  * With no datasets or methods given, checks every dataset with every method that can read.
  */
object SparqlRoundTripCheck:
  private final case class Mismatch(row: Int, variable: String, expected: AnyRef, actual: AnyRef)

  /** Compares terms, matching blank nodes by a bijection that is built as it goes. */
  private final class TermMatcher:
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

  private def show(term: AnyRef): String =
    val s = String.valueOf(term)
    if s.length > 160 then s.take(160) + "…" else s

  enum Result:
    case Ok

    /** Writing or reading threw. */
    case Failed(error: Throwable)

    /** The result set came back, but not as it was written. */
    case Differs(summary: String)

  /** Round-trips one dataset through one method. */
  def check(data: SparqlBenchData.Data, method: SparqlMethods.Method): Result =
    val original = method.original(data)
    val variables = data.variables
    Try {
      val bytes = method.writeToBytes(data)
      val read = mutable.ArrayBuffer.empty[AnyRef]
      method.read(bytes, read += _)
      read
    } match
      case Failure(e) => Result.Failed(e)
      case Success(read) =>
        val matcher = TermMatcher()
        val mismatches = mutable.ArrayBuffer.empty[Mismatch]
        var mismatchCount = 0
        for i <- 0 until math.min(original.size, read.size) do
          for v <- 0 until variables.size do
            val expected = original(i)(v)
            val actual = method.cell(read(i), variables.get(v))
            if !matcher.same(expected, actual) then
              mismatchCount += 1
              if mismatches.size < 3 then
                mismatches += Mismatch(i, variables.get(v), expected, actual)
        if mismatchCount == 0 && read.size == original.size then Result.Ok
        else
          val header =
            if read.size != original.size then
              s"read ${read.size} rows, wrote ${original.size}; $mismatchCount differing values"
            else s"$mismatchCount of ${original.size * variables.size} values differ"
          Result.Differs(
            (header +: mismatches.toSeq.map { m =>
              s"    row ${m.row} ?${m.variable}:\n" +
                s"      expected ${show(m.expected)}\n" +
                s"      actual   ${show(m.actual)}"
            }).mkString("\n"),
          )

  def main(args: Array[String]): Unit =
    var rows = 100_000
    var methods = SparqlMethods.all.filter(_.canRead)
    val datasets = mutable.ArrayBuffer.empty[String]
    var rest = args.toList
    while rest.nonEmpty do
      rest match
        case ("-r" | "--rows") :: value :: tail =>
          rows = value.toInt
          rest = tail
        case ("-m" | "--methods") :: value :: tail =>
          methods = value.split(",").toIndexedSeq.map(SparqlMethods(_))
          rest = tail
        case name :: tail =>
          datasets += name
          rest = tail
        case Nil => ()

    val names = if datasets.nonEmpty then datasets.toSeq else SparqlBenchData.datasetNames
    var failures = 0
    for name <- names do
      val data = SparqlBenchData.load(name, rows)
      for method <- methods do
        check(data, method) match
          case Result.Ok => println(f"OK    $name%-28s ${method.name}")
          case Result.Failed(e) =>
            failures += 1
            println(f"FAIL  $name%-28s ${method.name}: $e")
          case Result.Differs(summary) =>
            failures += 1
            println(f"DIFF  $name%-28s ${method.name}: $summary")
    println(s"\n$failures failing combinations")
