package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.proto.v1.{GraphTerm, SpoTerm}

trait PatchEncoderConverter[TNode, -TTriple, -TQuad, -TQuoted]:
  // TODO: this should really be moved to core and used in the regular encoder
  def getTstS(triple: TTriple): TNode
  def getTstP(triple: TTriple): TNode
  def getTstO(triple: TTriple): TNode

  def getQstS(quad: TQuad): TNode
  def getQstP(quad: TQuad): TNode
  def getQstO(quad: TQuad): TNode
  def getQstG(quad: TQuad): TNode

  def getQuotedS(triple: TQuoted): TNode
  def getQuotedP(triple: TQuoted): TNode
  def getQuotedO(triple: TQuoted): TNode

  def nodeToProto(node: TNode): SpoTerm
  def graphNodeToProto(node: TNode): GraphTerm
