//package eu.neverblink.jelly.integration_tests.patch.traits
//
//import eu.neverblink.jelly.core.proto.v1.patch.PatchStatementType
//import eu.neverblink.jelly.integration_tests.util.TestComparable
//
//import java.io.{File, InputStream}
//
///**
// * Interface for Jelly-Patch implementations that also support the RDF Patch format:
// * https://afs.github.io/rdf-delta/rdf-patch.html
// *
// * @tparam TPatch The type of the patch.
// */
//trait RdfPatchImplementation[TPatch : TestComparable] extends JellyPatchImplementation[TPatch]:
//  def readRdf(in: InputStream, stType: PatchStatementType): TPatch
//
//  def readRdf(filenames: Seq[File], stType: PatchStatementType, flat: Boolean): TPatch
