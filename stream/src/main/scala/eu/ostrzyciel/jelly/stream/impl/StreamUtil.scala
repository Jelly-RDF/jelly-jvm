package eu.ostrzyciel.jelly.stream.impl

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.FlowShape
import org.apache.pekko.stream.scaladsl.*

import scala.reflect.ClassTag

private[stream] object StreamUtil:
  def divertByTypeAndMerge[TIn1 : ClassTag, TIn2, TOut](
    f1: Flow[TIn1, TOut, NotUsed],
    f2: Flow[TIn2, TOut, NotUsed]
  ): Flow[TIn1 | TIn2, TOut, NotUsed] =
    Flow.fromGraph(GraphDSL.createGraph(f1, f2)(Tuple2.apply) {
      implicit builder => (f1, f2) =>
        import GraphDSL.Implicits.*
        val broadcast = builder.add(Broadcast[TIn1 | TIn2](2))
        val merge = builder.add(Merge[TOut](2))
        broadcast.out(0) ~> typeFilter[TIn1 | TIn2, TIn1] ~> f1 ~> merge
        // We don't want to require a classtag for TIn2, so instead we filter by negation
        broadcast.out(1) ~> inverseTypeFilter[TIn1 | TIn2, TIn1, TIn2] ~> f2 ~> merge
        FlowShape(broadcast.in, merge.out)
    }).mapMaterializedValue(_ => NotUsed)

  def typeFilter[TIn, TOut <: TIn : ClassTag]: Flow[TIn, TOut, NotUsed] =
    Flow[TIn].collect { case x: TOut => x }

  def inverseTypeFilter[TIn, TNotOut <: TIn : ClassTag, TOut <: TIn]: Flow[TIn, TOut, NotUsed] =
    Flow[TIn].mapConcat {
      case x: TNotOut => Nil
      case x => x.asInstanceOf[TOut] :: Nil
    }
