package eu.neverblink.jelly.integration_tests.rdf.util.riot

import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.riot.system.{ParserProfile, StreamRDF}
import org.apache.jena.riot.tokens.{Token, TokenType, Tokenizer}
import org.apache.jena.riot.{Lang, RDFLanguages}

/** Parser for generalized N-Triples. Heavily inspired by the Jena Riot code:
  * https://github.com/apache/jena/blob/bd97ad4cf731ade857926787dd2df735644a354b/jena-arq/src/main/java/org/apache/jena/riot/lang/LangNTriples.java
  */
final class LangNTriplesGeneralized(tokens: Tokenizer, profile: ParserProfile, dest: StreamRDF)
    extends LangNTupleGeneralized[Triple](tokens, profile, dest):

  override def getLang: Lang = RDFLanguages.NTRIPLES

  var lastSub: Option[Node] = None
  var lastPred: Option[Node] = None

  /** Method to parse the whole stream of triples, sending each to the sink */
  override protected def runParser(): Unit =
    while (hasNext) {
      val x = parseOne
      if (x != null) dest.triple(x)
    }

  override protected def parseOne: Triple =
    val triple = (lastSub, lastPred) match {
      case (Some(s), None) =>
        val p = parseNode(nextToken)
        val o = parseNode(nextToken)
        profile.getFactorRDF.createTriple(s, p, o)
      case (Some(s), Some(p)) =>
        val o = parseNode(nextToken)
        profile.getFactorRDF.createTriple(s, p, o)
      case _ =>
        parseTripleGeneralized
    }

    val x = nextToken
    if (
      (x.getType ne TokenType.DOT) && (x.getType ne TokenType.SEMICOLON) && (x.getType ne TokenType.COMMA)
    ) exception(x, "Triple not terminated by DOT: %s", x)
    if x.getType == TokenType.DOT then {
      lastSub = None
      lastPred = None
    }
    if x.getType == TokenType.SEMICOLON then {
      lastSub = Some(triple.getSubject)
      lastPred = None
    }
    if x.getType == TokenType.COMMA then {
      lastSub = Some(triple.getSubject)
      lastPred = Some(triple.getPredicate)
    }
    triple

  override protected def tokenAsNode(token: Token): Node =
    profile.create(null, token)
