package eu.neverblink.jelly.jmh.rdf

import eu.neverblink.jelly.jmh.TermMatcher
import eu.neverblink.jelly.jmh.TermMatcher.show

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/** Writes datasets with every method, reads them back, and compares every term with the original,
  * in order. This ensures that the format's encoding is lossless and the benchmark is fair.
  *
  * Blank nodes labels may be renamed.
  *
  * With no datasets or methods given, checks every dataset with every method. Run with
  * (`rdfRoundTrip` is an alias for this `runMain`):
  * {{{
  * sbt "rdfRoundTrip -r 100000 nanopubs"
  * }}}
  */
object RdfRoundTripCheck:
  private final case class Mismatch(statement: Int, position: Int, expected: AnyRef, actual: AnyRef)

  private val positions = IndexedSeq("subject", "predicate", "object", "graph")

  enum Result:
    case Ok

    /** Writing or reading threw. */
    case Failed(error: Throwable)

    /** The statements came back, but not as they were written. */
    case Differs(summary: String)

  /** Round-trips one dataset through one method. */
  def check(data: RdfBenchData.Data, method: RdfMethods.Method): Result =
    Try {
      val original = method.original(data)
      (original, method.readAll(method.writeToBytes(data), data.quads))
    } match
      case Failure(e) => Result.Failed(e)
      case Success((original, read)) =>
        val matcher = TermMatcher()
        val mismatches = mutable.ArrayBuffer.empty[Mismatch]
        var mismatchCount = 0
        for i <- 0 until math.min(original.size, read.size) do
          val expected = method.terms(original(i))
          val actual = method.terms(read(i))
          for p <- positions.indices do
            if !matcher.same(expected(p), actual(p)) then
              mismatchCount += 1
              if mismatches.size < 3 then mismatches += Mismatch(i, p, expected(p), actual(p))
        if mismatchCount == 0 && read.size == original.size then Result.Ok
        else
          val header =
            if read.size != original.size then
              s"read ${read.size} statements, wrote ${original.size}; $mismatchCount differing terms"
            else s"$mismatchCount terms of ${original.size} statements differ"
          Result.Differs(
            (header +: mismatches.toSeq.map { m =>
              s"    statement ${m.statement} ${positions(m.position)}:\n" +
                s"      expected ${show(m.expected)}\n" +
                s"      actual   ${show(m.actual)}"
            }).mkString("\n"),
          )

  def main(args: Array[String]): Unit =
    var rows = 100_000
    var methods = RdfMethods.all
    val datasets = mutable.ArrayBuffer.empty[String]
    var rest = args.toList
    while rest.nonEmpty do
      rest match
        case ("-r" | "--rows") :: value :: tail =>
          rows = value.toInt
          rest = tail
        case ("-m" | "--methods") :: value :: tail =>
          methods = value.split(",").toIndexedSeq.map(RdfMethods(_))
          rest = tail
        case name :: tail =>
          datasets += name
          rest = tail
        case Nil => ()

    val names = if datasets.nonEmpty then datasets.toSeq else RdfBenchData.datasetNames
    var failures = 0
    for name <- names do
      val data = RdfBenchData.load(name, rows)
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
