package eu.ostrzyciel.jelly.core.proto.v1

private[core] trait RdfValue:
  def isTriple: Boolean = false
  def isQuad: Boolean = false
  def isGraphStart: Boolean = false
  def isGraphEnd: Boolean = false
  def isNamespace: Boolean = false
  def isName: Boolean = false
  def isPrefix: Boolean = false
  def isDatatype: Boolean = false

  def triple: RdfTriple = null
  def quad: RdfQuad = null
  def graphStart: RdfGraphStart = null
  def graphEnd: RdfGraphEnd = null
  def namespace: RdfNamespaceDeclaration = null
  def name: RdfNameEntry = null
  def prefix: RdfPrefixEntry = null
  def datatype: RdfDatatypeEntry = null

private[core] trait RdfStreamRowValue extends RdfValue:
  def streamRowValueNumber: Int
  def isOptions: Boolean = false
  def options: RdfStreamOptions = null

private[core] trait RdfLookupEntryRowValue extends RdfStreamRowValue
