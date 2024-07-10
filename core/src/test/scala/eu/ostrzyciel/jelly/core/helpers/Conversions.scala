package eu.ostrzyciel.jelly.core.helpers

import com.google.protobuf.ByteString

object Conversions:
  given string2ByteString: Conversion[String, ByteString] = str => ByteString.copyFromUtf8(str)
