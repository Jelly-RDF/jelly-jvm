package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.convert.rdf4j.rio.{JellyFormat, JellyParserSettings, JellyWriterSettings}
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import eu.neverblink.jelly.integration_tests.rdf.util.JenaToRdf4jAdapter
import eu.neverblink.jelly.integration_tests.rdf.util.riot.TestRiot
import eu.neverblink.jelly.integration_tests.util.Measure
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.lang.LabelToNode
import org.eclipse.rdf4j.model.impl.{SimpleTriple, SimpleValueFactory}
import org.eclipse.rdf4j.model.{Statement, Value}
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}

import java.io.*
import scala.jdk.CollectionConverters.*

given seqMeasure[T]: Measure[Seq[T]] = (seq: Seq[T]) => seq.size

object Rdf4jSerDes
    extends NativeSerDes[Seq[Statement], Seq[Statement]],
      ProtocolSerDes[Value, Statement, Statement]:
  TestRiot.initialize()

  val name = "RDF4J"

  override def supportsGeneralizedStatements: Boolean = false

  override def supportsRdfStar(physicalStreamType: PhysicalStreamType): Boolean = true

  private def read(
      streams: Seq[InputStream],
      format: RDFFormat,
      supportedOptions: Option[RdfStreamOptions] = None,
  ): Seq[Statement] =
    val parser = Rio.createParser(format)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    // Preserve original blank node labels to match blank nodes IDs across different files
    parser.setPreserveBNodeIDs(true)
    supportedOptions.foreach(opt => parser.setParserConfig(JellyParserSettings.from(opt)))
    for is <- streams do parser.parse(is)

    collector.getStatements.asScala.toSeq

  override def readTriplesW3C(is: InputStream): Seq[Statement] = read(Seq(is), RDFFormat.TURTLESTAR)

  override def readTriplesW3C(files: Seq[File]): Seq[Statement] =
    val fileIss = files.map(FileInputStream(_))
    val result = read(fileIss, RDFFormat.TURTLESTAR)
    fileIss.foreach(_.close())
    result

  override def readQuadsW3C(is: InputStream): Seq[Statement] = {
    // Read the *.nq files using the test NQ_ANY Jena parser and then translate the results to RDF4J
    // This is needed because RDF4J doesn't have a NQ-star compatible parser
    val collector = new StatementCollector()
    val adapter = JenaToRdf4jAdapter(collector)

    RDFParser.source(is)
      .lang(TestRiot.NQ_ANY)
      // Preserve original blank node labels to match blank nodes IDs across different files
      .labelToNode(LabelToNode.createUseLabelAsGiven())
      .parse(adapter)

    collector.getStatements.asScala.toSeq
  }

  override def readQuadsW3C(files: Seq[File]): Seq[Statement] =
    val fileIss = files.map(FileInputStream(_))
    val result = for is <- fileIss yield readQuadsW3C(is)
    fileIss.foreach(_.close())
    result.flatten

  override def readTriplesJelly(
      is: InputStream,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[Statement] =
    read(Seq(is), JellyFormat.JELLY, supportedOptions)

  override def readTriplesJelly(
      file: File,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[Statement] =
    read(Seq(FileInputStream(file)), JellyFormat.JELLY, supportedOptions)

  override def readQuadsJelly(
      is: InputStream,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[Statement] =
    read(Seq(is), JellyFormat.JELLY, supportedOptions)

  override def readQuadsOrGraphsJelly(
      file: File,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[Statement] =
    val fileIs = FileInputStream(file)
    val result = read(Seq(fileIs), JellyFormat.JELLY, supportedOptions)
    fileIs.close()
    result

  private def write(
      os: OutputStream,
      model: Seq[Statement],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val conf =
      if opt.isDefined then
        JellyWriterSettings.empty
          .setFrameSize(frameSize)
          .setJellyOptions(opt.get)
      else JellyWriterSettings.empty.setFrameSize(frameSize)
    conf.setEnableNamespaceDeclarations(false)
    val writer = Rio.createWriter(JellyFormat.JELLY, os)
    writer.setWriterConfig(conf)
    writer.startRDF()
    model.foreach(writer.handleStatement)
    writer.endRDF()

  override def writeTriplesJelly(
      os: OutputStream,
      model: Seq[Statement],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    // We set the physical type to TRIPLES, because the writer has no way of telling triples from
    // quads in RDF4J. Thus, the writer will default to QUADS.
    write(os, model, opt.map(_.clone.setPhysicalType(PhysicalStreamType.TRIPLES)), frameSize)

  override def writeTriplesJelly(
      file: File,
      triples: Seq[Statement],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val fileOs = new FileOutputStream(file)
    write(fileOs, triples, opt, frameSize)
    fileOs.close()

  override def writeQuadsJelly(
      os: OutputStream,
      dataset: Seq[Statement],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    // No need to set the physical type, because the writer will default to QUADS.
    write(os, dataset, opt, frameSize)

  override def writeQuadsJelly(
      file: File,
      quads: Seq[Statement],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val fileOs = new FileOutputStream(file)
    write(fileOs, quads, opt, frameSize)
    fileOs.close()

  override def isBlank(node: Value): Boolean = node.isBNode

  override def getBlankNodeLabel(node: Value): String = node.stringValue()

  override def isNodeTriple(node: Value): Boolean = node.isTriple

  override def asNodeTriple(node: Value): Statement =
    val triple = node.asInstanceOf[SimpleTriple]
    // We have to convert into statement because node triple is not statement in Rdf4j
    SimpleValueFactory.getInstance.createStatement(
      triple.getSubject,
      triple.getPredicate,
      triple.getObject,
    )

  override def iterateTerms(statement: Statement): Seq[Value] =
    if statement.getContext != null then
      Seq(statement.getSubject, statement.getPredicate, statement.getObject, statement.getContext)
    else Seq(statement.getSubject, statement.getPredicate, statement.getObject)
