package eu.ostrzyciel.jelly.core.patch

/**
 * Abstract handler of RDF Patch operations.
 * @tparam TNode type of RDF nodes in the library
 */
trait PatchHandler[TNode]:
  /**
   * Add a triple to the patch stream. (A Triple)
   *
   * @param s the subject of the triple
   * @param p the predicate of the triple
   * @param o the object of the triple
   */
  def addTriple(s: TNode, p: TNode, o: TNode): Unit

  /**
   * Delete a triple from the patch stream. (D Triple)
   *
   * @param s the subject of the triple
   * @param p the predicate of the triple
   * @param o the object of the triple
   */
  def deleteTriple(s: TNode, p: TNode, o: TNode): Unit

  /**
   * Add a quad to the patch stream. (A Quad)
   *
   * @param s the subject of the quad
   * @param p the predicate of the quad
   * @param o the object of the quad
   * @param g the graph of the quad
   */
  def addQuad(s: TNode, p: TNode, o: TNode, g: TNode): Unit

  /**
   * Delete a quad from the patch stream. (D Quad)
   *
   * @param s the subject of the quad
   * @param p the predicate of the quad
   * @param o the object of the quad
   * @param g the graph of the quad
   */
  def deleteQuad(s: TNode, p: TNode, o: TNode, g: TNode): Unit

  /**
   * Start a new transaction. (TX)
   */
  def transactionStart(): Unit

  /**
   * Commit the current transaction. (TC)
   */
  def transactionCommit(): Unit

  /**
   * Abort the current transaction. (TA)
   */
  def transactionAbort(): Unit

  /**
   * Add a namespace declaration to the patch stream.
   * This is called "prefix add" in RDF Patch. (PA)
   *
   * @param name     the name of the namespace (without the trailing colon)
   * @param iriValue the IRI value of the namespace
   */
  def addNamespace(name: String, iriValue: String): Unit

  /**
   * Delete a namespace declaration from the patch stream.
   * This is called "prefix delete" in RDF Patch. (PD)
   *
   * @param name     the name of the namespace (without the trailing colon)
   * @param iriValue the IRI value of the namespace
   */
  def deleteNamespace(name: String, iriValue: String): Unit

  /**
   * Add a header to the patch stream. (H)
   *
   * @param key   the key of the header
   * @param value the value of the header
   */
  def header(key: String, value: TNode): Unit
