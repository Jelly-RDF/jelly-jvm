package eu.ostrzyciel.jelly.integration_tests

import eu.ostrzyciel.jelly.convert.jena.JenaConverterFactory
import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.{future, v1}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Tests checking forward compatibility of Jelly with future versions of the protocol.
 */
class ForwardCompatSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  private val futureFrame = future.RdfStreamFrame(Seq(
    future.RdfStreamRow(future.RdfStreamRow.Row.Options(
      future.RdfStreamOptions(
        physicalType = future.PhysicalStreamType.FUTURE,
        logicalType = future.LogicalStreamType.FUTURE,
        rdfStar = true,
        generalizedStatements = false,
        maxNameTableSize = 1000,
        maxPrefixTableSize = 200,
        maxDatatypeTableSize = 32,
        usesFutureFeatures = true,
        version = 123,
      )
    )),
    future.RdfStreamRow(future.RdfStreamRow.Row.GraphEndFuture(
      future.RdfGraphEnd()
    )),
    future.RdfStreamRow(future.RdfStreamRow.Row.GraphEndFuture(
      future.RdfGraphEnd()
    )),
  ))
  // Second test case: the options are supported by this version of Jelly,
  // but the protocol version is not.
  private val futureFrame2 = future.RdfStreamFrame(Seq(
    future.RdfStreamRow(future.RdfStreamRow.Row.Options(
      future.RdfStreamOptions(
        physicalType = future.PhysicalStreamType.TRIPLES,
        logicalType = future.LogicalStreamType.FLAT_TRIPLES,
        maxNameTableSize = 1000,
        maxPrefixTableSize = 200,
        maxDatatypeTableSize = 32,
        version = 123,
      )
    )),
  ))
  private val futureFrameBytes: Array[Byte] = futureFrame.toByteArray
  private val futureFrameBytes2: Array[Byte] = futureFrame2.toByteArray

  "current Jelly version" should {
    "be 1" in {
      // If this test is failing, it means that you have to update this spec :)
      // Go to integration-tests/src/main/protobuf and update the proto file to what you are using now.
      // Then, reintroduce the "future" changes that are tested here.
      // You can then update this test to the version number you are using.
      Constants.protoVersion should be (1)
    }
  }

  "v1.RdfStreamFrame" should {
    "parse a future stream frame" in {
      val parsed: v1.RdfStreamFrame = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
      parsed.rows.size should be (3)

      parsed.rows.head.row.isOptions should be (true)
      val options: v1.RdfStreamOptions = parsed.rows.head.row.options
      options.physicalType should be (v1.PhysicalStreamType.Unrecognized(
        future.PhysicalStreamType.FUTURE.value
      ))
      options.logicalType should be (v1.LogicalStreamType.Unrecognized(
        future.LogicalStreamType.FUTURE.value
      ))
      options.rdfStar should be (true)
      options.generalizedStatements should be (false)
      options.maxNameTableSize should be (1000)
      options.maxPrefixTableSize should be (200)
      options.maxDatatypeTableSize should be (32)
      options.version should be (123)

      parsed.rows(1).row.isEmpty should be (true)
      parsed.rows(2).row.isEmpty should be (true)
    }
  }

  "JellyOptions" should {
    "reject a future proto as incompatible" in {
      val parsed: v1.RdfStreamFrame = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
      val options: v1.RdfStreamOptions = parsed.rows.head.row.options
      val error = intercept[RdfProtoDeserializationError] {
        JellyOptions.checkCompatibility(options, JellyOptions.defaultSupportedOptions)
      }
      error.getMessage should include ("Unsupported proto version: 123")
    }
  }

  "ProtoDecoder (triples)" should {
    "reject future proto with a non-matching physical type" in {
      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
      val decoder = JenaConverterFactory.triplesDecoder()
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(parsed.rows.head)
      }
      error.getMessage should include ("Incoming stream type is not TRIPLES")
    }

    "reject future proto due to too high version" in {
      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes2)
      val decoder = JenaConverterFactory.triplesDecoder()
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(parsed.rows.head)
      }
      error.getMessage should include ("Unsupported proto version: 123")
    }
  }

  "ProtoDecoder (anyStatement)" should {
    "reject future proto due to too high version" in {
      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
      val decoder = JenaConverterFactory.anyStatementDecoder()
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(parsed.rows.head)
      }
      error.getMessage should include("Unsupported proto version: 123")
    }
  }
