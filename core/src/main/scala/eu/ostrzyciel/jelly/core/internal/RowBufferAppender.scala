package eu.ostrzyciel.jelly.core.internal

import eu.ostrzyciel.jelly.core.proto.v1.*

/**
 * Internal trait for appending lookup entries to the row buffer.
 *
 * This is used by NodeEncoder.
 */
private[core] trait RowBufferAppender:
  private[core] def appendNameEntry(entry: RdfNameEntry): Unit
  private[core] def appendPrefixEntry(entry: RdfPrefixEntry): Unit
  private[core] def appendDatatypeEntry(entry: RdfDatatypeEntry): Unit
