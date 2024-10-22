package eu.ostrzyciel.jelly.convert.jena.traits

import org.apache.jena.sys.JenaSystem

object JenaTest:
  JenaSystem.init()

/**
 * Trait that should be included in all tests that use Jena.
 * Guarantees that Jena is initialized before the tests are run and that we don't run into wonky issues like
 * this one: https://github.com/apache/jena/issues/2787
 */
trait JenaTest:
  // Touch the object to run the static initializer
  JenaTest.toString
