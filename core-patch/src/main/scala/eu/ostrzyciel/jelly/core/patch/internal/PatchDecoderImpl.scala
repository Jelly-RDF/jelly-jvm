package eu.ostrzyciel.jelly.core.patch.internal

import eu.ostrzyciel.jelly.core.internal.NameDecoder
import eu.ostrzyciel.jelly.core.ProtoDecoderConverter
import eu.ostrzyciel.jelly.core.patch.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.RdfPatchOptions

import scala.reflect.ClassTag

sealed abstract class PatchDecoderImpl[TNode, TDatatype : ClassTag](
  converter: ProtoDecoderConverter[TNode, TDatatype, ?, ?],
  handler: PatchHandler[TNode],
  supportedOptions: RdfPatchOptions,
) extends PatchDecoder:
  private var streamOpt: Option[RdfPatchOptions] = None
  private lazy val nameDecoder = {
    val opt = streamOpt getOrElse JellyPatchOptions.smallStrict
    NameDecoder(opt.maxPrefixTableSize, opt.maxNameTableSize, converter.makeIriNode)
  }

  final override def getPatchOpt: Option[RdfPatchOptions] = ???

object PatchDecoderImpl:
  ???
