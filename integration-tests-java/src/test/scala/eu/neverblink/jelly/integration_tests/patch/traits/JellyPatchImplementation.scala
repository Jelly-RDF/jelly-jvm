//package eu.neverblink.jelly.integration_tests.patch.traits
//
//import eu.neverblink.jelly.core.proto.v1.patch.RdfPatchOptions
//import eu.neverblink.jelly.integration_tests.util.TestComparable
//
//import java.io.{InputStream, OutputStream}
//
///**
// * Interface for Jelly-Patch implementations (e.g., jelly-jena-patch).
// * @tparam TPatch The type of the patch.
// */
//trait JellyPatchImplementation[TPatch : TestComparable]:
//  def readJelly(in: InputStream, supportedOptions: Option[RdfPatchOptions] = None): TPatch
//
//  def writeJelly(out: OutputStream, patch: TPatch, options: Option[RdfPatchOptions], frameSize: Int): Unit
//
//  def name: String
//
//  def supportsRdfStar: Boolean = false
//
//  def supportsGeneralizedStatements: Boolean = false
