import mill._, scalalib._

// SemanticDB output for the ScalaSemantic MCP server. `-Xsemanticdb` (Scala 3) makes scalac
// emit `.semanticdb` files under each module's compile output (`META-INF/semanticdb/`), which
// ScalaSemantic discovers by scanning the project root. `-sourceroot` pins recorded source paths
// to the project directory so files compiled by Mill and by scala-cli (project.scala) resolve
// identically. Run `mill app.compile` before using the MCP server.
val projectRoot = os.pwd.toString
val semanticDbOptions = Seq("-Xsemanticdb", s"-sourceroot:$projectRoot")

object app extends ScalaModule {
  def scalaVersion = "3.8.4"
  def scalacOptions = semanticDbOptions ++ Seq(
    "-P:wartremover:traverser:org.wartremover.warts.Unsafe",
    "-Wunused:imports",
    "-Werror"
  )
  def ivyDeps = Agg(
    // [dependencies-start]
    ivy"org.typelevel::cats-core:2.13.0",
    ivy"org.typelevel::cats-effect:3.7.0"
    // [dependencies-end]
  )

  def scalacPluginIvyDeps = Agg(
    // [plugins-start]
    ivy"ch.epfl.lara::stainless-compiler-plugin:0.9.8.1",
    ivy"org.wartremover:::wartremover:3.6.1"
    // [plugins-end]
  )

  object test extends ScalaTests {
    def testFramework = "munit.Framework"
    def scalacOptions = semanticDbOptions
    def ivyDeps = Agg(
      // [test-dependencies-start]
      ivy"org.scalameta::munit:1.3.3",
      ivy"org.typelevel::shapeless3-deriving:3.6.0"
      // [test-dependencies-end]
    )
  }
}

object docs extends ScalaModule {
  def scalaVersion = "3.3.4"
  def scalacOptions = semanticDbOptions
  def ivyDeps = Agg(ivy"org.scalameta::mdoc:2.9.0")
}

def prePush() = T.command {
  app.compile()
  app.test.test()()
}
