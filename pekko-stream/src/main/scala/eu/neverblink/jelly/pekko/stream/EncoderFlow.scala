package eu.neverblink.jelly.pekko.stream

import eu.neverblink.jelly.core.JellyConverterFactory
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.pekko.stream.impl.EncoderFlowBuilderImpl
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow

/** Factory of encoder flows for Jelly streams. When using these methods, you don't have to set the
  * physicalType and logicalType properties of [[RdfStreamOptions]]. They will be set automatically.
  * You can set the logical stream type manually, though.
  *
  * These methods will also ensure that the produced stream is more-or-less valid (that it adheres
  * to the appropriate physical and logical stream type).
  */
object EncoderFlow:

  /** Flexible builder for creating encoder flows for Jelly streams.
    *
    * Example usage:
    * {{{
    * EncoderFlow.builder
    *  .withLimiter(SizeLimiter(1000))
    *  .flatTriples(JellyOptions.smallStrict)
    *  .withNamespaceDeclarations
    *  .flow
    * }}}
    *
    * See more examples in the `examples` module.
    *
    * @param factory
    *   Implementation of [[JellyConverterFactory]] (e.g., JenaConverterFactory).
    * @tparam TNode
    *   Type of nodes.
    * @return
    *   Encoder flow builder.
    * @since 2.6.0
    */
  final def builder[TNode](using
      factory: JellyConverterFactory[TNode, ?, ?, ?],
  ): EncoderFlowBuilderImpl[TNode]#RootBuilder =
    new EncoderFlowBuilderImpl[TNode].builder
