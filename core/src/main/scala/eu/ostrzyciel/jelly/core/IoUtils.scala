package eu.ostrzyciel.jelly.core

import java.io.{ByteArrayInputStream, InputStream, SequenceInputStream}

object IoUtils:
  /**
   * Autodetects whether the input stream is a non-delimited Jelly file or a delimited Jelly file.
   *
   * To do this, the first three bytes in the stream are peeked.
   * These bytes are then put back into the stream, and the stream is returned, so the parser won't notice the peeking.
   * @param in the input stream
   * @return (isDelimited, newInputStream) where isDelimited is true if the stream is a delimited Jelly file
   */
  def autodetectDelimiting(in: InputStream): (Boolean, InputStream) =
    val scout = in.readNBytes(3)
    val scoutIn = ByteArrayInputStream(scout)
    val newInput = SequenceInputStream(scoutIn, in)
    // Truth table (notation: 0A = 0x0A, NN = not 0x0A, ?? = don't care):
    // NN ?? ?? -> delimited (all non-delimited start with 0A)
    // 0A NN ?? -> non-delimited
    // 0A 0A NN -> delimited (total message size = 10)
    // 0A 0A 0A -> non-delimited (stream options size = 10)
    //
    // A case like "0A 0A 0A 0A" in the delimited variant is impossible. It would mean that the whole message
    // is 10 bytes long, while stream options alone are 10 bytes long.
    // Yeah, it's magic. But it works.
    val isDelimited = (
      scout(0) != 0x0A || scout(1) == 0x0A && scout(2) != 0x0A
    )
    (isDelimited, newInput)
