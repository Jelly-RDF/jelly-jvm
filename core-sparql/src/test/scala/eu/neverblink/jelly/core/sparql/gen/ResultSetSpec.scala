package eu.neverblink.jelly.core.sparql.gen

/** Term type that a column is predominantly made of. */
enum ColumnKind:
  case Iri, BNode, PlainLiteral, LangLiteral, DtLiteral

/** How the unbound cells of a column are distributed over the rows. */
enum Sparsity:
  /** Each cell is independently unbound with probability `1 - boundFraction`. */
  case Random

  /** Strict bound/unbound alternation – the worst case for the layout encoding, as every second
    * cell forces a new layout token. `boundFraction` and `runLength` are ignored.
    */
  case Alternating

  /** Contiguous blocks of `size` unbound cells – the best case for the layout encoding, as a whole
    * block costs one token.
    */
  case Blocks(size: Int)

/** Knobs describing one column (= one result variable) of a generated result set.
  *
  * The defaults describe the simplest possible column: dense, IRI-only, one namespace, no repeated
  * values.
  *
  * @param kind
  *   term type the column is predominantly made of
  * @param distinctValues
  *   size of the pool of distinct values the column draws from
  * @param namespaces
  *   number of distinct IRI namespaces / datatypes / language tags used
  * @param valueLength
  *   length of the local part of IRIs / of the lexical form of literals
  * @param boundFraction
  *   fraction of cells that are bound (1.0 = no unbound cells)
  * @param sparsity
  *   how the unbound cells are distributed
  * @param runLength
  *   mean length of a run of the same value repeated in consecutive rows
  * @param jitterRuns
  *   if false, every run is exactly `runLength` long instead of being drawn at random – use this to
  *   hit the run-length escape boundaries exactly
  * @param sorted
  *   draw values from the pool in order (simulates ORDER BY) instead of at random
  * @param mixFraction
  *   fraction of bound cells that use a term type other than `kind`, which forces the column into a
  *   polymorphic one
  * @param mixStartRow
  *   first row at which off-type values may appear – set this past a frame boundary to force a
  *   mid-stream header restatement
  */
final case class ColumnSpec(
    kind: ColumnKind = ColumnKind.Iri,
    distinctValues: Int = 1024,
    namespaces: Int = 1,
    valueLength: Int = 12,
    boundFraction: Double = 1.0,
    sparsity: Sparsity = Sparsity.Random,
    runLength: Double = 1.0,
    jitterRuns: Boolean = true,
    sorted: Boolean = false,
    mixFraction: Double = 0.0,
    mixStartRow: Int = 0,
)

/** A complete, reproducible description of a generated SPARQL result set.
  *
  * @param name
  *   short identifier, used as the JMH parameter value and in test names
  * @param columns
  *   one entry per result variable, in projection order
  * @param rows
  *   number of solutions to generate
  * @param seed
  *   seed of the pseudo-random generator – the same spec always yields the same data
  */
final case class ResultSetSpec(
    name: String,
    columns: Seq[ColumnSpec],
    rows: Int,
    seed: Long = 0L,
):
  def variables: Seq[String] = columns.indices.map(i => s"v$i")
