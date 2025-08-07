package eu.neverblink.jelly.core.patch.helpers

import eu.neverblink.jelly.core.helpers.MockConverterFactory
import eu.neverblink.jelly.core.helpers.MockProtoEncoderConverter
import eu.neverblink.jelly.core.helpers.MockProtoDecoderConverter
import eu.neverblink.jelly.core.helpers.Mrl.{Node, Datatype}
import eu.neverblink.jelly.core.patch.JellyPatchConverterFactory

import scala.annotation.experimental

@experimental
object MockPatchConverterFactory
    extends JellyPatchConverterFactory[
      Node,
      Datatype,
      MockProtoEncoderConverter,
      MockProtoDecoderConverter,
    ](MockConverterFactory)
