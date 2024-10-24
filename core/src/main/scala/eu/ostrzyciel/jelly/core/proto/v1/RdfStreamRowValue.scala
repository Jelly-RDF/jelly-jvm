package eu.ostrzyciel.jelly.core.proto.v1

/**
 * Base trait for all classes that can be used in RdfStreamRow's "row" field.
 * 
 * Only intended for internal use.
 */
private[core] trait RdfStreamRowValue:
  /**
   * Number of the row value type used in switches in ser/des code.
   * This number does NOT have to correspond to the field number in the proto (although it currently does).
   * The numbers returned may change between Jelly-JVM releases without warning.
   * @return Number of the row value type.
   */
  def streamRowValueNumber: Int

  def isOptions: Boolean = false
  def isTriple: Boolean = false
  def isQuad: Boolean = false
  def isGraphStart: Boolean = false
  def isGraphEnd: Boolean = false
  def isName: Boolean = false
  def isPrefix: Boolean = false
  def isDatatype: Boolean = false

  def options: RdfStreamOptions = null
  def triple: RdfTriple = null
  def quad: RdfQuad = null
  def graphStart: RdfGraphStart = null
  def graphEnd: RdfGraphEnd = null
  def name: RdfNameEntry = null
  def prefix: RdfPrefixEntry = null
  def datatype: RdfDatatypeEntry = null
