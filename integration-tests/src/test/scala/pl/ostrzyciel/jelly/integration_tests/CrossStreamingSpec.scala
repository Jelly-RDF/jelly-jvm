package pl.ostrzyciel.jelly.integration_tests

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.*
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFParser}
import org.apache.jena.riot.system.{AsyncParser, StreamRDFLib}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.ostrzyciel.jelly.core.*
import pl.ostrzyciel.jelly.core.proto.RdfStreamFrame
import pl.ostrzyciel.jelly.stream.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream, InputStream}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

class CrossStreamingSpec extends AnyWordSpec, Matchers, ScalaFutures:
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = actorSystem.getDispatcher

  // TODO: getQuadFlow, same but for decoding, RDF4J impl, pass encoder options and wiggle them
  // TODO: grouped flows
  // TODO tests: check end-to-end, check if both RDF4J and Jena encode it the same way
  // TODO test cases: triples, quads, literals, bnodes (ughhh), RDF-star, weird RDF-star

  val implementations: Seq[(String, TestStream)] = Seq(
    ("Jena", JenaTestStream),
    ("RDF4J", Rdf4jTestStream),
  )

  val testFiles = Seq(
    "weather.nt",
  ).map(name => (
    name, File(getClass.getResource("/" + name).toURI)
  ))

  for (caseName, sourceFile) <- testFiles do
    val sourceGraph = RDFDataMgr.loadGraph(sourceFile.toURI.toString)

    for (encName, encFlow) <- implementations do
      for (decName, decFlow) <- implementations do
        s"$encName encoder" when {
          s"streaming to $decName decoding" should {
            s"stream file $caseName" in {
              val is = new FileInputStream(sourceFile)
              val os = new ByteArrayOutputStream()
              encFlow.tripleSource(is, EncoderFlow.Options(), JellyOptions.smallGeneralized)
                .toMat(decFlow.tripleSink(os))(Keep.right)
                .run()
                .futureValue

              val resultGraph = RDFParser.source(new ByteArrayInputStream(os.toByteArray))
                .lang(Lang.NT)
                .toGraph

              sourceGraph.size() should be (resultGraph.size())
              sourceGraph.isIsomorphicWith(resultGraph) should be (true)
            }

        }
      }



//  def getEncoderFlow[TEncoder <: ProtoEncoder[?, TTriple, TQuad, ?], TTriple, TQuad]
//  (factory: ConverterFactory[TEncoder, ?, TTriple, TQuad]): Flow[String, RdfStreamFrame, NotUsed] =
//    ???

//  def runTest[TEncoder <: ProtoEncoder[?, ?, ?, ?], TDecoder <: ProtoDecoder[?, ?, ?, ?]]
//  (encFactory: ConverterFactory[TEncoder, ?, ?, ?], decFactory: ConverterFactory[?, TDecoder, ?, ?]) =
//    ???