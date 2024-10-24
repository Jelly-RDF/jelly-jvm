package eu.ostrzyciel.jelly.core.proto.v1

private[core] trait RdfStreamRowValue:
  /**
   * Returns the internal stream row value number, which is used in switch statements to determine the type of the row.
   * This is NOT guaranteed to be the same as the field number in the protobuf encoding!
   * (although this is the case in the current implementation)
   * The values returned by this method may change in future versions of Jelly-JVM without warning.
   * @return
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
