import scala.meta._

/**
 * Source code transformer that (mostly) just removes Option[] from the code.
 * Instead, we use nulls to avoid heap allocations and other overhead.
 */
object ProtoTransformer2 {
  def transform(input: String): String = {
    val tree = input.parse[Source].get

    val transformer = new Transformer {
      private def transformIsDefinedInner(thenp: Term): Term = thenp.transform {
        // ().field.get => ().field
        case Term.Select(name, methodName) if methodName.value == "get" => name
        case t => super.apply(t)
      }.asInstanceOf[Term]

      override def apply(tree: Tree): Tree = tree match {
        case Template.After_4_4_0(_, _, _, stats, _) => tree.asInstanceOf[Template].copy(
          stats = stats.flatMap { stat => stat match {
            // Remove def asRecognized
            case Defn.Def.After_4_7_3(_, Term.Name("asRecognized"), _, _, _) => None
            // Transform def ... => Option[T]  to  def ... => T
            case Defn.Def.After_4_7_3(_, _, _, Some(Type.Apply.After_4_6_0(tpe, typeArgs)), term)
              if tpe.syntax == "_root_.scala.Option" => Some(stat.asInstanceOf[Defn.Def].copy(
              decltpe = Some(typeArgs.values.head),
              body = term match {
                case _ if term.syntax == "_root_.scala.None" => typeArgs.values.head.syntax match {
                  case "_root_.scala.Int" => q"0"
                  case "_root_.scala.Predef.String" => q""" "" """
                  case _ => q"null"
                }
                case Term.Apply.After_4_6_0(term, argClause) if term.syntax == "Some" => argClause.values.head
                case _ => term
              }
            ))
            case t => Some(super.apply(t).asInstanceOf[Stat])
          }}
        )

        // ().field.isDefined  to  ().isField
        case Term.If.After_4_4_0(cond, thenp, _, _) => cond match {
          case Term.Select(Term.Select(subject, Term.Name(fieldName)), methodName) => methodName.value match {
            case "isDefined" => tree.asInstanceOf[Term.If].copy(
              cond = Term.Select(subject, Term.Name(f"is${fieldName.head.toUpper}${fieldName.tail}")),
              thenp = transformIsDefinedInner(thenp)
            )
            case _ => tree
          }
          case _ => tree
        }

        // ().orNull  to  ()
        case Term.Select(name, methodName) => methodName.value match {
          case "orNull" => name
          case _ => tree
        }

        // ().fold($1)($2)  to  $1
        case Term.Apply.After_4_6_0(
          Term.Apply.After_4_6_0(Term.Select(name, methodName), argClause1),
          argClause2
        ) if methodName.value == "fold" => argClause1.values.head

        // ().field.foreach { __v => ... }  to  if (().isField) { ... }
        case Term.Apply.After_4_6_0(
          Term.Select(Term.Select(subject, Term.Name(fieldName)), methodName),
          Term.ArgClause(List(Term.Block(List(Term.Function.After_4_6_0(_, Term.Block(stats))))), _)
        ) if methodName.value == "foreach" =>
          val newStats = stats.map(_.transform {
            case Term.Name("__v") => Term.Select(subject, Term.Name(fieldName))
            case s => s
          }.asInstanceOf[Stat])
          Term.If.After_4_4_0(
            cond = Term.Select(subject, Term.Name(f"is${fieldName.head.toUpper}${fieldName.tail}")),
            thenp = Term.Block(newStats),
            elsep = Lit.Unit(),
          )

        // ().field.getOrElse(...)  to  if (().isField) { ... } else { ... }
        case Term.Apply.After_4_6_0(
          Term.Select(
            Term.Apply.After_4_6_0(Term.Select(Term.Select(subject, fieldName), Term.Name("map")), thenArgs),
            Term.Name("getOrElse")
          ),
          elseArgs
        ) =>
          val thenBody = thenArgs.values.head.asInstanceOf[Term.AnonymousFunction].body match {
            case Term.Apply.After_4_6_0(fun, _) => Term.Apply.After_4_6_0(
              fun,
              Term.ArgClause(List(Term.Select(subject, fieldName)))
            )
            case Term.Select(_, subField) => Term.Select(Term.Select(subject, fieldName), subField)
            case t => t
          }
          Term.If.After_4_4_0(
            cond = Term.Select(subject, Term.Name(f"is${fieldName.value.head.toUpper}${fieldName.value.tail}")),
            thenp = thenBody,
            elsep = elseArgs.values.head
          )
        case node => super.apply(node)
      }
    }

    transformer(tree).toString
  }
}
