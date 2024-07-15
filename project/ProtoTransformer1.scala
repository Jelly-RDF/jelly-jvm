import scala.meta._

/**
 * Source code transformer that for oneof protos:
 *  - Unifies naming of is[S/P/O/G]Something methods to isSomething
 *  - Removes with[S/P/O/G]Something methods
 *  - Unifies naming of [S/P/O/G]Something classes to Something
 *  - Unifies naming of [s/p/o/g]Something fields to something
 *  - Adds base traits (RdfTerm, RdfTermCompanion)
 *
 * All this must be done before ProtoTransformer2 is executed.
 */
object ProtoTransformer1 {
  def transform(input: String): String = {
    val tree = input.parse[Source].get

    val isMethodNamePattern = "^is[SPOG](Iri|Bnode|Literal|TripleTerm|DefaultGraph)$".r
    val withMethodNamePattern = "^with[SPOG](Iri|Bnode|Literal|TripleTerm|DefaultGraph)$".r
    val classNamePattern = "^[SPOG](Iri|Bnode|Literal|TripleTerm|DefaultGraph)".r
    val fieldNamePattern = "^[spog](Iri|Bnode|Literal|TripleTerm|DefaultGraph)".r
    val traitNamePattern = "^(Subject|Predicate|Object|Graph)$".r

    val transformer = new Transformer {
      override def apply(tree: Tree): Tree = tree match {
        // Transform method and class names in references
        case Term.Name(name) => name match {
          case isMethodNamePattern(t) => Term.Name(f"is$t")
          case classNamePattern(t) => Term.Name(t)
          case fieldNamePattern(t) => Term.Name(f"${t.head.toLower}${t.tail}")
          case _ => super.apply(tree)
        }

        // Transform class names in definitions
        case Type.Name(classNamePattern(t)) => Type.Name(t)

        // Remove with[S/P/O/G]Something methods
        case Template.After_4_4_0(_, _, _, stats, _) => tree.asInstanceOf[Template].copy(
          stats = stats.flatMap { stat => stat match {
            case Defn.Def.After_4_7_3(_, Term.Name(withMethodNamePattern(t)), _, _, _) => None
            case t => Some(apply(t).asInstanceOf[Stat])
          }}
        )

        // Transform traits for RDF terms
        case Defn.Trait.After_4_6_0(_, Type.Name(traitNamePattern(name)), _, _, templ) =>
          val adapterName = if (name == "Graph") "GraphTerm" else s"SpoTerm"
          tree.asInstanceOf[Defn.Trait].copy(
            templ = apply(templ.copy(
              inits = templ.inits :+ Init.After_4_6_0(
                Type.Select(q"eu.ostrzyciel.jelly.core.proto_adapters", Type.Name(adapterName)),
                Name.Anonymous(), Nil
              )
            )).asInstanceOf[Template]
          )

        // Transform companion objects for RDF terms
        case Defn.Object(_, Term.Name(traitNamePattern(name)), templ) =>
          val adapterName = if (name == "Graph") "GraphTermCompanion" else s"SpoTermCompanion"
          val lastMethod = if (name == "Graph")
            q"val makeDefaultGraph: DefaultGraph = DefaultGraph(RdfDefaultGraph.defaultInstance)"
          else q"def makeTripleTerm(t: eu.ostrzyciel.jelly.core.proto.v1.RdfTriple): TripleTerm = TripleTerm(t)"
          tree.asInstanceOf[Defn.Object].copy(
            templ = apply(templ.copy(
              inits = templ.inits :+ Init.After_4_6_0(
                Type.Apply(
                  Type.Select(q"eu.ostrzyciel.jelly.core.proto_adapters", Type.Name(adapterName)),
                  Type.ArgClause(Type.Name(name) :: Nil)
                ),
                Name.Anonymous(), Nil
              ),
              stats = templ.stats ++ Seq(
                q"val makeEmpty: Empty.type = Empty",
                q"def makeIri(iri: eu.ostrzyciel.jelly.core.proto.v1.RdfIri): Iri = Iri(iri)",
                q"def makeBnode(bnode: String): Bnode = Bnode(bnode)",
                q"def makeLiteral(literal: eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral): Literal = Literal(literal)"
              ) ++ Seq(lastMethod),
            )).asInstanceOf[Template]
          )

        case node => super.apply(node)
      }
    }

    transformer(tree).toString
  }
}
