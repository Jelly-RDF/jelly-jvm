package eu.neverblink.jelly.core.sparql.gen

import scala.collection.mutable
import scala.util.Random

/** Deterministic generator of SPARQL result sets, shared by the benchmarks (jelly-jmh) and the
  * fuzzing tests (jelly-integration-tests).
  *
  * The generated data is library-agnostic ([[TermSpec]]); use a [[TermFactory]] to turn it into
  * concrete RDF nodes.
  *
  * Every column is generated from its own pseudo-random stream derived from the spec's seed, so
  * changing one column does not perturb the others.
  */
object SparqlDataGen:

  private val namespaceBase = "https://example.org/ns"
  private val datatypeBase = "https://example.org/dt#"
  // Canonically cased (RFC 5646) on purpose: Jena rewrites "en-gb" to "en-GB" when it builds the
  // node, so a non-canonical tag here would make the mock and Jena models disagree about the data
  // before Jelly ever sees it.
  private val langs = IndexedSeq("en", "fr", "de", "es", "pl", "nl", "en-GB", "pt-BR")

  /** Rows of a generated result set, in row-major order. Unbound cells are nulls. */
  type Rows = IndexedSeq[IndexedSeq[TermSpec | Null]]

  /** Generates the result set described by the spec, in row-major order. */
  def generate(spec: ResultSetSpec): Rows =
    val columns = generateColumns(spec)
    if columns.isEmpty then IndexedSeq.fill(spec.rows)(IndexedSeq.empty)
    else (0 until spec.rows).map(row => columns.map(_(row)))

  /** Generates the result set described by the spec, in column-major order. */
  def generateColumns(spec: ResultSetSpec): IndexedSeq[IndexedSeq[TermSpec | Null]] =
    spec.columns.zipWithIndex.map { (column, i) =>
      generateColumn(column, spec.rows, Random(spec.seed * 31 + i))
    }.toIndexedSeq

  private def generateColumn(
      col: ColumnSpec,
      rows: Int,
      rnd: Random,
  ): IndexedSeq[TermSpec | Null] =
    val pool = buildPool(col, col.kind, rnd)
    val altPool =
      if col.mixFraction > 0 then buildPool(col, altKind(col.kind), rnd) else IndexedSeq.empty
    val out = mutable.ArrayBuffer.empty[TermSpec | Null]
    out.sizeHint(rows)
    // Cursor into the pool, used when the column is `sorted`
    var cursor = 0

    def nextValue(row: Int): TermSpec =
      val useAlt = altPool.nonEmpty && row >= col.mixStartRow && rnd.nextDouble() < col.mixFraction
      val from = if useAlt then altPool else pool
      if col.sorted then
        val value = from(cursor % from.size)
        cursor += 1
        value
      else from(rnd.nextInt(from.size))

    def drawRunLength(): Int =
      if col.runLength <= 1.0 then 1
      else if !col.jitterRuns then math.max(1, math.round(col.runLength).toInt)
      else
        // Geometric distribution with the requested mean
        val p = 1.0 / col.runLength
        var n = 1
        while rnd.nextDouble() > p && n < 4096 do n += 1
        n

    while out.size < rows do
      val row = out.size
      col.sparsity match
        case Sparsity.Alternating =>
          if row % 2 == 1 then out += null else out += nextValue(row)
        case sparsity =>
          if rnd.nextDouble() >= col.boundFraction then
            val blockSize = sparsity match
              case Sparsity.Blocks(size) => math.max(1, size)
              case _ => 1
            var k = 0
            while k < blockSize && out.size < rows do
              out += null
              k += 1
          else
            val value = nextValue(row)
            var len = drawRunLength()
            while len > 0 && out.size < rows do
              out += value
              len -= 1
    out.toIndexedSeq

  private def altKind(kind: ColumnKind): ColumnKind =
    if kind == ColumnKind.Iri then ColumnKind.PlainLiteral else ColumnKind.Iri

  private def buildPool(col: ColumnSpec, kind: ColumnKind, rnd: Random): IndexedSeq[TermSpec] =
    val n = math.max(1, col.distinctValues)
    val ns = math.max(1, col.namespaces)
    val len = math.max(1, col.valueLength)
    kind match
      case ColumnKind.Iri =>
        (0 until n).map(j => TermSpec.Iri(s"$namespaceBase${j % ns}#${pad(s"term$j", len)}"))
      case ColumnKind.BNode =>
        (0 until n).map(j => TermSpec.BNode(pad(s"b$j", math.min(len, 24))))
      case ColumnKind.PlainLiteral =>
        (0 until n).map(j => TermSpec.PlainLiteral(pad(s"value $j ", len)))
      case ColumnKind.LangLiteral =>
        (0 until n).map(j =>
          TermSpec.LangLiteral(pad(s"value $j ", len), langs(j % math.min(ns, langs.size))),
        )
      case ColumnKind.DtLiteral =>
        (0 until n).map(j => TermSpec.DtLiteral(pad(s"$j", len), s"${datatypeBase}d${j % ns}"))

  private def pad(base: String, length: Int): String =
    if base.length >= length then base else base + "x" * (length - base.length)

  // ---------------------------------------------------------------------------------------------
  // Preset catalogue
  //
  // Each preset isolates one property of the format, so a regression shows up in exactly one place.
  // ---------------------------------------------------------------------------------------------

  /** Default number of rows – large enough to amortize the per-frame overhead, small enough for a
    * JMH trial to set up quickly.
    */
  private val N = 20_000

  val presets: IndexedSeq[ResultSetSpec] = IndexedSeq(
    // --- IRI columns: the lookup tables and the prefix/name inference ---
    // Best case: one namespace, every row a new name, in order – name ids are inferred, so the
    // lookup entries themselves are the only real cost.
    ResultSetSpec("iri-sorted", Seq(ColumnSpec(distinctValues = N, sorted = true)), N),
    // Exactly the same values, shuffled. The name-id inference collapses and the name table
    // thrashes (the pool is much bigger than the table), so this doubles as the lookup-eviction
    // case. A/B it against 'iri-sorted' to see how much the format leans on the result being
    // ordered – the two specs differ in one flag and nothing else.
    ResultSetSpec("iri-shuffled", Seq(ColumnSpec(distinctValues = N)), N),
    // A pool small enough to sit in the name table: entries are transmitted once, then reused.
    ResultSetSpec("iri-small-pool", Seq(ColumnSpec(distinctValues = 2048)), N),
    // Many namespaces: the prefix lookup is exercised, prefix ids can no longer be inferred.
    ResultSetSpec("iri-many-ns", Seq(ColumnSpec(distinctValues = 2048, namespaces = 32)), N),
    // --- Repeated values: the run-length part of the layout encoding ---
    ResultSetSpec(
      "runs-2",
      Seq(ColumnSpec(distinctValues = 2048, runLength = 2, sorted = true)),
      N,
    ),
    ResultSetSpec(
      "runs-8",
      Seq(ColumnSpec(distinctValues = 2048, runLength = 8, sorted = true)),
      N,
    ),
    // Runs of exactly 8 and 9 cells: len_code 6 vs the 7 escape (see the layout notes in
    // sparql.proto). A perf or size cliff here means the escape path is wrong.
    ResultSetSpec(
      "runs-exactly-8",
      Seq(ColumnSpec(distinctValues = 2048, runLength = 8, jitterRuns = false, sorted = true)),
      N,
    ),
    ResultSetSpec(
      "runs-exactly-9",
      Seq(ColumnSpec(distinctValues = 2048, runLength = 9, jitterRuns = false, sorted = true)),
      N,
    ),
    // Adversarial: A, B, A, B, ... – no run ever fires, so the layout must stay empty.
    ResultSetSpec("alternating-values", Seq(ColumnSpec(distinctValues = 2, sorted = true)), N),
    // --- Unbound cells: the unbound-run part of the layout encoding ---
    ResultSetSpec(
      "sparse-random-10",
      Seq(ColumnSpec(distinctValues = 2048, boundFraction = 0.1)),
      N,
    ),
    ResultSetSpec(
      "sparse-random-50",
      Seq(ColumnSpec(distinctValues = 2048, boundFraction = 0.5)),
      N,
    ),
    ResultSetSpec(
      "sparse-random-90",
      Seq(ColumnSpec(distinctValues = 2048, boundFraction = 0.9)),
      N,
    ),
    // Best case for unbound cells: whole blocks collapse into one token.
    ResultSetSpec(
      "sparse-blocks",
      Seq(ColumnSpec(distinctValues = 2048, boundFraction = 0.5, sparsity = Sparsity.Blocks(16))),
      N,
    ),
    // Adversarial: every second cell unbound, so the layout gets one token per two cells.
    ResultSetSpec(
      "sparse-alternating",
      Seq(ColumnSpec(distinctValues = 2048, sparsity = Sparsity.Alternating)),
      N,
    ),
    // --- Literals ---
    ResultSetSpec("lit-plain", Seq(ColumnSpec(ColumnKind.PlainLiteral, distinctValues = 2048)), N),
    ResultSetSpec(
      "lit-lang",
      Seq(ColumnSpec(ColumnKind.LangLiteral, distinctValues = 2048, namespaces = 8)),
      N,
    ),
    ResultSetSpec(
      "lit-typed",
      Seq(ColumnSpec(ColumnKind.DtLiteral, distinctValues = 2048, namespaces = 8)),
      N,
    ),
    // Big lexical forms: string handling should not copy more than it has to.
    ResultSetSpec(
      "lit-long",
      Seq(ColumnSpec(ColumnKind.PlainLiteral, distinctValues = 512, valueLength = 4096)),
      2_000,
    ),
    ResultSetSpec("bnodes", Seq(ColumnSpec(ColumnKind.BNode, distinctValues = 2048)), N),
    // --- Polymorphic columns ---
    ResultSetSpec(
      "poly-half",
      Seq(ColumnSpec(distinctValues = 2048, mixFraction = 0.5)),
      N,
    ),
    // Almost all IRIs, with the first literal appearing well past the first frame boundary: forces
    // a mid-stream header restatement and a switch to a polymorphic column.
    ResultSetSpec(
      "poly-late-switch",
      Seq(ColumnSpec(distinctValues = 2048, mixFraction = 0.0005, mixStartRow = 5_000)),
      N,
    ),
    // --- Result set shape ---
    ResultSetSpec("wide-5", Seq.fill(5)(ColumnSpec(distinctValues = 2048)), N),
    ResultSetSpec("wide-20", Seq.fill(20)(ColumnSpec(distinctValues = 2048)), N / 2),
    // 100 columns: the per-column, per-frame framing overhead should dominate here.
    ResultSetSpec("wide-100", Seq.fill(100)(ColumnSpec(distinctValues = 2048)), 2_000),
    // The fixed overhead of a stream – the very common "LIMIT 1" case.
    ResultSetSpec("single-row", Seq.fill(3)(ColumnSpec(distinctValues = 8)), 1),
    // Zero variables: row_count is the only payload.
    ResultSetSpec("zero-vars", Seq.empty, N),
    // --- A realistic mix: entity, property, label, optional value, polymorphic object ---
    ResultSetSpec(
      "realistic-mixed",
      Seq(
        // ?item – sorted entity IRIs, repeated a few times each (ORDER BY ?item)
        ColumnSpec(distinctValues = 4096, runLength = 3, sorted = true, valueLength = 8),
        // ?prop – a handful of predicates
        ColumnSpec(distinctValues = 32, valueLength = 10),
        // ?label – language-tagged literals
        ColumnSpec(ColumnKind.LangLiteral, distinctValues = 4096, namespaces = 4, valueLength = 24),
        // ?optional – bound in 40% of the rows
        ColumnSpec(ColumnKind.PlainLiteral, distinctValues = 1024, boundFraction = 0.4),
        // ?value – mixes IRIs and literals
        ColumnSpec(distinctValues = 2048, mixFraction = 0.3),
      ),
      N,
    ),
  )

  private val presetsByName: Map[String, ResultSetSpec] = presets.map(s => s.name -> s).toMap

  val presetNames: IndexedSeq[String] = presets.map(_.name)

  /** Shrinks a spec to at most `maxRows` rows, keeping its shape intact – in particular, the row at
    * which a polymorphic column starts mixing term types is scaled along with the row count, so
    * that a shrunk 'poly-late-switch' still switches somewhere in the middle.
    */
  def scaled(spec: ResultSetSpec, maxRows: Int): ResultSetSpec =
    if spec.rows <= maxRows then spec
    else
      val factor = maxRows.toDouble / spec.rows
      spec.copy(
        rows = maxRows,
        columns = spec.columns.map(c => c.copy(mixStartRow = (c.mixStartRow * factor).toInt)),
      )

  def preset(name: String): ResultSetSpec =
    presetsByName.getOrElse(
      name,
      throw IllegalArgumentException(
        s"Unknown preset '$name'. Available: ${presetNames.mkString(", ")}",
      ),
    )

  // ---------------------------------------------------------------------------------------------
  // Random specs, for fuzzing
  // ---------------------------------------------------------------------------------------------

  /** Draws a random result set spec. Kept small on purpose: fuzzing wants many different shapes,
    * not much data per shape.
    */
  def randomSpec(name: String, rnd: Random): ResultSetSpec =
    // 0 columns is a legal (and easy to get wrong) result set
    val columnCount = rnd.nextInt(6)
    ResultSetSpec(
      name = name,
      columns = (0 until columnCount).map(_ => randomColumn(rnd)),
      rows = rnd.nextInt(600),
      seed = rnd.nextLong(),
    )

  def randomColumn(rnd: Random): ColumnSpec =
    ColumnSpec(
      kind = ColumnKind.values(rnd.nextInt(ColumnKind.values.length)),
      distinctValues = 1 + rnd.nextInt(64),
      namespaces = 1 + rnd.nextInt(4),
      valueLength = 1 + rnd.nextInt(24),
      boundFraction = rnd.nextDouble(),
      sparsity = rnd.nextInt(3) match
        case 0 => Sparsity.Random
        case 1 => Sparsity.Alternating
        case _ => Sparsity.Blocks(1 + rnd.nextInt(10)),
      runLength = 1.0 + rnd.nextInt(10),
      jitterRuns = rnd.nextBoolean(),
      sorted = rnd.nextBoolean(),
      // Most columns stay monomorphic – polymorphic ones are the exception in practice
      mixFraction = if rnd.nextInt(3) == 0 then rnd.nextDouble() * 0.5 else 0.0,
      mixStartRow = rnd.nextInt(50),
    )
