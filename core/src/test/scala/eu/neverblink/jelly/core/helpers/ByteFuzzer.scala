package eu.neverblink.jelly.core.helpers

import scala.collection.mutable
import scala.util.Random

/** Byte-level mutational fuzzing of serialized messages.
  *
  * Here we try to produce input that still parses as protobuf but says nonsense. We check if the
  * decoder properly catches any errors and does not OOM.
  *
  * Runs are seeded, so a finding can be reproduced from the seed and iteration.
  */
object ByteFuzzer:
  private val maxReported = 5

  private val maxSize = 1 << 16

  /** Applies 1--4 random edits to a copy of the input. */
  def mutate(input: Array[Byte], rnd: Random): Array[Byte] =
    var bytes = input
    for _ <- 0 to rnd.nextInt(4) do
      val edited = edit(bytes, rnd)
      if edited.length <= maxSize then bytes = edited
    bytes

  private def edit(bytes: Array[Byte], rnd: Random): Array[Byte] =
    if bytes.isEmpty then bytes
    else
      rnd.nextInt(6) match
        case 0 =>
          // Flip one bit
          val out = bytes.clone
          val i = rnd.nextInt(out.length)
          out(i) = (out(i) ^ (1 << rnd.nextInt(8))).toByte
          out
        case 1 =>
          // Replace one byte with a random value
          val out = bytes.clone
          out(rnd.nextInt(out.length)) = rnd.nextInt(256).toByte
          out
        case 2 =>
          // Replace one byte with one that means something on the wire: varint edges, a
          // continuation bit, and the tag of field 1
          val out = bytes.clone
          out(rnd.nextInt(out.length)) = interesting(rnd.nextInt(interesting.length))
          out
        case 3 =>
          // Cut the message short
          bytes.take(rnd.nextInt(bytes.length))
        case 4 =>
          // Repeat a slice, so repeated fields and submessages get longer
          val from = rnd.nextInt(bytes.length)
          val to = from + rnd.nextInt(bytes.length - from) + 1
          bytes.take(to) ++ bytes.slice(from, to) ++ bytes.drop(to)
        case _ =>
          // Splice in bytes that were never there
          val at = rnd.nextInt(bytes.length)
          bytes.take(at) ++ Array.fill(1 + rnd.nextInt(4))(rnd.nextInt(256).toByte) ++
            bytes.drop(at)

  private val interesting: Array[Byte] =
    Array(0x00, 0x01, 0x7f, 0x80.toByte, 0xff.toByte, 0x0a)

  /** Feeds mutated copies of the corpus to `consume`.
    *
    * @return
    *   one description per finding, with enough to reproduce it
    */
  def findings(
      corpus: Seq[Array[Byte]],
      iterations: Int,
      seed: Long,
      isExpected: Throwable => Boolean,
  )(consume: Array[Byte] => Unit): Seq[String] =
    val rnd = Random(seed)
    val found = mutable.ListBuffer.empty[String]
    var count = 0
    for i <- 0 until iterations do
      val bytes = mutate(corpus(rnd.nextInt(corpus.size)), rnd)
      try consume(bytes)
      catch
        case t: Throwable if isExpected(t) => ()
        case t: Throwable =>
          count += 1
          if found.size < maxReported then found += describe(seed, i, bytes, t)
    if count > found.size then found += s"...and ${count - found.size} more"
    found.toSeq

  private def describe(seed: Long, iteration: Int, bytes: Array[Byte], t: Throwable): String =
    val hex = bytes.take(96).map(b => f"${b & 0xff}%02x").mkString
    val ellipsis = if bytes.length > 96 then s"... (${bytes.length} bytes)" else ""
    val at = Option(t.getStackTrace).flatMap(_.headOption).map(f => s"\n    at $f").getOrElse("")
    s"seed $seed, iteration $iteration: ${t.getClass.getName}: ${t.getMessage}$at" +
      s"\n    input: $hex$ellipsis"
