package eu.ostrzyciel.jelly.core.internal

import eu.ostrzyciel.jelly.core.proto.v1.RdfLookupEntryRowValue

/**
 * Internal trait for appending lookup entries to the row buffer.
 *
 * This is used by NodeEncoder.
 */
private[core] trait RowBufferAppender:
  private[core] def appendLookupEntry(entry: RdfLookupEntryRowValue): Unit
