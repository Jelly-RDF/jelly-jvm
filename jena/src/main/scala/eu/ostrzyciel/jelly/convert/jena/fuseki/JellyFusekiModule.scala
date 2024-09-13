package eu.ostrzyciel.jelly.convert.jena.fuseki

import org.apache.jena.fuseki.DEF
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.main.sys.FusekiModule
import org.apache.jena.rdf.model.Model
import org.apache.jena.sys.JenaSystem

import java.util

class JellyFusekiModule extends FusekiModule:

  override def name(): String = "Jelly"

  override def prepare(serverBuilder: FusekiServer.Builder, datasetNames: util.Set[String], configModel: Model): Unit =
    // TODO:
    // if defined DEF.constructOfferDefault()
    // and Jelly not currently registered there
    // then register Jelly
