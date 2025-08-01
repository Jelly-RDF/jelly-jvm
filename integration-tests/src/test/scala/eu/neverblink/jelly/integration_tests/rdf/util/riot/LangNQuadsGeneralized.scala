package eu.neverblink.jelly.integration_tests.rdf.util.riot

import org.apache.jena.graph.Node
import org.apache.jena.riot.system.{ParserProfile, StreamRDF}
import org.apache.jena.riot.tokens.{Token, TokenType, Tokenizer}
import org.apache.jena.riot.{Lang, RDFLanguages}
import org.apache.jena.sparql.core.Quad

/** Parser for generalized N-Quads. Heavily inspired by the Jena Riot code:
 * https://github.com/apache/jena/blob/bd97ad4cf731ade857926787dd2df735644a354b/jena-arq/src/main/java/org/apache/jena/riot/lang/LangNQuads.java
 */
final class LangNQuadsGeneralized(tokens: Tokenizer, profile: ParserProfile, dest: StreamRDF)
  extends LangNTupleGeneralized[Quad](tokens, profile, dest):

  // Null for no graph.
  private var currentGraph: Node = null

  override def getLang: Lang = RDFLanguages.NQUADS

  /** Method to parse the whole stream of triples, sending each to the sink */
  override protected def runParser(): Unit =
    while (hasNext) {
      val x = parseOne
      if (x != null) dest.quad(x)
    }

  override protected def parseOne: Quad =
    val sToken = nextToken
    val s = parseNode(sToken)
    val p = parseNode(nextToken)
    val o = parseNode(nextToken)
    var xToken = nextToken // Maybe DOT
    if (xToken.getType eq TokenType.EOF)
      exception(xToken, "Premature end of file: Quad not terminated by DOT: %s", xToken)
    // Process graph node first, before S,P,O
    // to set bnode label scope (if not global)
    var c: Node = null
    if (xToken.getType ne TokenType.DOT) {
      c = parseNode(xToken)
      xToken = nextToken
      currentGraph = c
    } else {
      c = Quad.defaultGraphNodeGenerated
      currentGraph = null
    }
    // Check end of quad
    if (xToken.getType ne TokenType.DOT) exception(xToken, "Quad not terminated by DOT: %s", xToken)
    profile.createQuad(c, s, p, o, sToken.getLine, sToken.getColumn)

  override protected def tokenAsNode(token: Token): Node =
    profile.create(currentGraph, token)