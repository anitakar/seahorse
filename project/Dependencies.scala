import sbt._

object Version {
  val akka = "2.4.9"
  val apacheCommons = "3.3.2"
  val avro = "1.7.7"
  val guice = "3.0"
  val h2 = "1.4.191"
  val jclouds = "1.9.0"
  val metricsScala = "3.5.4_a2.3"
  val mockito = "1.10.19"
  val nsscalaTime = "1.8.0"
  val scala = "2.11.8"
  val scalatest = "3.0.0"
  val scalatra = "2.4.0"
  val scoverage = "1.0.4"
  val slick = "3.1.1"
  val spark = "2.0.0"
  val spray = "1.3.3"
  val sprayJson = "1.3.1"
  val wiremock = "1.57"
  val flyway = "4.0"
}

object Library {

  implicit class RichModuleID(m: ModuleID) {
    def excludeAkkaActor: ModuleID = m excludeAll ExclusionRule("com.typesafe.akka")
  }

  val akka = (name: String) => "com.typesafe.akka" %% s"akka-$name" % Version.akka
  val jclouds = (name: String) => "org.apache.jclouds.api" % s"openstack-$name" % Version.jclouds

  val spark = (name: String) => "org.apache.spark" %% s"spark-$name" % Version.spark

  val spray = (name: String) => "io.spray" %% s"spray-$name" % Version.spray excludeAkkaActor

  val akkaActor = akka("actor")
  val akkaAgent = akka("agent")
  val akkaTestkit = akka("testkit")
  val apacheCommons = "org.apache.commons" % "commons-lang3" % Version.apacheCommons
  val apacheCommonsExec = "org.apache.commons" % "commons-exec" % "1.3"
  val guice = "com.google.inject" % "guice" % Version.guice
  val guiceMultibindings = "com.google.inject.extensions" % "guice-multibindings" % Version.guice
  val jcloudsKeystone = jclouds("keystone")
  val jcloudsCompute = "org.apache.jclouds" % "jclouds-compute" % Version.jclouds
  val jcloudsNova = jclouds("nova")
  val jettyWebapp = "org.eclipse.jetty" % "jetty-webapp" % "9.3.8.v20160314"
  val metricsScala = "nl.grons" %% "metrics-scala" % Version.metricsScala excludeAkkaActor
  val mockitoCore = "org.mockito" % "mockito-core" % Version.mockito
  val nscalaTime = "com.github.nscala-time" %% "nscala-time" % Version.nsscalaTime
  val rabbitmq = "com.thenewmotion.akka" %% "akka-rabbitmq" % "2.2" excludeAkkaActor
  val scalaReflect = "org.scala-lang" % "scala-reflect" % Version.scala
  val scalatest = "org.scalatest" %% "scalatest" % Version.scalatest
  val scalatra = "org.scalatra" %% "scalatra" % Version.scalatra
  val scalatraTest = "org.scalatra" %% "scalatra-scalatest" % Version.scalatra % "test"
  val scalaz = "org.scalaz" %% "scalaz-core" % "7.2.5"
  val scoverage = "org.scoverage" %% "scalac-scoverage-runtime" % Version.scoverage
  val slick = "com.typesafe.slick" %% "slick" % Version.slick
  val sparkCore = spark("core")
  val sparkMLLib = spark("mllib")
  val sparkLauncher = spark("launcher")
  val sprayCan = spray("can")
  val sprayRouting = spray("routing")
  val sprayTestkit = spray("testkit")
  val sprayClient = spray("client")
  val sprayJson = "io.spray" %% "spray-json" % Version.sprayJson
  val h2 = "com.h2database" % "h2" % Version.h2
  val wiremock = "com.github.tomakehurst" % "wiremock" % Version.wiremock
  val flyway = "org.flywaydb" % "flyway-core" % Version.flyway
}

object Dependencies {

  import Library._

  // file:// won't work with relative path
  val projectAbsPath = Path.absolute(new File(".")).getAbsolutePath

  val resolvers = Seq(
    "typesafe.com"           at "http://repo.typesafe.com/typesafe/repo/",
    "sonatype.org"           at "https://oss.sonatype.org/content/repositories/releases",
    "spray.io"               at "http://repo.spray.io",
    "The New Motion Public Repo" at "http://nexus.thenewmotion.com/content/groups/public/",
    "embedded-ivy-repo-for-workflow-executor" at
      s"file://$projectAbsPath/seahorse-workflow-executor/target/ds-workflow-executor-ivy-repo",
    Classpaths.typesafeReleases
  )

  object Spark {
    val components = Seq(
      sparkCore,
      sparkMLLib)
    // val provided = components.map(_ % Provided)
    val provided = components
    val test = components.map(_ % s"$Test,it")
    val onlyInTests = provided ++ test
  }

  val commons = Seq(
    akkaActor,
    apacheCommons,
    guice,
    guiceMultibindings,
    jcloudsCompute,
    jcloudsKeystone,
    jcloudsNova,
    metricsScala,
    nscalaTime,
    sprayCan,
    sprayJson,
    sprayRouting
  ) ++ Seq(akkaTestkit, mockitoCore, scalatest, sprayTestkit).map(_ % Test)

  val workflowmanager = Spark.components ++ Seq(
    akkaActor,
    apacheCommons,
    guice,
    guiceMultibindings,
    h2,
    slick,
    flyway,
    sprayCan,
    sprayClient,
    sprayJson,
    sprayRouting
  ) ++ Seq(akkaTestkit, mockitoCore, scalatest, scoverage, sprayTestkit).map(_ % s"$Test,it")

  val sessionmanager = Seq(
    akkaActor,
    akkaAgent,
    apacheCommonsExec,
    h2,
    slick,
    flyway,
    scalaz,
    sprayCan,
    sprayClient,
    sprayJson,
    sprayRouting,
    sparkLauncher
  ) ++ Seq(akkaTestkit, mockitoCore, scalatest, scoverage, sprayTestkit, wiremock)
    .map(_ % s"$Test,it")

  val libraryservice = Seq(
    scalatra,
    jettyWebapp,
    scalatraTest
  ) ++ Seq(scoverage).map(_ % s"$Test,it")

  val integrationtests = Seq(
    "com.typesafe.play" %% "play-ws" % "2.4.3",
    "org.jfarcand" % "wcs" % "1.5",
    scalaz
  ) ++ Seq(scalatest).map(_ % s"$Test,it")
}
