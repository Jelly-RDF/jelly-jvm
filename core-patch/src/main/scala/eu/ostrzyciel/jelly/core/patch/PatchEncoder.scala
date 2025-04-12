package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.internal.*
import eu.ostrzyciel.jelly.core.patch.handler.{AnyPatchHandler, PatchHandler}
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*

import scala.annotation.experimental
import scala.collection.mutable

@experimental
object PatchEncoder:
  /**
   * Parameters passed to the Jelly-Patch encoder.
   * @param options options for this patch stream
   * @param rowBuffer buffer for storing patch rows. The encoder will append the RdfPatchRows to
   *                  this buffer. The caller is responsible for managing this buffer and grouping
   *                  the rows in RdfPatchFrames.
   */
  final case class Params(
    options: RdfPatchOptions,
    rowBuffer: mutable.Buffer[RdfPatchRow],
  )

/**
 * Encoder for RDF-Patch streams.
 *
 * See [[PatchHandler]] for the basic operations that can be performed on the patch stream.
 *
 * @tparam TNode type of RDF nodes in the library
 * @since 2.11.0
 */
@experimental
trait PatchEncoder[TNode >: Null] extends
  ProtoEncoderBase[TNode, ?, ?],
  AnyPatchHandler[TNode],
  RowBufferAppender:

  /**
   * RdfPatchOptions for this encoder.
   */
  val options: RdfPatchOptions
