import scala.meta._

/**
 * Source code transformer that for oneof protos:
 *  - Unifies naming of is[S/P/O/G]Something methods to isSomething
 *  - Removes with[S/P/O/G]Something methods
 *  - Removes Subject/Predicate/Object/Graph classes and replaces them with RdfTerm from jelly-core
 *  - Unifies naming of [s/p/o/g]Something fields to something
 *
 * All this must be done before ProtoTransformer2 is executed.
 */
object Transform1 {
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

      // Transform method and class names in references
      case Term.Select(_, Term.Name(traitNamePattern(name))) =>
        q"eu.ostrzyciel.jelly.core.RdfTerm"

      // Transform class names
      case Type.Select(_, Type.Name(traitNamePattern(name))) =>
        Type.Select(q"eu.ostrzyciel.jelly.core", Type.Name(if (name == "Graph") "GraphTerm" else "SpoTerm"))

      // Remove with[S/P/O/G]Something methods and Subject/Predicate/Object/Graph classes
      case Template.After_4_4_0(_, _, _, stats, _) => tree.asInstanceOf[Template].copy(
        stats = stats.flatMap { stat => stat match {
          case Defn.Def.After_4_7_3(_, Term.Name(withMethodNamePattern(t)), _, _, _) => None
          case Defn.Trait.After_4_6_0(_, Type.Name(traitNamePattern(name)), _, _, _) => None
          case Defn.Object(_, Term.Name(traitNamePattern(name)), _) => None
          case t => Some(apply(t).asInstanceOf[Stat])
        }}
      )

      case node => super.apply(node)
    }
  }
}
