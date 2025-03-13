package eu.ostrzyciel.jelly.examples.shared

trait ScalaExample extends Example:
  def main(args: Array[String]): Unit
  
  final override def run(args: Array[String]): Unit = main(args)
