import scala.meta.*

/**
 * Transformer that adds `is*` and accessor methods to classes implementing RdfTerm and RdfStreamRowValue,
 * to allow using them directly without additional wrapping. See: [[eu.ostrzyciel.jelly.core.proto.v1.RdfTerm]].
 */
object Transform3 {
  val transformer: Transformer = new Transformer {
    def copyTemplate(templ: Template, traits: Seq[String], name: String, isName: String, number: Option[Int] = None):
    Template = {
      templ.copy(
        inits = templ.inits ++ traits.map { tName =>
          Init.After_4_6_0(Type.Name(tName), Name.Anonymous(), Nil)
        },
        stats = templ.body.stats ++ Seq(
          // is* method
          Defn.Def.After_4_7_3(
            List(Mod.Override()),
            Term.Name(isName),
            Nil,
            None,
            Lit.Boolean(value = true),
          ),
          // Accessor method
          Defn.Def.After_4_7_3(
            List(Mod.Override()),
            Term.Name(name),
            Nil,
            None,
            Term.This(Name.Anonymous()),
          ),
        ) ++ number.map { n =>
          // streamRowValueNumber method
          Defn.Def.After_4_7_3(
            List(Mod.Override()),
            Term.Name("streamRowValueNumber"),
            Nil,
            None,
            Lit.Int(n),
          )
        }
      )
    }

    override def apply(tree: Tree): Tree = tree match {
      case Defn.Class.After_4_6_0(_, Type.Name(name), _, _, templ) =>
        val newTempl = name match {
          // RdfTerm
          case "RdfIri" => Some(copyTemplate(templ, Seq("UniversalTerm"), "iri", "isIri"))
          case "RdfLiteral" => Some(copyTemplate(templ, Seq("UniversalTerm"), "literal", "isLiteral"))
          case "RdfDefaultGraph" => Some(copyTemplate(templ, Seq("GraphTerm"), "defaultGraph", "isDefaultGraph"))

          // RdfStreamRowValue
          case "RdfStreamOptions" => Some(copyTemplate(templ, Seq("RdfStreamRowValue"), "options", "isOptions", Some(1)))
          case "RdfGraphEnd" => Some(copyTemplate(templ, Seq("RdfStreamRowValue"), "graphEnd", "isGraphEnd", Some(5)))
          case "RdfNameEntry" => Some(copyTemplate(templ, Seq("RdfStreamRowValue"), "name", "isName", Some(9)))
          case "RdfPrefixEntry" => Some(copyTemplate(templ, Seq("RdfStreamRowValue"), "prefix", "isPrefix", Some(10)))
          case "RdfDatatypeEntry" => Some(copyTemplate(templ, Seq("RdfStreamRowValue"), "datatype", "isDatatype", Some(11)))
          case _ => None
        }
        newTempl.map(templ => tree.asInstanceOf[Defn.Class].copy(templ = templ)).getOrElse(tree)
      case t => super.apply(t)
    }
  }
}
