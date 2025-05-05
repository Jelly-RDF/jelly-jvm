package eu.neverblink.jelly.core.patch.helpers

import eu.neverblink.jelly.core.proto.v1.patch.{RdfPatchFrame, RdfPatchRow}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

@experimental
object Assertions extends AnyWordSpec, Matchers:
  def assertEncoded(observed: Seq[RdfPatchRow], expected: Seq[RdfPatchRow]): Unit =
    for ix <- 0 until observed.size.min(expected.size) do
      val obsRow = observed.applyOrElse(ix, null)
      withClue(s"Row $ix:") {
        obsRow should be (expected.applyOrElse(ix, null))
      }
    observed.size should be (expected.size)

  def assertEncodedFrame(observed: RdfPatchFrame, expected: RdfPatchFrame): Unit = assertEncoded(
    observed.getRows.asScala.toSeq,
    expected.getRows.asScala.toSeq
  )

  def assertEncodedFrame(observed: Seq[RdfPatchFrame], expected: Seq[RdfPatchFrame]): Unit =
    for ix <- 0 until observed.size.min(expected.size) do
      val obsFrame = observed.applyOrElse(ix, null)
      withClue(s"Frame $ix:") {
        assertEncodedFrame(obsFrame, expected.applyOrElse(ix, null))
      }
    observed.size should be (expected.size)
