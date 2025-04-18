package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.helpers.Mrl.Statement
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamRow, RdfStreamRowValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object Assertions extends AnyWordSpec, Matchers:
  def assertEncoded(observed: Seq[RdfStreamRow], expected: Seq[RdfStreamRowValue]): Unit =
    for ix <- 0 until observed.size.min(expected.size) do
      val obsRow = observed.applyOrElse(ix, null)
      withClue(s"Row $ix:") {
        obsRow.row should be (expected.applyOrElse(ix, null))
      }
    observed.size should be(expected.size)

  def assertDecoded(observed: Seq[Statement], expected: Seq[Statement]): Unit =
    for ix <- 0 until observed.size.min(expected.size) do
      val obsRow = observed.applyOrElse(ix, null)
      withClue(s"Row $ix:") {
        obsRow should be (expected.applyOrElse(ix, null))
      }
    observed.size should be (expected.size)
