package eu.ostrzyciel.jelly.core.patch.helpers

import eu.ostrzyciel.jelly.core.proto.v1.patch.RdfPatchRow
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object Assertions extends AnyWordSpec, Matchers:
  def assertEncoded(observed: Seq[RdfPatchRow], expected: Seq[RdfPatchRow]): Unit =
    for ix <- 0 until observed.size.min(expected.size) do
      val obsRow = observed.applyOrElse(ix, null)
      withClue(s"Row $ix:") {
        obsRow should be (expected.applyOrElse(ix, null))
      }
    observed.size should be (expected.size)
