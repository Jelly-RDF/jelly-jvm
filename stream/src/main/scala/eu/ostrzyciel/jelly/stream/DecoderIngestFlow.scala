package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.ConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.*

object DecoderIngestFlow:
  type InFlow = FlowOps[RdfStreamFrame, NotUsed]

  sealed abstract class DecoderIngestFlowOps(f: InFlow, streamType: RdfStreamType)

  private object DecoderIngestFlowOps:
    case class TriplesIngestFlowOps(f: InFlow) extends
      DecoderIngestFlowOps(f, RdfStreamType.TRIPLES),
      InterpretableAs.FlatTripleStream
    :
      def asFlatTripleStream[TTriple](using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]) =
        ???

    case class QuadsIngestFlowOps(f: InFlow) extends DecoderIngestFlowOps(f, RdfStreamType.QUADS)
    case class GraphsIngestFlowOps(f: InFlow) extends DecoderIngestFlowOps(f, RdfStreamType.GRAPHS)
    case class AnyIngestFlowOps(f: InFlow) extends DecoderIngestFlowOps(f, RdfStreamType.UNSPECIFIED)

  extension (f: InFlow)
    def decodeTriples = DecoderIngestFlowOps.TriplesIngestFlowOps(f)
    def decodeQuads = DecoderIngestFlowOps.QuadsIngestFlowOps(f)
    def decodeGraphs = DecoderIngestFlowOps.GraphsIngestFlowOps(f)
    def decodeAny = DecoderIngestFlowOps.AnyIngestFlowOps(f)

  object InterpretableAs:
    trait FlatTripleStream:
      def asFlatTripleStream[TTriple](using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
        FlowOps[TTriple, NotUsed]

    trait FlatQuadStream:
      def asFlatQuadStream[TQuad](using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
        FlowOps[TQuad, NotUsed]



