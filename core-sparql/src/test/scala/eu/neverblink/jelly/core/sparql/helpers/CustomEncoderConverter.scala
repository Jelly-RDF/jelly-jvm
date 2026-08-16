package eu.neverblink.jelly.core.sparql.helpers

import eu.neverblink.jelly.core.helpers.Mrl.Node
import eu.neverblink.jelly.core.{NodeEncoder, ProtoEncoderConverter}

/** Encoder converter whose node encoding is supplied by the test. Used to reach the parts of the
  * encoder that the well-behaved [[eu.neverblink.jelly.core.helpers.MockProtoEncoderConverter]]
  * never exercises.
  */
final class CustomEncoderConverter(f: (NodeEncoder[Node], Node) => Object)
    extends ProtoEncoderConverter[Node]:

  override def nodeToProto(encoder: NodeEncoder[Node], node: Node): Object = f(encoder, node)

  override def graphNodeToProto(encoder: NodeEncoder[Node], node: Node): Object = f(encoder, node)
