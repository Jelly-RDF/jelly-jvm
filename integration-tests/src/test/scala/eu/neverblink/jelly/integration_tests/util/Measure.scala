package eu.neverblink.jelly.integration_tests.util

/** A thing that can measure how many statements a model or dataset has.
  * @tparam T
  *   type of the thing to measure
  */
trait Measure[T]:
  def size(x: T): Long
