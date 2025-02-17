package eu.ostrzyciel.jelly.core.internal

import eu.ostrzyciel.jelly.core.NodeEncoder

private[core] object NodeEncoderFactory:

  /**
   * Create a new NodeEncoder using the default cache size heuristics.
   */
  def create[TNode](
    prefixTableSize: Int,
    nameTableSize: Int,
    datatypeTableSize: Int,
    appender: RowBufferAppender,
  ): NodeEncoder[TNode] = new NodeEncoderImpl[TNode](
    prefixTableSize,
    nameTableSize,
    datatypeTableSize,
    // Make the node cache size between 256 and 1024, depending on the user's maxNameTableSize.
    Math.max(Math.min(nameTableSize, 1024), 256),
    nameTableSize,
    Math.max(Math.min(nameTableSize, 1024), 256),
    appender
  )
