package eu.neverblink.jelly.integration_tests.rdf.util.riot

import org.apache.jena.graph.{Node, NodeFactory, Triple}
import org.apache.jena.riot.lang.LangNTuple
import org.apache.jena.riot.system.{ParserProfile, StreamRDF}
import org.apache.jena.riot.tokens.{Token, TokenType, Tokenizer}

/** Base class for parsing N-Triples and N-Quads. Heavily inspired by the Jena Riot code:
 * https://github.com/apache/jena/blob/bd97ad4cf731ade857926787dd2df735644a354b/jena-arq/src/main/java/org/apache/jena/riot/lang/LangNTuple.java
 */
abstract class LangNTupleGeneralized[T](tokens: Tokenizer, profile: ParserProfile, dest: StreamRDF)
  extends LangNTuple[T](tokens, profile, dest):

  protected final def parseNode(token: Token): Node =
    if (token.isEOF) exception(token, "Premature end of file: %s", token)
    // LT is RDF-star syntax for triple terms <<
    // L_TRIPLE is RDF1.2 syntax for triple terms <<(
    if (token.hasType(TokenType.LT2) || token.hasType(TokenType.L_TRIPLE)) parseTripleTermGeneralized
    else
      checkRDFTerm(token)
      tokenAsNode(token)

  protected final def parseTripleGeneralized: Triple =
    val sToken = nextToken
    val s = parseNode(sToken)
    val p = parseNode(nextToken)
    val o = parseNode(nextToken)
    profile.createTriple(s, p, o, sToken.getLine, sToken.getColumn)

  protected final def parseTripleTermGeneralized: Node =
    val t = parseTripleGeneralized
    val x = nextToken
    if ((x.getType ne TokenType.GT2) && (x.getType ne TokenType.R_TRIPLE)) exception(x, "Triple term not terminated by >>: %s", x)
    NodeFactory.createTripleNode(t)