package eu.ostrzyciel.jelly.core

object Constants:
  val jellyName = "Jelly"
  val jellyFileExtension = "jelly"
  val jellyContentType = "application/x-jelly-rdf"
  
  @deprecated("Use `protoVersion_1_0_x` instead", "2.8.0")
  val protoVersionNoNsDecl = 1
  val protoVersion_1_0_x = 1 
  val protoVersion_1_1_x = 2
  val protoVersion: Int = protoVersion_1_1_x
  
  @deprecated("Use `protoSemanticVersion_1_0_0` instead", "2.8.0")
  val protoSemanticVersionNoNsDecl = "1.0.0"
  val protoSemanticVersion_1_0_0 = "1.0.0" // First protocol version
  val protoSemanticVersion_1_1_0 = "1.1.0" // Protocol version with namespace declarations
  val protoSemanticVersion_1_1_1 = "1.1.1" // Protocol version with metadata in RdfStreamFrame
  val protoSemanticVersion: String = protoSemanticVersion_1_1_1
