import scala.meta.*

/**
 * Transformer that adds `is*` and `*` methods to `RdfIri`, `RdfLiteral` and `RdfDefaultGraph` classes,
 * to allow using them directly in RDF term context. See: [[eu.ostrzyciel.jelly.core.proto.v1.RdfTerm]].
 */
object Transform3 {
  val transformer: Transformer = new Transformer {
    def copyTemplate(
      templ: Template,
      traits: Seq[String],
      name: String = "",
      isName: String = "",
      number: Option[Int] = None
    ): Template = {
      templ.copy(
        inits = templ.inits ++ traits.map { tName =>
          Init.After_4_6_0(Type.Name(tName), Name.Anonymous(), Nil)
        },
        stats = templ.body.stats ++ Seq(
          if (isName == "") None else Some(Defn.Def.After_4_7_3(
            List(Mod.Override()),
            Term.Name(isName),
            Nil,
            None,
            Lit.Boolean(value = true),
          )),
          if (name == "") None else Some(Defn.Def.After_4_7_3(
            List(Mod.Override()),
            Term.Name(name),
            Nil,
            None,
            Term.This(Name.Anonymous()),
          )),
          number.map { n =>
            Defn.Def.After_4_7_3(
              List(Mod.Override()),
              Term.Name("streamRowValueNumber"),
              Nil,
              None,
              Lit.Int(n),
            )
          },
        ).flatten
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
          case "RdfStreamOptions" => Some(copyTemplate(
            templ, Seq("RdfStreamRowValue", "BaseJellyOptions"), "options", "isOptions", Some(1)
          ))
          case "RdfGraphEnd" => Some(copyTemplate(templ, Seq("RdfStreamRowValue"), "graphEnd", "isGraphEnd", Some(5)))
          case "RdfNameEntry" => Some(copyTemplate(templ, Seq("RdfStreamRowValue"), "name", "isName", Some(9)))
          case "RdfPrefixEntry" => Some(copyTemplate(templ, Seq("RdfStreamRowValue"), "prefix", "isPrefix", Some(10)))
          case "RdfDatatypeEntry" => Some(copyTemplate(templ, Seq("RdfStreamRowValue"), "datatype", "isDatatype", Some(11)))

          // PatchValue
          case "RdfPatchOptions" => Some(copyTemplate(
            templ, Seq("PatchValue", "BaseJellyOptions")
          ))
          case "RdfPatchTransactionStart" => Some(copyTemplate(templ, Seq("PatchValue")))
          case "RdfPatchTransactionCommit" => Some(copyTemplate(templ, Seq("PatchValue")))
          case "RdfPatchTransactionAbort" => Some(copyTemplate(templ, Seq("PatchValue")))
          case "RdfPatchHeader" => Some(copyTemplate(templ, Seq("PatchValue")))
          case "RdfPatchPunctuation" => Some(copyTemplate(templ, Seq("PatchValue")))

          case _ => None
        }
        newTempl.map(templ => tree.asInstanceOf[Defn.Class].copy(templ = templ)).getOrElse(tree)
      case t => super.apply(t)
    }
  }
}
