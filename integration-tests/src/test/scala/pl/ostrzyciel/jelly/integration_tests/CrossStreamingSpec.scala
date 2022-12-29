package pl.ostrzyciel.jelly.integration_tests

/*
import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source, StreamConverters}
import org.apache.jena.riot.Lang
import org.apache.jena.riot.system.{AsyncParser, StreamRDFLib}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.ostrzyciel.jelly.convert.jena.JenaConverterFactory
import pl.ostrzyciel.jelly.convert.rdf4j.Rdf4jConverterFactory
import pl.ostrzyciel.jelly.core.*
import pl.ostrzyciel.jelly.core.proto.RdfStreamFrame
import pl.ostrzyciel.jelly.stream.*

import java.io.InputStream
import scala.jdk.CollectionConverters.*

class CrossStreamingSpec extends AnyWordSpec, Matchers:
  sealed trait TestEncoderFlow:
    def getTripleFlow(in: InputStream): Source[RdfStreamFrame, NotUsed]

  case object JenaTestEncoderFlow extends TestEncoderFlow:
    override def getTripleFlow(in: InputStream) =
      Source.fromIterator(() => AsyncParser.asyncParseTriples(in, Lang.NT, "").asScala)
        .via(EncoderFlow.fromFlatTriples(
          JenaConverterFactory,
          EncoderFlow.Options(16, false),
          RdfStreamOptions()
        ))

  // TODO: getQuadFlow, same but for decoding, RDF4J impl, pass encoder options and wiggle them
  // TODO: grouped flows
  // TODO tests: check end-to-end, check if both RDF4J and Jena encode it the same way
  // TODO test cases: triples, quads, literals, bnodes (ughhh), RDF-star, weird RDF-star

  sealed trait TestDecoderFlow:
    def getFlow: Flow[RdfStreamFrame, String, NotUsed]

  case object JenaTestDecoderFlow extends TestDecoderFlow:
    override def getFlow =
      ???

  val encoders: Seq[(String, TestEncoderFlow)] = Seq(
    ("Jena", JenaTestEncoderFlow),
  )

  val decoders: Seq[(String, TestDecoderFlow)] = Seq(
    ("Jena", JenaTestDecoderFlow),
  )

  for (encName, encFlow) <- encoders do
    for (decName, decFlow) <- decoders do
      s"$encName encoder" when {
        s"streaming to $decName decoding" should {
          "bep" in {
            encFlow.getTripleFlow
          }
        }
      }

*/

//  def getEncoderFlow[TEncoder <: ProtoEncoder[?, TTriple, TQuad, ?], TTriple, TQuad]
//  (factory: ConverterFactory[TEncoder, ?, TTriple, TQuad]): Flow[String, RdfStreamFrame, NotUsed] =
//    ???

//  def runTest[TEncoder <: ProtoEncoder[?, ?, ?, ?], TDecoder <: ProtoDecoder[?, ?, ?, ?]]
//  (encFactory: ConverterFactory[TEncoder, ?, ?, ?], decFactory: ConverterFactory[?, TDecoder, ?, ?]) =
//    ???