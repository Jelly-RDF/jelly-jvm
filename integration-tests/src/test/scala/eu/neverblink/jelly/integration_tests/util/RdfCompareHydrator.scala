package eu.neverblink.jelly.integration_tests.util


trait RdfCompareHydrator[TNode, TStatement]:

  def isBlank(node: TNode): Boolean

  def getBlankNodeLabel(node: TNode): String

  def isNodeTriple(node: TNode): Boolean

  def asNodeTriple(node: TNode): TStatement

  def iterateTerms(statement: TStatement): Seq[TNode]
