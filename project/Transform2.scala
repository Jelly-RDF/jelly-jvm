import scala.meta.*

/**
 * Transforms if statement chains into else ifs where possible (in serialization code).
 */
object Transform2 {
  def addToIfChain(left: Term.If, right: Term.If): Term.If = left.elsep match {
    case Lit.Unit() => left.copy(elsep = right)
    case elsep: Term.If => left.copy(elsep = addToIfChain(elsep, right))
  }

  val transformer: Transformer = new Transformer {
    override def apply(tree: Tree): Tree = tree match {
      case Term.Block(stats) =>
        val newStats = scala.collection.mutable.ArrayBuffer.empty[Stat]
        var lastSubject: Option[String] = None
        var acc: Option[Term.If] = None
        for (stat <- stats) {
          stat match {
            case Term.If.After_4_4_0(Term.Select(Term.Name(subject), Term.Name(method)), thenp, Lit.Unit(), mods)
            if method.startsWith("is") =>
              val ifTerm = stat.asInstanceOf[Term.If]
              if (lastSubject.contains(subject)) {
                acc = Some(addToIfChain(acc.get, ifTerm))
              } else {
                acc.foreach(newStats += _)
                acc = Some(ifTerm)
                lastSubject = Some(subject)
              }
            case _ =>
              acc.foreach(newStats += _)
              newStats += stat
              lastSubject = None
              acc = None
          }
        }
        acc.foreach(newStats += _)
        Term.Block(newStats.toList)

      case t => super.apply(t)
    }
  }
}
