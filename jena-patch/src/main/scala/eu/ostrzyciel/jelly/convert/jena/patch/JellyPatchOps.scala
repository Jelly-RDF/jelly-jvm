package eu.ostrzyciel.jelly.convert.jena.patch

import eu.ostrzyciel.jelly.core.patch.handler.AnyPatchHandler
import org.apache.jena.graph.Node
import org.apache.jena.rdfpatch.RDFChanges

import scala.annotation.experimental

@experimental
object JellyPatchOps:
  import JenaPatchHandler.*

  def fromJellyToJena(jenaStream: RDFChanges): AnyPatchHandler[Node] = JellyToJena(jenaStream)

  def fromJenaToJelly(jellyStream: AnyPatchHandler[Node]): RDFChanges = JenaToJelly(jellyStream)

  // TODO: readers, writers, ...
