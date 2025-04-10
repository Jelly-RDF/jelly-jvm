package eu.ostrzyciel.jelly.core.patch.helpers

import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.patch.PatchConverterFactory

import scala.annotation.experimental

@experimental
object MockPatchConverterFactory extends PatchConverterFactory(MockConverterFactory)
