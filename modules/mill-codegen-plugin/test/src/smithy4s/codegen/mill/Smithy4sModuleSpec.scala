/*
 *  Copyright 2021-2022 Disney Streaming
 *
 *  Licensed under the Tomorrow Open Source Technology License, Version 1.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     https://disneystreaming.github.io/TOST-1.0.txt
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package smithy4s.codegen.mill

import mill.testkit.MillTestKit
import mill.scalalib._
import mill._
import munit.Location
import sourcecode.FullName
import java.nio.file.Paths
import mill.scalalib.publish.PomSettings
import mill.scalalib.publish.VersionControl
import coursier.Repository
import coursier.ivy.IvyRepository
import mill.define.Task

class Smithy4sModuleSpec extends munit.FunSuite {
  private val resourcePath =
    os.Path(Paths.get(this.getClass().getResource("/").toURI()))

  private object testKit extends MillTestKit

  private val coreDep =
    ivy"com.disneystreaming.smithy4s::smithy4s-core:${smithy4s.codegen.BuildInfo.version}"

  ivy"com.disneystreaming.smithy4s::smithy4s-aws-kernel:${smithy4s.codegen.BuildInfo.version}"

  test("basic codegen runs") {
    object foo extends testKit.BaseModule with Smithy4sModule {
      override def scalaVersion = "2.13.10"
      override def ivyDeps = Agg(coreDep)
      override def millSourcePath = resourcePath / "basic"
    }
    val ev = testKit.staticTestEvaluator(foo)(FullName("codegen-runs"))

    compileWorks(foo, ev)
    checkFileExist(
      ev.outPath / "smithy4sOutputDir.dest" / "scala" / "basic" / "MyNewString.scala",
      shouldExist = true
    )

    withFile(
      foo.millSourcePath / "smithy" / "added.smithy",
      """namespace basic
        |
        |structure Added {}""".stripMargin
    )(compileWorks(foo, ev))

    checkFileExist(
      ev.outPath / "smithy4sOutputDir.dest" / "scala" / "basic" / "Added.scala",
      shouldExist = true
    )
  }

  test("codegen with dependencies") {
    object foo extends testKit.BaseModule with Smithy4sModule {
      override def scalaVersion = "2.13.10"
      override def ivyDeps = Agg(coreDep)
      override def millSourcePath = resourcePath / "basic"
      override def smithy4sAllowedNamespaces = T(Some(Set("aws.iam")))
      override def smithy4sIvyDeps = Agg(
        ivy"software.amazon.smithy:smithy-aws-iam-traits:${smithy4s.codegen.BuildInfo.smithyVersion}"
      )
    }
    val ev =
      testKit.staticTestEvaluator(foo)(FullName("codegen-with-dependencies"))

    compileWorks(foo, ev)
    checkFileExist(
      ev.outPath / "smithy4sOutputDir.dest" / "scala" / "aws" / "iam" / "ActionPermissionDescription.scala",
      shouldExist = true
    )
  }

  test("multi-module codegen works") {

    object foo extends testKit.BaseModule with Smithy4sModule {
      override def scalaVersion = "2.13.10"
      override def ivyDeps = Agg(coreDep)
      override def millSourcePath = resourcePath / "multi-module" / "foo"
    }

    object bar extends testKit.BaseModule with Smithy4sModule {
      override def moduleDeps = Seq(foo)
      override def scalaVersion = "2.13.10"
      override def ivyDeps = Agg(coreDep)
      override def millSourcePath = resourcePath / "multi-module" / "bar"
    }

    val fooEv = testKit.staticTestEvaluator(foo)(FullName("multi-module-foo"))
    val barEv = testKit.staticTestEvaluator(bar)(FullName("multi-module-bar"))

    compileWorks(foo, fooEv)
    checkFileExist(
      fooEv.outPath / "smithy4sOutputDir.dest" / "scala" / "foo" / "Foo.scala",
      shouldExist = true
    )
    checkFileExist(
      fooEv.outPath / "smithy4sOutputDir.dest" / "scala" / "foodir" / "FooDir.scala",
      shouldExist = true
    )

    compileWorks(bar, barEv)
    checkFileExist(
      barEv.outPath / "smithy4sOutputDir.dest" / "scala" / "foo" / "Foo.scala",
      shouldExist = false
    )
    checkFileExist(
      barEv.outPath / "smithy4sOutputDir.dest" / "scala" / "foodir" / "FooDir.scala",
      shouldExist = false
    )
    checkFileExist(
      barEv.outPath / "smithy4sOutputDir.dest" / "scala" / "bar" / "Bar.scala",
      shouldExist = true
    )

    withFile(
      foo.millSourcePath / "src" / "a.scala",
      """package foo
        |object a""".stripMargin
    )(compileWorks(bar, barEv))
  }

  test("multi-module codegen works with AWS specs upstream") {

    object foo extends testKit.BaseModule with Smithy4sModule {
      override def scalaVersion = "2.13.8"
      override def ivyDeps = Agg(
        ivy"com.disneystreaming.smithy4s::smithy4s-aws-kernel:${smithy4s.codegen.BuildInfo.version}"
      )
      override def smithy4sIvyDeps: T[Agg[Dep]] = Agg(
        ivy"software.amazon.smithy:smithy-aws-traits:${smithy4s.codegen.BuildInfo.smithyVersion}"
      )
      override def millSourcePath = resourcePath / "multi-module-aws" / "foo"
    }

    object bar extends testKit.BaseModule with Smithy4sModule {
      override def moduleDeps = Seq(foo)
      override def scalaVersion = "2.13.8"
      override def millSourcePath = resourcePath / "multi-module-aws" / "bar"
    }

    val fooEv =
      testKit.staticTestEvaluator(foo)(FullName("multi-module-aws-foo"))

    val barEv =
      testKit.staticTestEvaluator(bar)(FullName("multi-module-aws-bar"))

    compileWorks(foo, fooEv)
    checkFileExist(
      fooEv.outPath / "smithy4sOutputDir.dest" / "scala" / "foo" / "Lambda.scala",
      shouldExist = true
    )
    // Checking no aws package is generated
    checkFileExist(
      fooEv.outPath / "smithy4sOutputDir.dest" / "scala" / "aws",
      shouldExist = false
    )

    compileWorks(bar, barEv)
    checkFileExist(
      barEv.outPath / "smithy4sOutputDir.dest" / "scala" / "foo" / "Lambda.scala",
      shouldExist = false
    )
  }

  private def withFile[A](path: os.Path, content: String)(f: => A): A = {
    os.write(path, content, createFolders = true)
    try f
    finally
    // we need to clean up, because we copy files to the target path
    // (which doesn't get cleared automatically on test re-runs)
    os.remove.all(path)
  }

  test(
    "multi-module codegen doesn't trigger upstream compilation when opted out"
  ) {

    object foo extends testKit.BaseModule with ScalaModule {
      override def scalaVersion = "2.13.10"
      override def millSourcePath =
        resourcePath / "multi-module-no-compile" / "foo"
    }

    object bar extends testKit.BaseModule with Smithy4sModule {
      override def moduleDeps = Seq(foo)
      override def scalaVersion = "2.13.10"
      override def ivyDeps = Agg(coreDep)
      override def millSourcePath =
        resourcePath / "multi-module-no-compile" / "bar"

      override def smithy4sInternalDependenciesAsJars = List.empty[PathRef]
    }

    val barEv = testKit.staticTestEvaluator(bar)(FullName("multi-module-bar"))

    taskWorks(bar.smithy4sCodegen, barEv)
  }

  test("multi-module staged codegen works") {

    val localIvyRepo = os.temp.dir() / ".ivy2" / "local"

    trait Base
        extends testKit.BaseModule
        with SbtModule
        with Smithy4sModule
        with PublishModule {
      override def scalaVersion = "2.13.10"
      override def repositoriesTask: Task[Seq[Repository]] = T.task {
        val ivy2Local = IvyRepository.fromPattern(
          (localIvyRepo.toNIO.toUri.toString + "/") +: coursier.ivy.Pattern.default,
          dropInfoAttributes = true
        )
        Seq(ivy2Local) ++ super.repositoriesTask()
      }
      def pomSettings: T[PomSettings] = PomSettings(
        "foo",
        "foobar",
        "http://foobar",
        Seq.empty,
        VersionControl(),
        Seq.empty
      )
      def publishVersion: T[String] = "0.0.1-SNAPSHOT"

    }

    object foo extends Base {
      override def artifactName: T[String] = "foo-mill"
      override def scalaVersion = "2.13.10"
      override def ivyDeps = Agg(coreDep)
      override def smithy4sAllowedNamespaces: T[Option[Set[String]]] =
        Some(Set("aws.api", "foo"))
      override def millSourcePath = resourcePath / "multimodule-staged" / "foo"
      // foo refers to smithy-aws-traits explicitly as a code-gen only dep, and upon publishing,
      // this information is stored in the manifest of bar's jar, for downstream consumption
      override def smithy4sIvyDeps = Agg(
        ivy"software.amazon.smithy:smithy-aws-traits:${smithy4s.codegen.BuildInfo.smithyVersion}"
      )
    }

    object bar extends Base {
      override def artifactName: T[String] = "bar-mill"
      override def scalaVersion = "2.13.10"
      // bar depend on foo as a library, and an assumption is made that bar may depend on the same smithy models
      // that foo depended on for its own codegen. Therefore, these are retrieved from foo's manifest,
      // resolved and added to the list of jars to seek smithy models from during code generation
      override def ivyDeps = T {
        super.ivyDeps() ++ Agg(
          ivy"${pomSettings().organization}::foo-mill:${publishVersion()}"
        )
      }
      override def millSourcePath = resourcePath / "multimodule-staged" / "bar"
    }

    val fooEv =
      testKit.staticTestEvaluator(foo)(FullName("multi-module-staged-foo"))
    val barEv =
      testKit.staticTestEvaluator(bar)(FullName("multi-module-staged-bar"))

    taskWorks(foo.publishLocal(localIvyRepo.toString()), fooEv)
    taskWorks(bar.compile, barEv)

    checkFileExist(
      barEv.outPath / "smithy4sOutputDir.dest" / "scala" / "bar" / "Bar.scala",
      shouldExist = true
    )
    checkFileExist(
      barEv.outPath / "smithy4sOutputDir.dest" / "scala" / "foo" / "Foo.scala",
      shouldExist = false
    )

  }

  private def compileWorks(
      sm: ScalaModule,
      testEvaluator: testKit.TestEvaluator
  )(implicit loc: Location) =
    taskWorks(sm.compile, testEvaluator)

  private def taskWorks[A](
      task: mill.define.Task[A],
      testEvaluator: testKit.TestEvaluator
  )(implicit loc: Location) = {
    val result = testEvaluator(task).map(_._1)
    assertEquals(
      result.isRight,
      true,
      s"Failed with the following error: ${result.swap.getOrElse("error unavailable")}"
    )
  }

  private def checkFileExist(path: os.Path, shouldExist: Boolean)(implicit
      loc: Location
  ) = {
    if (!os.exists(path) && shouldExist) {
      fail(s"${path} file not found")
    }
    if (os.exists(path) && !shouldExist) {
      fail(s"${path} file should not exist")
    }
  }
}
