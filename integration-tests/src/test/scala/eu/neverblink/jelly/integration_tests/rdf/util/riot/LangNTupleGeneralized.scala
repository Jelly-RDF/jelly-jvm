package eu.neverblink.jelly.integration_tests.rdf.util.riot

import eu.neverblink.jelly.convert.jena.JenaCompatHelper
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.riot.lang.LangNTuple
import org.apache.jena.riot.system.{ParserProfile, StreamRDF}
import org.apache.jena.riot.tokens.{StringType, Token, TokenType, Tokenizer}

/** Base class for parsing N-Triples and N-Quads. Heavily inspired by the Jena Riot code:
  * https://github.com/apache/jena/blob/bd97ad4cf731ade857926787dd2df735644a354b/jena-arq/src/main/java/org/apache/jena/riot/lang/LangNTuple.java
  */
abstract class LangNTupleGeneralized[T](tokens: Tokenizer, profile: ParserProfile, dest: StreamRDF)
    extends LangNTuple[T](tokens, profile, dest):

  // extracted from Jena library for compat with older versions...
  private def checkRDFTermCompat(token: Token): Unit = token.getType match {
    case TokenType.IRI =>
    case TokenType.BNODE =>
    case TokenType.STRING =>
      checkString(token)
    case TokenType.LITERAL_LANG =>
    case TokenType.LITERAL_DT =>
      checkString(token.getSubToken1)
    case TokenType.KEYWORD if token.asWord() == "a" =>
      token.setType(TokenType.IRI).setImage("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    case TokenType.KEYWORD if token.asWord() == "true" =>
      token.setType(TokenType.LITERAL_DT)
        .setSubToken1(Token(TokenType.STRING, "true"))
        .setSubToken2(Token(TokenType.IRI, "http://www.w3.org/2001/XMLSchema#boolean"))
    case TokenType.KEYWORD if token.asWord() == "false" =>
      token.setType(TokenType.LITERAL_DT)
        .setSubToken1(Token(TokenType.STRING, "false"))
        .setSubToken2(Token(TokenType.IRI, "http://www.w3.org/2001/XMLSchema#boolean"))
    case TokenType.DECIMAL =>
      token.setType(TokenType.LITERAL_DT)
        .setSubToken1(Token(TokenType.STRING, token.asWord()))
        .setSubToken2(Token(TokenType.IRI, "http://www.w3.org/2001/XMLSchema#decimal"))
    case TokenType.INTEGER =>
      token.setType(TokenType.LITERAL_DT)
        .setSubToken1(Token(TokenType.STRING, token.asWord()))
        .setSubToken2(Token(TokenType.IRI, "http://www.w3.org/2001/XMLSchema#integer"))
    case _ =>
      exception(token, "Illegal: %s", token)
  }

  private def checkString(token: Token): Unit = {
    if (isStrictMode && !token.hasStringType(StringType.STRING2))
      exception(token, "Not a \"\"-quoted string: %s", token)
  }

  protected final def parseNode(token: Token): Node =
    if (token.isEOF) exception(token, "Premature end of file: %s", token)
    // LT is RDF-star syntax for triple terms <<
    // L_TRIPLE is RDF1.2 syntax for triple terms <<(
    if (token.hasType(TokenType.LT2) || token.hasType(TokenType.L_TRIPLE))
      parseTripleTermGeneralized
    else
      checkRDFTermCompat(token)
      tokenAsNode(token)

  protected final def parseTripleGeneralized: Triple =
    val sToken = nextToken
    val s = parseNode(sToken)
    val p = parseNode(nextToken)
    val o = parseNode(nextToken)
    profile.getFactorRDF.createTriple(s, p, o)

  protected final def parseTripleTermGeneralized: Node =
    val t = parseTripleGeneralized
    val x = nextToken
    if ((x.getType ne TokenType.GT2) && (x.getType ne TokenType.R_TRIPLE))
      exception(x, "Triple term not terminated by >>: %s", x)
    JenaCompatHelper.getInstance().createTripleNode(t.getSubject, t.getPredicate, t.getObject)
