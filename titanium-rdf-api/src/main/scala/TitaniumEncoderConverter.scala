import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.{GraphTerm, SpoTerm}

final class TitaniumEncoderConverter extends ProtoEncoderConverter[String, ?, ?]:
  private def err: String =
    throw new NotImplementedError("The titanium-rdf-api implementation of Jelly does not support" +
      "triple and quad objects. Use the term-based API instead.")
  
  override def getTstS(triple: Nothing): String = err
  override def getTstP(triple: Nothing): String = err
  override def getTstO(triple: Nothing): String = err

  override def getQstS(quad: Nothing): String = err
  override def getQstP(quad: Nothing): String = err
  override def getQstO(quad: Nothing): String = err
  override def getQstG(quad: Nothing): String = err

  override def nodeToProto(encoder: NodeEncoder[String], node: String): SpoTerm =


  override def graphNodeToProto(encoder: NodeEncoder[String], node: String): GraphTerm = ???
