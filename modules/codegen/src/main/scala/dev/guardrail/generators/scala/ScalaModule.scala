package dev.guardrail.generators.scala

import cats.data.NonEmptyList

import dev.guardrail._
import dev.guardrail.generators._
import dev.guardrail.generators.scala.akkaHttp.{ AkkaHttpClientGenerator, AkkaHttpGenerator, AkkaHttpServerGenerator }
import dev.guardrail.generators.scala.circe.CirceProtocolGenerator
import dev.guardrail.generators.scala.dropwizard.{ DropwizardClientGenerator, DropwizardGenerator, DropwizardServerGenerator }
import dev.guardrail.generators.scala.endpoints.{ EndpointsClientGenerator, EndpointsGenerator, EndpointsServerGenerator }
import dev.guardrail.generators.scala.http4s.{ Http4sClientGenerator, Http4sGenerator, Http4sServerGenerator }
import dev.guardrail.generators.scala.jackson.JacksonProtocolGenerator
import dev.guardrail.terms.client.ClientTerms
import dev.guardrail.terms.protocol._
import dev.guardrail.terms.server.ServerTerms
import dev.guardrail.terms.framework.FrameworkTerms
import dev.guardrail.terms.{ CollectionsLibTerms, LanguageTerms, SwaggerTerms }

object ScalaModule extends AbstractModule[ScalaLanguage] {
  private def catchClassNotFound[A](value: => A, error: => MissingDependency): Target[A] =
    try {
      Target.pure(value)
    } catch {
      case _: _root_.java.lang.NoClassDefFoundError =>
        Target.raiseError(error)
    }

  def circe(circeModelGenerator: CirceModelGenerator)(implicit Cl: CollectionsLibTerms[ScalaLanguage, Target]): (
      ProtocolSupportTerms[ScalaLanguage, Target],
      ModelProtocolTerms[ScalaLanguage, Target],
      EnumProtocolTerms[ScalaLanguage, Target],
      ArrayProtocolTerms[ScalaLanguage, Target],
      PolyProtocolTerms[ScalaLanguage, Target]
  ) = (
    CirceProtocolGenerator.ProtocolSupportTermInterp,
    CirceProtocolGenerator.ModelProtocolTermInterp(circeModelGenerator),
    CirceProtocolGenerator.EnumProtocolTermInterp,
    CirceProtocolGenerator.ArrayProtocolTermInterp,
    CirceProtocolGenerator.PolyProtocolTermInterp
  )

  def circeJava8(circeModelGenerator: CirceModelGenerator)(implicit Cl: CollectionsLibTerms[ScalaLanguage, Target]): (
      ProtocolSupportTerms[ScalaLanguage, Target],
      ModelProtocolTerms[ScalaLanguage, Target],
      EnumProtocolTerms[ScalaLanguage, Target],
      ArrayProtocolTerms[ScalaLanguage, Target],
      PolyProtocolTerms[ScalaLanguage, Target]
  ) = {
    val stockProtocolSupportInterp = CirceProtocolGenerator.ProtocolSupportTermInterp
    val protocolSupportInterp = stockProtocolSupportInterp.copy(
      packageObjectImports = () =>
        stockProtocolSupportInterp.packageObjectImports().map { values =>
          import _root_.scala.meta._
          values :+ q"import io.circe.java8.time._"
        }
    )
    (
      protocolSupportInterp,
      CirceProtocolGenerator.ModelProtocolTermInterp(circeModelGenerator),
      CirceProtocolGenerator.EnumProtocolTermInterp,
      CirceProtocolGenerator.ArrayProtocolTermInterp,
      CirceProtocolGenerator.PolyProtocolTermInterp
    )
  }

  def jackson(implicit Cl: CollectionsLibTerms[ScalaLanguage, Target]): (
      ProtocolSupportTerms[ScalaLanguage, Target],
      ModelProtocolTerms[ScalaLanguage, Target],
      EnumProtocolTerms[ScalaLanguage, Target],
      ArrayProtocolTerms[ScalaLanguage, Target],
      PolyProtocolTerms[ScalaLanguage, Target]
  ) = (
    JacksonProtocolGenerator.ProtocolSupportTermInterp,
    JacksonProtocolGenerator.ModelProtocolTermInterp,
    JacksonProtocolGenerator.EnumProtocolTermInterp,
    JacksonProtocolGenerator.ArrayProtocolTermInterp,
    JacksonProtocolGenerator.PolyProtocolTermInterp
  )

