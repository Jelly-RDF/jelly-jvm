package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.integration_tests.util.RdfCompareHydrator

import java.io.File

trait ProtocolSerDes[TNode, TTriple, TQuad] extends RdfCompareHydrator[TNode, TTriple | TQuad]:
  def name: String

  def readTriplesW3C(files: Seq[File]): Seq[TTriple]
  def readQuadsW3C(files: Seq[File]): Seq[TQuad]

  def readTriplesJelly(file: File, supportedOptions: Option[RdfStreamOptions]): Seq[TTriple]
  def readQuadsOrGraphsJelly(file: File, supportedOptions: Option[RdfStreamOptions]): Seq[TQuad]

  def writeTriplesJelly(file: File, triples: Seq[TTriple], opt: Option[RdfStreamOptions], frameSize: Int): Unit
  def writeQuadsJelly(file: File, quads: Seq[TQuad], opt: Option[RdfStreamOptions], frameSize: Int): Unit

  def supportsRdfStar: Boolean
  def supportsGeneralizedStatements: Boolean
