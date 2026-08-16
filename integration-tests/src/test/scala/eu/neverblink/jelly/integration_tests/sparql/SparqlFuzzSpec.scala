package eu.neverblink.jelly.integration_tests.sparql

import eu.neverblink.jelly.convert.jena.sparql.gen.JenaTermFactory
import eu.neverblink.jelly.convert.jena.sparql.{
  JenaSparqlConverterFactory,
  RowSetReaderJelly,
  RowSetWriterJelly,
}
import eu.neverblink.jelly.core.helpers.Mrl
import eu.neverblink.jelly.core.proto.v1.sparql.{SparqlResultsFrame, SparqlResultsOptions}
import eu.neverblink.jelly.core.sparql.gen.{MrlTermFactory, ResultSetSpec, SparqlDataGen}
import eu.neverblink.jelly.core.sparql.helpers.{MockSparqlConverterFactory, ResultsCollector}
import eu.neverblink.jelly.core.sparql.{JellySparqlOptions, SparqlEncoder}
import org.apache.jena.graph.Node
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.BindingFactory
import org.apache.jena.sparql.exec.RowSetStream
import org.apache.jena.sys.JenaSystem
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.annotation.experimental
import scala.jdk.CollectionConverters.*
import scala.util.Random

/** Fuzzing for Jelly-SPARQL, driven by the shared result set generator
  * ([[eu.neverblink.jelly.core.sparql.gen.SparqlDataGen]], also used by the JMH benchmarks).
  *
  * Every generated result set is pushed through four paths, so that a bug in one implementation
  * cannot hide behind the matching bug in its counterpart:
  *
  *   - core encoder -> core decoder (mock node model, no RDF library involved)
  *   - Jena writer -> Jena reader (the full RowSet stack)
  *   - Jena writer -> core decoder
  *   - core encoder -> Jena reader
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

  // -----------------------------------------------------------------------------------------
  // Encoding / decoding through the two implementations
  // -----------------------------------------------------------------------------------------

  private def encodeCore(
      vars: Seq[String],
      rows: IndexedSeq[Array[Mrl.Node & Object]],
      frameSize: Int,
      options: SparqlResultsOptions,
  ): Array[Byte] =
    val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options))
    encoder.setVariables(vars.asJava)
    val out = ByteArrayOutputStream()
    var rowsInFrame = 0
    var wroteAnyFrame = false
    for row <- rows do
      encoder.appendRow(row)
      rowsInFrame += 1
      if rowsInFrame >= frameSize then
        encoder.endFrame().writeDelimitedTo(out)
        wroteAnyFrame = true
        rowsInFrame = 0
    // The last (possibly empty) frame still carries the header
    if rowsInFrame > 0 || !wroteAnyFrame then encoder.endFrame().writeDelimitedTo(out)
    out.toByteArray

  private def decodeCore(bytes: Array[Byte]): (Seq[String], Seq[Seq[Any]]) =
    val collector = ResultsCollector()
    val decoder =
      MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
    val in = ByteArrayInputStream(bytes)
    var frame = SparqlResultsFrame.parseDelimitedFrom(in)
    while frame != null do
      decoder.ingestFrame(frame)
      frame = SparqlResultsFrame.parseDelimitedFrom(in)
    (collector.variables.toSeq, collector.rows.toSeq)

  private def encodeJena(
      vars: Seq[String],
      rows: IndexedSeq[Array[Node]],
      frameSize: Int,
      options: SparqlResultsOptions,
  ): Array[Byte] =
    val jenaVars = vars.map(Var.alloc)
    val bindings = rows.map { row =>
      val builder = BindingFactory.builder()
      for i <- row.indices if row(i) != null do builder.add(jenaVars(i), row(i))
      builder.build()
    }
    val out = ByteArrayOutputStream()
    val writer = RowSetWriterJelly(
      RowSetWriterJelly.Options(options, frameSize, true),
      JenaSparqlConverterFactory.getInstance(),
    )
    writer.write(out, RowSetStream.create(jenaVars.asJava, bindings.iterator.asJava), null)
    out.toByteArray

  private def decodeJena(bytes: Array[Byte]): (Seq[String], Seq[Seq[Any]]) =
    val rowSet = RowSetReaderJelly(
      RowSetReaderJelly.Options(),
      JenaSparqlConverterFactory.getInstance(),
    ).read(ByteArrayInputStream(bytes), null)
    val jenaVars = rowSet.getResultVars.asScala.toSeq
    val rows = rowSet.asScala.map(binding => jenaVars.map(binding.get)).toSeq
    (jenaVars.map(_.getVarName), rows)

  // -----------------------------------------------------------------------------------------
  // Test driver
  // -----------------------------------------------------------------------------------------

  /** Frame size and lookup table sizes for one case.
    *
    * The name table must be able to hold the working set of a single frame, otherwise the encoder
    * legitimately refuses to encode. One row contributes at most one name per column, so
    * `frameSize * columns` is a safe upper bound.
    */
  private def parametersFor(spec: ResultSetSpec, rnd: Random): (Int, SparqlResultsOptions) =
    val frameSize = 1 + rnd.nextInt(64)
    val nameTableSize = math.min(
      JellySparqlOptions.BIG.getMaxNameTableSize,
      math.max(8, frameSize * math.max(1, spec.columns.size) + 32),
    )
    val options = SparqlResultsOptions
      .newInstance()
      .setMaxNameTableSize(nameTableSize)
      // Every fourth case runs with the prefix lookup disabled, which is a separate code path
      .setMaxPrefixTableSize(
        if rnd.nextInt(4) == 0 then 0 else JellySparqlOptions.BIG.getMaxPrefixTableSize,
      )
      .setMaxDatatypeTableSize(JellySparqlOptions.BIG.getMaxDatatypeTableSize)
    (frameSize, options)

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
    val (frameSize, options) = parametersFor(spec, rnd)
    withClue(s"spec: $spec, frameSize: $frameSize, options: $options\n") {
      val generated = SparqlDataGen.generate(spec)
      val vars = spec.variables

      val mrlRows = MrlTermFactory.materializeRows(generated)
      val jenaRows = JenaTermFactory.materializeRows(generated)
      val mrlExpected = mrlRows.map(_.toSeq)
      val jenaExpected = jenaRows.map(_.toSeq)

      val coreBytes = encodeCore(vars, mrlRows, frameSize, options)
      val jenaBytes = encodeJena(vars, jenaRows, frameSize, options)

      compare("core -> core", decodeCore(coreBytes), vars, mrlExpected)
      compare("jena -> jena", decodeJena(jenaBytes), vars, jenaExpected)
      compare("jena -> core", decodeCore(jenaBytes), vars, mrlExpected)
      compare("core -> jena", decodeJena(coreBytes), vars, jenaExpected)
    }

  "Jelly-SPARQL" should {
    for preset <- SparqlDataGen.presets do
      s"round-trip the '${preset.name}' preset in all four directions" in {
        runCase(SparqlDataGen.scaled(preset, maxPresetRows), Random(preset.name.hashCode.toLong))
      }

    s"round-trip $iterations randomly generated result sets in all four directions" in {
      val rnd = Random(baseSeed)
      for i <- 0 until iterations do runCase(SparqlDataGen.randomSpec(s"random-$i", rnd), rnd)
    }
  }
