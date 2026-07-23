package codacy.phpcsfixer.docsgen

object GeneratorMain {

  def main(args: Array[String]): Unit = {
    new Generator().run()
  }
}