  def akkaHttp(modelGeneratorType: ModelGeneratorType)(implicit Cl: CollectionsLibTerms[ScalaLanguage, Target]): (
      ClientTerms[ScalaLanguage, Target],
      ServerTerms[ScalaLanguage, Target],
      FrameworkTerms[ScalaLanguage, Target]
  ) = (
    AkkaHttpClientGenerator.ClientTermInterp(modelGeneratorType),
    AkkaHttpServerGenerator.ServerTermInterp(modelGeneratorType),
    AkkaHttpGenerator.FrameworkInterp(modelGeneratorType)
  )

  def endpoints(modelGeneratorType: ModelGeneratorType)(implicit Cl: CollectionsLibTerms[ScalaLanguage, Target]): (
      ClientTerms[ScalaLanguage, Target],
      ServerTerms[ScalaLanguage, Target],
      FrameworkTerms[ScalaLanguage, Target]
  ) = (
    EndpointsClientGenerator.ClientTermInterp,
    EndpointsServerGenerator.ServerTermInterp,
    EndpointsGenerator.FrameworkInterp
  )

  def http4s(implicit Cl: CollectionsLibTerms[ScalaLanguage, Target]): (
      ClientTerms[ScalaLanguage, Target],
      ServerTerms[ScalaLanguage, Target],
      FrameworkTerms[ScalaLanguage, Target]
  ) = (
    Http4sClientGenerator.ClientTermInterp,
    Http4sServerGenerator.ServerTermInterp,
    Http4sGenerator.FrameworkInterp
  )

  def dropwizard(implicit Cl: CollectionsLibTerms[ScalaLanguage, Target]): (
      ClientTerms[ScalaLanguage, Target],
      ServerTerms[ScalaLanguage, Target],
      FrameworkTerms[ScalaLanguage, Target]
  ) = (
    DropwizardClientGenerator.ClientTermInterp,
    DropwizardServerGenerator.ServerTermInterp,
    DropwizardGenerator.FrameworkInterp
  )

  def extract(modules: NonEmptyList[String]): Target[Framework[ScalaLanguage, Target]] = {
    implicit val collections = ScalaCollectionsGenerator.ScalaCollectionsInterp
    (for {
      (modelGeneratorType, (protocol, model, enum, array, poly)) <- popModule(
        "json",
        ("circe-java8", catchClassNotFound((CirceModelGenerator.V011, circeJava8(CirceModelGenerator.V011)), MissingDependency("guardrail-scala-support"))),
        ("circe-0.11", catchClassNotFound((CirceModelGenerator.V011, circe(CirceModelGenerator.V011)), MissingDependency("guardrail-scala-support"))),
        ("circe", catchClassNotFound((CirceModelGenerator.V012, circe(CirceModelGenerator.V012)), MissingDependency("guardrail-scala-support"))),
        ("jackson", catchClassNotFound((JacksonModelGenerator, jackson), MissingDependency("guardrail-scala-support")))
      )
      (client, server, framework) <- popModule(
        "framework",
        ("akka-http", catchClassNotFound(akkaHttp(modelGeneratorType), MissingDependency("guardrail-scala-akka-http"))),
        ("http4s", catchClassNotFound(http4s, MissingDependency("guardrail-scala-http4s"))),
        ("endpoints", catchClassNotFound(endpoints(modelGeneratorType), MissingDependency("guardrail-scala-endpoints"))),
        ("dropwizard", catchClassNotFound(dropwizard, MissingDependency("guardrail-scala-dropwizard")))
      )
      // parser             =  or interpFramework
      // codegenApplication = ScalaGenerator.ScalaInterp or parser
    } yield new Framework[ScalaLanguage, Target] {
      def ArrayProtocolInterp: ArrayProtocolTerms[ScalaLanguage, Target]     = array
      def ClientInterp: ClientTerms[ScalaLanguage, Target]                   = client
      def EnumProtocolInterp: EnumProtocolTerms[ScalaLanguage, Target]       = enum
      def FrameworkInterp: FrameworkTerms[ScalaLanguage, Target]             = framework
      def ModelProtocolInterp: ModelProtocolTerms[ScalaLanguage, Target]     = model
      def PolyProtocolInterp: PolyProtocolTerms[ScalaLanguage, Target]       = poly
      def ProtocolSupportInterp: ProtocolSupportTerms[ScalaLanguage, Target] = protocol
      def ServerInterp: ServerTerms[ScalaLanguage, Target]                   = server
      def SwaggerInterp: SwaggerTerms[ScalaLanguage, Target]                 = SwaggerGenerator[ScalaLanguage]
      def LanguageInterp: LanguageTerms[ScalaLanguage, Target]               = ScalaGenerator.ScalaInterp
      def CollectionsLibInterp: CollectionsLibTerms[ScalaLanguage, Target]   = collections
    }).runA(modules.toList.toSet)
  }
}
