package eu.neverblink.jelly.integration_tests.sparql

import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions
import eu.neverblink.jelly.core.sparql.JellySparqlOptions
import eu.neverblink.jelly.core.sparql.gen.{ResultSetSpec, SparqlDataGen}
import org.apache.jena.sys.JenaSystem
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental
import scala.util.Random

/** Fuzzing for Jelly-SPARQL, driven by the shared result set generator
  * ([[eu.neverblink.jelly.core.sparql.gen.SparqlDataGen]], also used by the JMH benchmarks).
  *
  * Every generated result set is pushed through every writer/reader pair of every implementation
  * (core codec with the mock node model, Jena, RDF4J), so that a bug in one implementation cannot
  * hide behind the matching bug in its counterpart.
  *
  * The number of random cases and the seed can be overridden with the JELLY_SPARQL_FUZZ_ITERATIONS
  * and JELLY_SPARQL_FUZZ_SEED environment variables – bump the iterations for a long soak run.
  */
@experimental
class SparqlFuzzSpec extends AnyWordSpec, Matchers:
  JenaSystem.init()

  private val iterations =
    sys.env.get("JELLY_SPARQL_FUZZ_ITERATIONS").map(_.toInt).getOrElse(500)
  private val baseSeed =
    sys.env.get("JELLY_SPARQL_FUZZ_SEED").map(_.toLong).getOrElse(20260816L)

  /** The presets are sized for the benchmarks – shrink them so the test suite stays quick. */
  private val maxPresetRows = 400

  private val impls = SparqlImplementation.all

  // -----------------------------------------------------------------------------------------
  // Test driver
  // -----------------------------------------------------------------------------------------

  /** Frame value budget and lookup table sizes for one case.
    *
    * The two are chosen independently on purpose: whenever the frame's working set outgrows the
    * name table, the encoder must end the frame early and the caller must carry the row over. That
    * path is the interesting one, so most cases should hit it.
    */
  private def parametersFor(spec: ResultSetSpec, rnd: Random): (Int, SparqlResultsOptions) =
    val maxValuesPerFrame = 1 + rnd.nextInt(1024)
    val nameTableSize = JellySparqlOptions.MIN_NAME_TABLE_SIZE +
      rnd.nextInt(JellySparqlOptions.BIG_NAME_TABLE_SIZE - JellySparqlOptions.MIN_NAME_TABLE_SIZE)
    val options = SparqlResultsOptions
      .newInstance()
      .setMaxNameTableSize(nameTableSize)
      // Every fourth case runs with the prefix lookup disabled, which is a separate code path
      .setMaxPrefixTableSize(
        if rnd.nextInt(4) == 0 then 0 else JellySparqlOptions.BIG.getMaxPrefixTableSize,
      )
      .setMaxDatatypeTableSize(JellySparqlOptions.BIG.getMaxDatatypeTableSize)
    (maxValuesPerFrame, options)

  private def compare(
      label: String,
      actual: (Seq[String], Seq[Seq[Any]]),
      expectedVars: Seq[String],
      expectedRows: Seq[Seq[Any]],
  ): Unit =
    val (actualVars, actualRows) = actual
    withClue(s"$label – variables: ") { actualVars shouldBe expectedVars }
    withClue(s"$label – row count: ") { actualRows.size shouldBe expectedRows.size }
    for i <- expectedRows.indices do
      withClue(s"$label – row $i: ") { actualRows(i) shouldBe expectedRows(i) }

  private def runCase(spec: ResultSetSpec, rnd: Random): Unit =
    val (maxValuesPerFrame, options) = parametersFor(spec, rnd)
    withClue(s"spec: $spec, maxValuesPerFrame: $maxValuesPerFrame, options: $options\n") {
      val generated = SparqlDataGen.generate(spec)
      val vars = spec.variables
      val encoded = impls.map(impl => impl.encode(vars, generated, maxValuesPerFrame, options))
      for
        (writer, bytes) <- impls.zip(encoded)
        reader <- impls
      do
        compare(
          s"${writer.name} -> ${reader.name}",
          reader.decode(bytes),
          vars,
          reader.expected(generated),
        )
    }

  "Jelly-SPARQL" should {
    for preset <- SparqlDataGen.presets do
      s"round-trip the '${preset.name}' preset in all directions" in {
        runCase(SparqlDataGen.scaled(preset, maxPresetRows), Random(preset.name.hashCode.toLong))
      }

    s"round-trip $iterations randomly generated result sets in all directions" in {
      val rnd = Random(baseSeed)
      for i <- 0 until iterations do runCase(SparqlDataGen.randomSpec(s"random-$i", rnd), rnd)
    }

    "round-trip boolean (ASK) results in all directions" in {
      for
        value <- Seq(true, false)
        writer <- impls
      do
        val bytes = writer.encodeAsk(value, JellySparqlOptions.SMALL)
        for reader <- impls do
          withClue(s"${writer.name} -> ${reader.name}, value $value: ") {
            reader.decodeAsk(bytes) shouldBe value
          }
    }
  }
