package eu.neverblink.jelly.integration_tests.util

import org.apache.jena.Jena

object CompatibilityUtils:
  lazy val jenaVersion54OrHigher: Boolean = {
    val split = Jena.VERSION.split('.')
    split(0).toInt > 5 || split(1).toInt >= 4
  }
