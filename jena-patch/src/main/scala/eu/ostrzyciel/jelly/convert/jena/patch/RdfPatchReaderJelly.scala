package eu.ostrzyciel.jelly.convert.jena.patch

import eu.ostrzyciel.jelly.core.IoUtils
import eu.ostrzyciel.jelly.core.patch.JellyPatchOptions
import eu.ostrzyciel.jelly.core.proto.v1.patch.{RdfPatchFrame, RdfPatchOptions}
import org.apache.jena.rdfpatch.{PatchProcessor, RDFChanges}

import java.io.InputStream
import scala.annotation.experimental

object RdfPatchReaderJelly:
  final case class Options(
    supportedOptions: RdfPatchOptions = JellyPatchOptions.defaultSupportedOptions,
  )

@experimental
final class RdfPatchReaderJelly(opt: RdfPatchReaderJelly.Options, in: InputStream) extends PatchProcessor:

  def apply(destination: RDFChanges): Unit =
    val handler = JellyPatchOps.fromJellyToJena(destination)
    val decoder = JenaPatchConverterFactory.anyStatementDecoder(handler, Some(opt.supportedOptions))

    destination.start()
    try
      IoUtils.autodetectDelimiting(in) match
        case (false, newIn) =>
          // Non-delimited Jelly-Patch file, read only one frame
          decoder.ingestFrame(RdfPatchFrame.parseFrom(newIn))
        case (true, newIn) =>
          // Delimited Jelly-Patch file, we can read multiple frames
          Iterator.continually(RdfPatchFrame.parseDelimitedFrom(newIn))
            .takeWhile(_.isDefined)
            .foreach { maybeFrame => decoder.ingestFrame(maybeFrame.get) }
    finally destination.finish()
