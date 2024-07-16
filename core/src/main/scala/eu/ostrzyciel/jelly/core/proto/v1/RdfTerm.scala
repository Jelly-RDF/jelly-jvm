package eu.ostrzyciel.jelly.core.proto.v1

import com.google.protobuf.CodedOutputStream
import eu.ostrzyciel.jelly.core.proto.v1.*

/**
 * Union type for RDF terms, used in RdfTriple, RdfQuad, and RdfGraphStart.
 */
type RdfTerm = RdfIri | String | RdfLiteral | Null

/**
 * Union type for RDF terms, used in RdfTriple and RdfQuad in S, P, O positions
 */
type SpoTerm = RdfTerm | RdfTriple

/**
 * Union type for RDF terms, used in RdfQuad and RdfGraphStart in G position
 */
type GraphTerm = RdfTerm | RdfDefaultGraph

// Methods below are used in RdfTriple, RdfQuad, and RdfGraphStart instead of generated code. They are all
// inlined by the Scala compiler.

private[v1] inline def fieldTagSize(inline tag: Int) = if tag < 16 then 1 else 2

private[v1] inline def graphTermSerializedSize(g: GraphTerm, inline tagOffset: Int) = g match
  case null => 0
  case iri: RdfIri => fieldTagSize(1 + tagOffset)
    + CodedOutputStream.computeUInt32SizeNoTag(iri.serializedSize) + iri.serializedSize
  case bnode: String => CodedOutputStream.computeStringSize(2 + tagOffset, bnode)
  case defaultGraph: RdfDefaultGraph => fieldTagSize(3 + tagOffset)
    + CodedOutputStream.computeUInt32SizeNoTag(defaultGraph.serializedSize) + defaultGraph.serializedSize
  case literal: RdfLiteral => fieldTagSize(4 + tagOffset)
    + CodedOutputStream.computeUInt32SizeNoTag(literal.serializedSize)+ literal.serializedSize
  
private[v1] inline def graphTermWriteTo(g: GraphTerm, inline tagOffset: Int, out: CodedOutputStream): Unit = g match
  case null => ()
  case iri: RdfIri =>
    out.writeTag(1 + tagOffset, 2)
    out.writeUInt32NoTag(iri.serializedSize)
    iri.writeTo(out)
  case bnode: String =>
    out.writeString(2 + tagOffset, bnode)
  case defaultGraph: RdfDefaultGraph =>
    out.writeTag(3 + tagOffset, 2)
    out.writeUInt32NoTag(defaultGraph.serializedSize)
    defaultGraph.writeTo(out)
  case literal: RdfLiteral =>
    out.writeTag(4 + tagOffset, 2)
    out.writeUInt32NoTag(literal.serializedSize)
    literal.writeTo(out)
  
private[v1] inline def spoTermSerializedSize(t: SpoTerm, inline tagOffset: Int) = t match
  case null => 0
  case iri: RdfIri => fieldTagSize(1 + tagOffset)
    + CodedOutputStream.computeUInt32SizeNoTag(iri.serializedSize) + iri.serializedSize
  case bnode: String => CodedOutputStream.computeStringSize(2 + tagOffset, bnode)
  case literal: RdfLiteral => fieldTagSize(3 + tagOffset)
    + CodedOutputStream.computeUInt32SizeNoTag(literal.serializedSize) + literal.serializedSize
  case triple: RdfTriple => fieldTagSize(4 + tagOffset)
    + CodedOutputStream.computeUInt32SizeNoTag(triple.serializedSize) + triple.serializedSize

private[v1] inline def spoTermWriteTo(t: SpoTerm, inline tagOffset: Int, out: CodedOutputStream): Unit = t match
  case null => ()
  case iri: RdfIri =>
    out.writeTag(1 + tagOffset, 2)
    out.writeUInt32NoTag(iri.serializedSize)
    iri.writeTo(out)
  case bnode: String =>
    out.writeString(2 + tagOffset, bnode)
  case literal: RdfLiteral =>
    out.writeTag(3 + tagOffset, 2)
    out.writeUInt32NoTag(literal.serializedSize)
    literal.writeTo(out)
  case triple: RdfTriple =>
    out.writeTag(4 + tagOffset, 2)
    out.writeUInt32NoTag(triple.serializedSize)
    triple.writeTo(out)
