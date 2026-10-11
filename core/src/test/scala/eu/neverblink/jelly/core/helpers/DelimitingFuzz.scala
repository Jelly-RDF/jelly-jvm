package eu.neverblink.jelly.core.helpers

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.Descriptors.Descriptor
import eu.neverblink.jelly.core.utils.IoUtils.AutodetectDelimitingResponse

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/** Fuzzing of the detection of delimited streams, for any frame type. */
object DelimitingFuzz:
  /** A case the detection got wrong, or that lost bytes. */
  final case class Failure(what: String, bytes: Array[Byte]):
    override def toString: String =
      s"$what: ${bytes.take(40).map(b => f"${b & 0xff}%02x").mkString(" ")}" +
        (if bytes.length > 40 then s" … (${bytes.length} bytes)" else "")

  final case class Result(failures: Seq[Failure], cases: Int, ambiguous: Int)

  /** Frames written non-delimited must be detected as non-delimited, and the stream returned by the
    * detection must still contain every byte.
    */
  def nonDelimited(
      frame: Descriptor,
      detect: InputStream => AutodetectDelimitingResponse,
      iterations: Int,
      seed: Long,
  ): Result =
    val rnd = Random(seed)
    val tags = frameTags(frame)
    val failures = ArrayBuffer[Failure]()
    var ambiguous = 0
    for _ <- 0 until iterations do
      val bytes = WireFuzzer.message(frame, rnd)
      if bytes.nonEmpty && tags(bytes(0) & 0xff) then ambiguous += 1
      check(bytes, expected = false, detect).foreach(failures += _)
    Result(failures.toSeq, iterations, ambiguous)

  /** Delimited streams of 1–3 frames must be detected as delimited, with every byte kept. */
  def delimited(
      frame: Descriptor,
      detect: InputStream => AutodetectDelimitingResponse,
      iterations: Int,
      seed: Long,
  ): Result =
    val rnd = Random(seed)
    val tags = frameTags(frame)
    val failures = ArrayBuffer[Failure]()
    var ambiguous = 0
    for _ <- 0 until iterations do
      val bytes = ByteArrayOutputStream()
      val out = CodedOutputStream.newInstance(bytes)
      for _ <- 0 to rnd.nextInt(3) do
        val f = WireFuzzer.message(frame, rnd)
        out.writeUInt32NoTag(f.length)
        out.writeRawBytes(f)
      out.flush()
      val stream = bytes.toByteArray
      if tags(stream(0) & 0xff) then ambiguous += 1
      check(stream, expected = true, detect).foreach(failures += _)
    Result(failures.toSeq, iterations, ambiguous)

  /** Completely random byts: the detection must not throw anything but an IOException, and must
    * keep every byte.
    */
  def randomBytes(
      detect: InputStream => AutodetectDelimitingResponse,
      iterations: Int,
      seed: Long,
  ): Result =
    val rnd = Random(seed)
    val failures = ArrayBuffer[Failure]()
    for _ <- 0 until iterations do
      val length = if rnd.nextInt(10) == 0 then rnd.nextInt(400) else rnd.nextInt(24)
      val bytes = Array.fill(length)(rnd.nextInt(256).toByte)
      try
        val response = detect(ByteArrayInputStream(bytes))
        if !(response.newInput.readAllBytes() sameElements bytes) then
          failures += Failure("bytes lost", bytes)
      catch
        case _: java.io.IOException => ()
        case e: Throwable => failures += Failure(s"threw $e", bytes)
    Result(failures.toSeq, iterations, 0)

  private def check(
      bytes: Array[Byte],
      expected: Boolean,
      detect: InputStream => AutodetectDelimitingResponse,
  ): Option[Failure] =
    val response = detect(ByteArrayInputStream(bytes))
    if response.isDelimited != expected then
      Some(
        Failure(
          if expected then "delimited taken as non-delimited"
          else "non-delimited taken as delimited",
          bytes,
        ),
      )
    else if !(response.newInput.readAllBytes() sameElements bytes) then
      Some(Failure("bytes lost", bytes))
    else None

  /** The single-byte tags of the frame's fields, as in IoUtils. */
  private def frameTags(frame: Descriptor): Array[Boolean] =
    import com.google.protobuf.Descriptors.FieldDescriptor.Type
    import scala.jdk.CollectionConverters.*
    val tags = Array.fill(256)(false)
    for f <- frame.getFields.asScala if f.getNumber <= 15 do
      val wire = f.getType match
        case Type.MESSAGE | Type.STRING | Type.BYTES | Type.GROUP => 2
        case Type.DOUBLE | Type.FIXED64 | Type.SFIXED64 => 1
        case Type.FLOAT | Type.FIXED32 | Type.SFIXED32 => 5
        case _ => 0
      tags((f.getNumber << 3) | wire) = true
      if f.isRepeated then tags((f.getNumber << 3) | 2) = true
    tags
