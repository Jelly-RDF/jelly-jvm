package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.proto.RdfStreamRow
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import eu.ostrzyciel.jelly.core.helpers.Mrl.Statement
import eu.ostrzyciel.jelly.core.proto.RdfStreamRow

object Assertions extends AnyWordSpec, Matchers:
  def assertEncoded(observed: Seq[RdfStreamRow], expected: Seq[RdfStreamRow.Row]): Unit =
    for ix <- 0 until observed.size.max(expected.size) do
      val obsRow = observed.applyOrElse(ix, null)
      withClue(s"Row $ix:") {
        obsRow.row should be (expected.applyOrElse(ix, null))
      }

  def assertDecoded(observed: Seq[Statement], expected: Seq[Statement]): Unit =
    for ix <- 0 until observed.size.max(expected.size) do
      val obsRow = observed.applyOrElse(ix, null)
      withClue(s"Row $ix:") {
        obsRow should be (expected.applyOrElse(ix, null))
      }