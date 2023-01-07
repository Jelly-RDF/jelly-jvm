package pl.ostrzyciel.jelly.convert.jena.riot

import org.apache.jena.riot.{LangBuilder, RDFLanguages, RDFParserRegistry}

object JellyLanguage:
  val contentTypeJelly = ""
  val strLangJelly = "Jelly"

  val JELLY = LangBuilder.create(strLangJelly, contentTypeJelly)
    .addAltNames("JELLY")
    .addFileExtensions("jelly")
    .build

  // Registration
  RDFLanguages.register(JELLY)
  RDFParserRegistry.registerLangTriples(JELLY, JellyReaderFactory)
  RDFParserRegistry.registerLangQuads(JELLY, JellyReaderFactory)



