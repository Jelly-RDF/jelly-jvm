package eu.ostrzyciel.jelly

import eu.ostrzyciel.jelly.core.proto.v1.LogicalStreamType

package object core:
  sealed class RdfProtoDeserializationError(msg: String) extends Error(msg)
  final class MissingPrefixEntryError(val prefixId: Int) extends RdfProtoDeserializationError(
    s"Missing entry in prefix table at ID: $prefixId"
  )
  final class MissingNameEntryError(val nameId: Int) extends RdfProtoDeserializationError(
    s"Missing entry in name table at ID: $nameId"
  )

  final class RdfProtoSerializationError(msg: String) extends Error(msg)

  // Constants
  object Constants:
    val jellyName = "Jelly"
    val jellyFileExtension = "jelly"
    val jellyContentType = "application/x-jelly-rdf"
    val protoVersion = 1
    val protoSemanticVersion = "1.0.0"

  extension (logicalType: LogicalStreamType)
    /**
     * Converts the logical stream type to its base concrete stream type in RDF-STaX.
     * For example, [[LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS]] will be converted to [[LogicalStreamType.DATASETS]].
     * UNSPECIFIED values will be left as-is.
     *
     * @return base stream type
     */
    def toBaseType: LogicalStreamType =
      LogicalStreamType.fromValue(logicalType.value % 10)

    /**
     * Checks if the logical stream type is equal to or a subtype of the other logical stream type.
     * For example, [[LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS]] is a subtype of [[LogicalStreamType.DATASETS]].
     *
     * @param other the other logical stream type
     * @return true if the logical stream type is equal to or a subtype of the other logical stream type
     */
    def isEqualOrSubtypeOf(other: LogicalStreamType): Boolean =
      logicalType == other || logicalType.value.toString.endsWith(other.value.toString)
