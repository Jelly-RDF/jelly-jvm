package eu.neverblink.jelly.jmh.rdf

import eu.neverblink.jelly.jmh.TermMatcher
import eu.neverblink.jelly.jmh.TermMatcher.show

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/** Writes datasets with every method, reads them back, and compares every term with the original,
  * in order. This ensures that the format's encoding is lossless and the benchmark is fair.
  *
  * Blank nodes labels may be renamed. Writers that collect all statements before writing them (see
  * [[RdfMethods.Method.buffers]]) are checked for the same set of statements instead.
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

  private def isBlank(term: AnyRef): Boolean = term match
    case n: org.apache.jena.graph.Node => n.isBlank
    case _: org.eclipse.rdf4j.model.BNode => true
    case _ => false

  /** The distinct statements, each as a string, with how many there are of each (1, unless two
    * statements differ only in blank nodes that cannot be told apart).
    *
    * Blank nodes are written as colors, refined over three rounds from the statements each blank
    * node is in (Weisfeiler-Leman color refinement), so the labels do not matter. That is not a
    * full proof of isomorphism, but a difference in terms, in structure, or in which blank nodes
    * are linked within three hops makes the sets differ. Jena's exact matcher runs out of memory on
    * datasets with tens of thousands of blank nodes.
    */
  private def statementSet(
      method: RdfMethods.Method,
      statements: collection.Seq[AnyRef],
  ): Map[String, Int] =
    val rows = statements.map(method.terms).distinctBy(_.toSeq)
    var color = Map.empty[AnyRef, Int]
    def show(term: AnyRef): String =
      if term == null then "-"
      else if isBlank(term) then "_:" + color.getOrElse(term, 0)
      else term.toString
    for _ <- 1 to 3 do
      val signatures = mutable.HashMap.empty[AnyRef, mutable.ArrayBuffer[String]]
      for row <- rows; p <- row.indices if row(p) != null && isBlank(row(p)) do
        signatures.getOrElseUpdate(row(p), mutable.ArrayBuffer.empty) +=
          row.indices.map(i => if i == p then "*" else show(row(i))).mkString(" ")
      color = signatures.view.mapValues(_.sorted.mkString("\n").hashCode).toMap
    rows.groupMapReduce(_.map(show).mkString(" "))(_ => 1)(_ + _)

  /** Round-trips one dataset through one method. */
  def check(data: RdfBenchData.Data, method: RdfMethods.Method): Result =
    Try {
      val original = method.original(data)
      (original, method.readAll(method.writeToBytes(data), data.quads))
    } match
      case Failure(e) => Result.Failed(e)
      case Success((original, read)) if method.buffers =>
        // Written in an order of the writer's own, without duplicates
        val expected = statementSet(method, original)
        val actual = statementSet(method, read)
        val distinct = expected.values.sum
        if read.size == distinct && expected == actual then Result.Ok
        else
          Result.Differs(
            s"read ${read.size} statements, wrote $distinct distinct ones; " +
              (if expected == actual then "the same set" else "not the same set"),
          )
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
          case Result.Ok =>
            val duplicates =
              if method.buffers then data.size - method.original(data).distinct.size else 0
            val note =
              if duplicates > 0 then s" (the writer dropped $duplicates duplicates)" else ""
            println(f"OK    $name%-28s ${method.name}$note")
          case Result.Failed(e) =>
            failures += 1
            println(f"FAIL  $name%-28s ${method.name}: $e")
          case Result.Differs(summary) =>
            failures += 1
            println(f"DIFF  $name%-28s ${method.name}: $summary")
    println(s"\n$failures failing combinations")
