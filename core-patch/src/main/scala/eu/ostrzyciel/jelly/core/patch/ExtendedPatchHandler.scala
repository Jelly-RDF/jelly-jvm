package eu.ostrzyciel.jelly.core.patch

trait ExtendedPatchHandler[TNode, -TTriple, -TQuad] extends PatchHandler[TNode]:
  /**
   * Add RDF triple command. (A Triple)
   *
   * @param triple the triple to add
   */
  def addTriple(triple: TTriple): Unit

  /**
   * Delete RDF triple command. (D Triple)
   *
   * @param triple the triple to delete
   */
  def deleteTriple(triple: TTriple): Unit

  /**
   * Add RDF quad command. (A Quad)
   *
   * @param quad the quad to add
   */
  def addQuad(quad: TQuad): Unit

  /**
   * Delete RDF quad command. (D Quad)
   *
   * @param quad the quad to delete
   */
  def deleteQuad(quad: TQuad): Unit
