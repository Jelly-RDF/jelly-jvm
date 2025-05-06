//package eu.neverblink.jelly.integration_tests.rdf
//
//import com.google.protobuf.ByteString
//import eu.neverblink.jelly.convert.jena.JenaConverterFactory
//import eu.neverblink.jelly.convert.jena.traits.JenaTest
//import eu.neverblink.jelly.core.*
//import eu.neverblink.jelly.core.proto.{future, v1}
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AnyWordSpec
//
///**
// * Tests checking forward compatibility of Jelly with future versions of the protocol.
// */
//class ForwardCompatSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
//  private val futureFrame = future.RdfStreamFrame(Seq(
//    future.RdfStreamRow(future.RdfStreamRow.Row.Options(
//      future.RdfStreamOptions(
//        physicalType = future.PhysicalStreamType.FUTURE,
//        logicalType = future.LogicalStreamType.FUTURE,
//        rdfStar = true,
//        generalizedStatements = false,
//        maxNameTableSize = 1000,
//        maxPrefixTableSize = 200,
//        maxDatatypeTableSize = 32,
//        usesFutureFeatures = true,
//        version = 123,
//      )
//    )),
//    future.RdfStreamRow(future.RdfStreamRow.Row.GraphEndFuture(
//      future.RdfGraphEnd()
//    )),
//    future.RdfStreamRow(future.RdfStreamRow.Row.GraphEndFuture(
//      future.RdfGraphEnd()
//    )),
//  ))
//  // Second test case: the options are supported by this version of Jelly,
//  // but the protocol version is not.
//  private val futureFrame2 = future.RdfStreamFrame(Seq(
//    future.RdfStreamRow(future.RdfStreamRow.Row.Options(
//      future.RdfStreamOptions(
//        physicalType = future.PhysicalStreamType.TRIPLES,
//        logicalType = future.LogicalStreamType.FLAT_TRIPLES,
//        maxNameTableSize = 1000,
//        maxPrefixTableSize = 200,
//        maxDatatypeTableSize = 32,
//        version = 123,
//      )
//    )),
//  ))
//  // Third test case: everything is supported by this version of Jelly,
//  // only a new metadata field is added to RdfStreamFrame.
//  // This tests adding a feature like the one added in Jelly-RDF 1.1.1:
//  // https://github.com/Jelly-RDF/jelly-protobuf/issues/32
//  private val futureFrame3 = future.RdfStreamFrame(
//    Seq(future.RdfStreamRow(future.RdfStreamRow.Row.Options(
//      future.RdfStreamOptions(
//        physicalType = future.PhysicalStreamType.TRIPLES,
//        logicalType = future.LogicalStreamType.FLAT_TRIPLES,
//        maxNameTableSize = 1000,
//        maxPrefixTableSize = 200,
//        maxDatatypeTableSize = 32,
//        version = 2,
//      )
//    ))),
//    futureMetadata = Map(
//      "key" -> ByteString.copyFromUtf8("value"),
//      "key2" -> ByteString.copyFrom(Array.ofDim[Byte](100))
//    ),
//  )
//  private val futureFrameBytes: Array[Byte] = futureFrame.toByteArray
//  private val futureFrameBytes2: Array[Byte] = futureFrame2.toByteArray
//  private val futureFrameBytes3: Array[Byte] = futureFrame3.toByteArray
//
//  "current Jelly version" should {
//    "be 2" in {
//      // If this test is failing, it means that you have to update this spec :)
//      // Go to integration-tests/src/main/protobuf and update the proto file to what you are using now.
//      // Then, reintroduce the "future" changes that are tested here.
//      // You can then update this test to the version number you are using.
//      Constants.protoVersion should be (2)
//    }
//  }
//
//  "v1.RdfStreamFrame" should {
//    "parse a future stream frame" in {
//      val parsed: v1.RdfStreamFrame = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
//      parsed.getRows.asScala.size should be (3)
//
//      parsed.getRows.asScala.head.hasOptions should be (true)
//      val options: v1.RdfStreamOptions = parsed.getRows.asScala.head.options
//      options.physicalType should be (v1.PhysicalStreamType.Unrecognized(
//        future.PhysicalStreamType.FUTURE.value
//      ))
//      options.logicalType should be (v1.LogicalStreamType.Unrecognized(
//        future.LogicalStreamType.FUTURE.value
//      ))
//      options.rdfStar should be (true)
//      options.generalizedStatements should be (false)
//      options.maxNameTableSize should be (1000)
//      options.maxPrefixTableSize should be (200)
//      options.maxDatatypeTableSize should be (32)
//      options.version should be (123)
//
//      parsed.getRows.asScala(1).row should be (null)
//      parsed.getRows.asScala(2).row should be (null)
//    }
//  }
//
//  "JellyOptions" should {
//    "reject a future proto as incompatible" in {
//      val parsed: v1.RdfStreamFrame = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
//      val options: v1.RdfStreamOptions = parsed.getRows.asScala.head.options
//      val error = intercept[RdfProtoDeserializationError] {
//        JellyOptions.checkCompatibility(options, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
//      }
//      error.getMessage should include ("Unsupported proto version: 123")
//    }
//  }
//
//  "ProtoDecoder (triples)" should {
//    "reject future proto with a non-matching physical type" in {
//      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
//      val decoder = JenaConverterFactory.triplesDecoder()
//      val error = intercept[RdfProtoDeserializationError] {
//        decoder.ingestRow(parsed.getRows.asScala.head)
//      }
//      error.getMessage should include ("Incoming stream type is not TRIPLES")
//    }
//
//    "reject future proto due to too high version" in {
//      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes2)
//      val decoder = JenaConverterFactory.triplesDecoder()
//      val error = intercept[RdfProtoDeserializationError] {
//        decoder.ingestRow(parsed.getRows.asScala.head)
//      }
//      error.getMessage should include ("Unsupported proto version: 123")
//    }
//  }
//
//  "ProtoDecoder (anyStatement)" should {
//    "reject future proto due to too high version" in {
//      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
//      val decoder = JenaConverterFactory.anyStatementDecoder()
//      val error = intercept[RdfProtoDeserializationError] {
//        decoder.ingestRow(parsed.getRows.asScala.head)
//      }
//      error.getMessage should include("Unsupported proto version: 123")
//    }
//  }
//
//  "RdfStreamFrame" should {
//    "ignore unknown metadata field" in {
//      // This tests forward compat with changes like those introduced in Jelly-RDF 1.1.1:
//      // https://github.com/Jelly-RDF/jelly-protobuf/issues/32
//      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes3)
//      parsed.getRows.asScala.size should be (1)
//      parsed.getRows.asScala.head.hasOptions should be (true)
//    }
//  }
