package eu.ostrzyciel.jelly.integration_tests.util

/**
 * A trait for comparing two objects of the same type in the context of a scalatest test.
 * @tparam T the type of the objects to compare
 */
trait TestComparable[T] extends Measure[T]:
  def compare(actual: T, expected: T): Unit
