package eu.neverblink.jelly.pekko.stream

import eu.neverblink.jelly.core.JellyConverterFactory
import eu.neverblink.jelly.pekko.stream.impl.EncoderFlowBuilderImpl

/** Factory of encoder flows for Jelly streams. When using these methods, you don't have to set the
  * physicalType, streamType, and logicalType properties of [[RdfStreamOptions]]. They will be set
  * automatically.
  *
  * By default, the flows write Jelly-RDF 1.2 (column layout). If the protocol version in the
  * options is set to 1 or 2, they write Jelly-RDF 1.0 or 1.1 (row layout) instead. Writing
  * Jelly-RDF 1.0/1.1 is deprecated and will be removed in Jelly-JVM 5.0.0.
  *
  * In Jelly-RDF 1.2 streams, flows of single statements and of flat groups of statements write
  * streams of type FLAT, and flows of RDF graphs and datasets write streams of RDF Messages (type
  * MESSAGES).
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
