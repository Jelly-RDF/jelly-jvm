import scala.meta._

object ProtoTransformer {
  val transformations: Seq[Transformer] = Seq(
    Transform1.transformer,
    Transform2.transformer,
  )

  def transform(input: String): String = {
    val tree = input.parse[Source].get

    val transformer = new Transformer {
      override def apply(tree: Tree): Tree = {
        transformations.foldLeft(tree) { (t, transformer) =>
          transformer(t)
        }
      }
    }

    transformer(tree).syntax
  }
}
