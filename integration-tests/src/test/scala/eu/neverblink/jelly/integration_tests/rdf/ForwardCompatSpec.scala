package eu.neverblink.jelly.integration_tests.rdf

import com.google.protobuf.ByteString
import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.*
import eu.neverblink.jelly.core.proto.{future, v1}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

/** Tests checking forward compatibility of Jelly with future versions of the protocol.
  */
class ForwardCompatSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  private val futureFrame = future.RdfStreamFrame.newBuilder()
    .addRows(
      future.RdfStreamRow.newBuilder()
        .setOptions(
          future.RdfStreamOptions.newBuilder()
            .setPhysicalType(future.PhysicalStreamType.PHYSICAL_STREAM_TYPE_FUTURE)
            .setLogicalType(future.LogicalStreamType.LOGICAL_STREAM_TYPE_FUTURE)
            .setRdfStar(true)
            .setGeneralizedStatements(false)
            .setMaxNameTableSize(1000)
            .setMaxPrefixTableSize(200)
            .setMaxDatatypeTableSize(32)
            .setUsesFutureFeatures(true)
            .setVersion(123)
            .build(),
        )
        .build(),
    )
    .addRows(
      future.RdfStreamRow.newBuilder()
        .setGraphEndFuture(
          future.RdfGraphEnd.newBuilder()
            .build(),
        )
        .build(),
    )
    .addRows(
      future.RdfStreamRow.newBuilder()
        .setGraphEndFuture(
          future.RdfGraphEnd.newBuilder()
            .build(),
        )
        .build(),
    )
    .build()

  // Second test case: the options are supported by this version of Jelly,
  // but the protocol version is not.
  private val futureFrame2 = future.RdfStreamFrame.newBuilder()
    .addRows(
      future.RdfStreamRow.newBuilder()
        .setOptions(
          future.RdfStreamOptions.newBuilder()
            .setPhysicalType(future.PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
            .setLogicalType(future.LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES)
            .setRdfStar(true)
            .setGeneralizedStatements(false)
            .setMaxNameTableSize(1000)
            .setMaxPrefixTableSize(200)
            .setMaxDatatypeTableSize(32)
            .setUsesFutureFeatures(true)
            .setVersion(123)
            .build(),
        )
        .build(),
    )
    .build()

  // Third test case: everything is supported by this version of Jelly,
  // only a new metadata field is added to RdfStreamFrame.
  // This tests adding a feature like the one added in Jelly-RDF 1.1.1:
  // https://github.com/Jelly-RDF/jelly-protobuf/issues/32
  private val futureFrame3 = future.RdfStreamFrame.newBuilder()
    .addRows(
      future.RdfStreamRow.newBuilder()
        .setOptions(
          future.RdfStreamOptions.newBuilder()
            .setPhysicalType(future.PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
            .setLogicalType(future.LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES)
            .setRdfStar(true)
            .setGeneralizedStatements(false)
            .setMaxNameTableSize(1000)
            .setMaxPrefixTableSize(200)
            .setMaxDatatypeTableSize(32)
            .setUsesFutureFeatures(true)
            .setVersion(2)
            .build(),
        )
        .build(),
    )
    .putFutureMetadata("key", ByteString.copyFromUtf8("value"))
    .putFutureMetadata("key2", ByteString.copyFrom(Array.ofDim[Byte](100)))
    .build()

  private val futureFrameBytes: Array[Byte] = futureFrame.toByteArray
  private val futureFrameBytes2: Array[Byte] = futureFrame2.toByteArray
  private val futureFrameBytes3: Array[Byte] = futureFrame3.toByteArray

  "current Jelly version" should {
    "be 2" in {
      // If this test is failing, it means that you have to update this spec :)
      // Go to integration-tests/src/main/protobuf and update the proto file to what you are using now.
      // Then, reintroduce the "future" changes that are tested here.
      // You can then update this test to the version number you are using.
      JellyConstants.PROTO_VERSION should be(2)
    }
  }

  "v1.RdfStreamFrame" should {
    "parse a future stream frame" in {
      val parsed: v1.RdfStreamFrame = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
      parsed.getRows.asScala.size should be(3)

      parsed.getRows.asScala.head.hasOptions should be(true)
      val options: v1.RdfStreamOptions = parsed.getRows.asScala.head.getOptions
      options.getPhysicalType should be(v1.PhysicalStreamType.UNSPECIFIED)
      options.getLogicalType should be(v1.LogicalStreamType.UNSPECIFIED)
      options.getRdfStar should be(true)
      options.getGeneralizedStatements should be(false)
      options.getMaxNameTableSize should be(1000)
      options.getMaxPrefixTableSize should be(200)
      options.getMaxDatatypeTableSize should be(32)
      options.getVersion should be(123)

      parsed.getRows.asScala.toList(1).getRow should be(null)
      parsed.getRows.asScala.toList(2).getRow should be(null)
    }
  }

  "JellyOptions" should {
    "reject a future proto as incompatible" in {
      val parsed: v1.RdfStreamFrame = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
      val options: v1.RdfStreamOptions = parsed.getRows.asScala.head.getOptions
      val error = intercept[RdfProtoDeserializationError] {
        JellyOptions.checkCompatibility(options, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      }
      error.getMessage should include("Unsupported proto version: 123")
    }
  }

  "ProtoDecoder (triples)" should {
    "reject future proto with a non-matching physical type" in {
      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
      val decoder = JenaConverterFactory.getInstance().triplesDecoder(
        null,
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
      )
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(parsed.getRows.asScala.head)
      }
      error.getMessage should include("Incoming stream type is not TRIPLES")
    }

    "reject future proto due to too high version" in {
      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes2)
      val decoder = JenaConverterFactory.getInstance().triplesDecoder(
        null,
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
      )
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(parsed.getRows.asScala.head)
      }
      error.getMessage should include("Unsupported proto version: 123")
    }
  }

  "ProtoDecoder (anyStatement)" should {
    "reject future proto due to too high version" in {
      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes)
      val decoder = JenaConverterFactory.getInstance().anyStatementDecoder(
        null,
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
      )
      val error = intercept[RdfProtoDeserializationError] {
        decoder.ingestRow(parsed.getRows.asScala.head)
      }
      error.getMessage should include("Unsupported proto version: 123")
    }
  }

  "RdfStreamFrame" should {
    "ignore unknown metadata field" in {
      // This tests forward compat with changes like those introduced in Jelly-RDF 1.1.1:
      // https://github.com/Jelly-RDF/jelly-protobuf/issues/32
      val parsed = v1.RdfStreamFrame.parseFrom(futureFrameBytes3)
      parsed.getRows.asScala.size should be(1)
      parsed.getRows.asScala.head.hasOptions should be(true)
    }
  }
