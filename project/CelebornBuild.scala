/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.nio.file.Files
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

import scala.util.Properties

import sbtassembly.AssemblyPlugin.autoImport._
import sbtprotoc.ProtocPlugin.autoImport._

import sbt._
import sbt.Keys._
import Utils._
import CelebornCommonSettings._
// import sbt.Keys.streams

object CelebornCommonSettings {

  // Scala versions
  val SCALA_2_11_12 = "2.11.12"
  val SCALA_2_12_10 = "2.12.10"
  val SCALA_2_12_15 = "2.12.15"
  val SCALA_2_12_17 = "2.12.17"
  val scala213 = "2.13.5"
  val ALL_SCALA_VERSIONS = Seq(SCALA_2_11_12, SCALA_2_12_10, SCALA_2_12_15, SCALA_2_12_17, scala213)

  val DEFAULT_SCALA_VERSION = SCALA_2_12_15

  val projectScalaVersion = defaultScalaVersion()

  val zstdJniVersion = sparkClientProjects.map(_.zstdJniVersion).getOrElse("1.5.2-1")
  val lz4JavaVersion = sparkClientProjects.map(_.lz4JavaVersion).getOrElse("1.8.0")
  
  // Dependent library versions
  val commonsCryptoVersion = "1.0.0"
  val commonsIoVersion = "2.13.0"
  val commonsLang3Version = "3.12.0"
  val findbugsVersion = "1.3.9"
  val guavaVersion = "14.0.1"
  val hadoopVersion = "3.2.4"
  val javaxServletVersion = "3.1.0"
  val leveldbJniVersion = "1.8"
  val log4j2Version = "2.17.2"
  val metricsVersion = "3.2.6"
  val scalatestMockitoVersion = "1.17.14"
  val nettyVersion = "4.1.93.Final"
  val ratisVersion = "2.5.1"
  val roaringBitmapVersion = "0.9.32"
  val slf4jVersion = "1.7.36"
  val snakeyamlVersion = "1.33"
  
  // Versions for proto
  val protocVersion = "3.19.2"
  val protoVersion = "3.19.2"
  
  scalaVersion := projectScalaVersion

  autoScalaLibrary := false
  
  // crossScalaVersions must be set to Nil on the root project
  crossScalaVersions := Nil

  lazy val commonSettings = Seq(
    organization := "org.apache.celeborn",
    scalaVersion := projectScalaVersion,
    crossScalaVersions := ALL_SCALA_VERSIONS,
    fork := true,
    scalacOptions ++= Seq("-target:jvm-1.8"),
    javacOptions ++= Seq("-encoding", UTF_8.name(), "-source", "1.8"),
  
    // -target cannot be passed as a parameter to javadoc. See https://github.com/sbt/sbt/issues/355
    Compile / compile / javacOptions ++= Seq("-target", "1.8"),
  
    // Make sure any tests in any project that uses Spark is configured for running well locally
    Test / javaOptions ++= Seq(
      "-Xmx4g",
      "-XX:+IgnoreUnrecognizedVMOptions",
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
      "--add-opens=java.base/java.io=ALL-UNNAMED",
      "--add-opens=java.base/java.net=ALL-UNNAMED",
      "--add-opens=java.base/java.nio=ALL-UNNAMED",
      "--add-opens=java.base/java.util=ALL-UNNAMED",
      "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
      "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
      "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
      "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
      "--add-opens=java.base/sun.security.action=ALL-UNNAMED",
      "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
      "-Dio.netty.tryReflectionSetAccessible=true"
    ),
  
    testOptions += Tests.Argument("-oF"),

    Test / testOptions += Tests.Argument("-oDF"),
    Test / testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),

    // Don't execute in parallel since we can't have multiple Sparks in the same JVM
    Test / parallelExecution := false,

    javaOptions += "-Xmx4g",

    // Configurations to speed up tests and reduce memory footprint
    Test / javaOptions ++= Seq(
      "-Xmx4g"
    ),

    Test / envVars += ("IS_TESTING", "1")
  )

  lazy val protoSettings = Seq(
    // Setting version for the protobuf compiler
    PB.protocVersion := protocVersion,
    // set proto sources path
    Compile / PB.protoSources := Seq(sourceDirectory.value / "main" / "proto"),
    Compile / PB.targets := Seq(PB.gens.java -> (Compile / sourceManaged).value)
  )

  lazy val commonUnitTestDependencies = Seq(
    "org.mockito" % "mockito-core" % "4.11.0" % "test",
    "org.scalatest" %% "scalatest" % "3.2.16" % "test",
    "junit" % "junit" % "4.13.2" % "test",
    // https://www.scala-sbt.org/1.x/docs/Testing.html
    "com.github.sbt" % "junit-interface" % "0.13.3" % "test")
}

object CelebornBuild extends sbt.internal.BuildDef {
  override def projectDefinitions(baseDirectory: File): Seq[Project] = {
    Seq(
      CelebornCommon.common,
      CelebornClient.client,
      CelebornService.service,
      CelebornWorker.worker,
      CelebornMaster.master) ++ maybeSparkClientModules ++ maybeFlinkClientModules
  }
  
  // ThisBuild / parallelExecution := false

  // scalaVersion := "2.11.12"

  // autoScalaLibrary := false

  crossScalaVersions := Nil

  // load user-defined Profiles
  // loadProfiles()
}

object Utils {
  val profiles = {
    val profiles = Properties.envOrNone("SBT_MAVEN_PROFILES")
      .orElse(Properties.propOrNone("sbt.maven.profiles")) match {
        case None => Seq("sbt")
        case Some(v) =>
          v.split("(\\s+|,)").filterNot(_.isEmpty).map(_.trim.replaceAll("-P", "")).toSeq
      }
      if (profiles.contains("jdwp-test-debug")) {
        sys.props.put("test.jdwp.enabled", "true")
      }
      profiles
  }

  val SPARK_VERSION = profiles.filter(_.startsWith("spark")).headOption

  lazy val sparkClientProjects = SPARK_VERSION match {
    case Some("spark-2.4") => Some(Spark24)
    case Some("spark-3.0") => Some(Spark30)
    case Some("spark-3.1") => Some(Spark31)
    case Some("spark-3.2") => Some(Spark32)
    case Some("spark-3.3") => Some(Spark33)
    case Some("spark-3.4") => Some(Spark34)
    case _ => None
  }

  lazy val maybeSparkClientModules: Seq[Project] = sparkClientProjects.map(_.modules).getOrElse(Seq.empty)

  val FLINK_VERSION = profiles.filter(_.startsWith("flink")).headOption

  lazy val flinkClientProjects = FLINK_VERSION match {
    case Some("flink-1.14") => Some(Flink114)
    case Some("flink-1.15") => Some(Flink115)
    case Some("flink-1.17") => Some(Flink117)
    case _ => None
  }

  lazy val maybeFlinkClientModules: Seq[Project] = flinkClientProjects.map(_.modules).getOrElse(Seq.empty)

  def defaultScalaVersion(): String = {
    // 1. Inherit the scala version of the spark project
    // 2. if the spark profile not specified, using the DEFAULT_SCALA_VERSION
    val v = sparkClientProjects.map(_.sparkProjectScalaVersion).getOrElse(DEFAULT_SCALA_VERSION)
    require(ALL_SCALA_VERSIONS.contains(v), s"found not allow scala version: $v")
    v
  }
}

object CelebornCommon {
  lazy val common = Project("celeborn-common", file("common"))
    .settings (
      commonSettings,
      protoSettings,
      libraryDependencies ++= Seq(
        "com.google.protobuf" % "protobuf-java" % protoVersion % "protobuf",
        "com.google.code.findbugs" % "jsr305" % findbugsVersion,
        "com.google.guava" % "guava" % guavaVersion,
        "commons-io" % "commons-io" % commonsIoVersion,
        "io.dropwizard.metrics" % "metrics-core" % metricsVersion,
        "io.dropwizard.metrics" % "metrics-graphite" % metricsVersion,
        "io.dropwizard.metrics" % "metrics-jvm" % metricsVersion,
        "io.netty" % "netty-all" % nettyVersion,
        "org.apache.commons" % "commons-crypto" % commonsCryptoVersion,
        "org.apache.commons" % "commons-lang3" % commonsLang3Version,
        "org.apache.hadoop" % "hadoop-client-api" % hadoopVersion,
        "org.apache.hadoop" % "hadoop-client-runtime" % hadoopVersion,
        "org.apache.ratis" % "ratis-client" % ratisVersion,
        "org.apache.ratis" % "ratis-common" % ratisVersion,
        "org.fusesource.leveldbjni" % "leveldbjni-all" % leveldbJniVersion,
        "org.roaringbitmap" % "RoaringBitmap" % roaringBitmapVersion,
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
        "org.slf4j" % "jul-to-slf4j" % slf4jVersion,
        "org.slf4j" % "slf4j-api" % slf4jVersion,
        "org.yaml" % "snakeyaml" % snakeyamlVersion,
        "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version % "test",
        "org.apache.logging.log4j" % "log4j-1.2-api" % log4j2Version % "test"
      ) ++ commonUnitTestDependencies,

      Compile / sourceGenerators += Def.task {
        val file = (Compile / sourceManaged).value / "org" / "apache" / "celeborn" / "package.scala"
        streams.value.log.info(s"geneate version information file ${file.toPath}")
        IO.write(file,
          s"""package org.apache
             |
             |package object celeborn {
             |  val VERSION = "${version.value}"
             |}
             |""".stripMargin)
        Seq(file)
        // generate version task depends on PB generate to avoid concurrency generate source files
      }.dependsOn(Compile / PB.generate),
  
      // a task to show current profiles
      printProfiles := {
        val message = profiles.mkString("", " ", "")
        println("compile with profiles: %s".format(message))
      }
    )

    lazy val printProfiles = taskKey[Unit]("Prints Profiles")
}

object CelebornClient {
  lazy val client = Project("celeborn-client", file("client"))
    // ref: https://www.scala-sbt.org/1.x/docs/Multi-Project.html#Classpath+dependencies
    .dependsOn(CelebornCommon.common % "test->test;compile->compile")
    .settings (
      commonSettings,
      libraryDependencies ++= Seq(
        "io.netty" % "netty-all" % nettyVersion,
        "com.google.guava" % "guava" % guavaVersion,
        "org.lz4" % "lz4-java" % lz4JavaVersion,
        "com.github.luben" % "zstd-jni" % zstdJniVersion,
        "org.apache.commons" % "commons-lang3" % commonsLang3Version,
        "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version % "test",
        "org.apache.logging.log4j" % "log4j-1.2-api" % log4j2Version % "test"
      ) ++ commonUnitTestDependencies
    )
}

object CelebornService {
  lazy val service = Project("celeborn-service", file("service"))
    .dependsOn(CelebornCommon.common)
    .settings (
      commonSettings,
      libraryDependencies ++= Seq(
        "com.google.code.findbugs" % "jsr305" % findbugsVersion,
        "commons-io" % "commons-io" % commonsIoVersion,
        "io.netty" % "netty-all" % nettyVersion,
        "javax.servlet" % "javax.servlet-api" % javaxServletVersion,
        "org.apache.commons" % "commons-crypto" % commonsCryptoVersion,
        "org.slf4j" % "slf4j-api" % slf4jVersion,
        "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version % "test",
        "org.apache.logging.log4j" % "log4j-1.2-api" % log4j2Version % "test"
      ) ++ commonUnitTestDependencies
    )
}

object CelebornMaster {
  lazy val master = Project("celeborn-master", file("master"))
    .dependsOn(CelebornCommon.common, CelebornService.service)
    .settings (
      commonSettings,
      protoSettings,
      libraryDependencies ++= Seq(
        "com.google.guava" % "guava" % guavaVersion,
        "com.google.protobuf" % "protobuf-java" % protoVersion,
        "io.netty" % "netty-all" % nettyVersion,
        "org.apache.hadoop" % "hadoop-client-api" % hadoopVersion,
        "org.apache.logging.log4j" % "log4j-1.2-api" % log4j2Version,
        "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version,
        "org.apache.ratis" % "ratis-client" % ratisVersion,
        "org.apache.ratis" % "ratis-common" % ratisVersion,
        "org.apache.ratis" % "ratis-grpc" % ratisVersion,
        "org.apache.ratis" % "ratis-netty" % ratisVersion,
        "org.apache.ratis" % "ratis-server" % ratisVersion,
        "org.apache.ratis" % "ratis-shell" % ratisVersion,
      ) ++ commonUnitTestDependencies
    )
}

object CelebornWorker {
  lazy val worker = Project("celeborn-worker", file("worker"))
    .dependsOn(CelebornCommon.common, CelebornService.service)
    .dependsOn(CelebornClient.client % "test->test;compile->compile")
    .dependsOn(CelebornMaster.master % "test->test;compile->compile")
    .settings (
      commonSettings,
      libraryDependencies ++= Seq(
        "com.google.guava" % "guava" % guavaVersion,
        "commons-io" % "commons-io" % commonsIoVersion,
        "io.netty" % "netty-all" % nettyVersion,
        "org.apache.logging.log4j" % "log4j-1.2-api" % log4j2Version,
        "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version,
        "org.fusesource.leveldbjni" % "leveldbjni-all" % leveldbJniVersion,
        "org.roaringbitmap" % "RoaringBitmap" % roaringBitmapVersion,
        "org.mockito" %% "mockito-scala-scalatest" % scalatestMockitoVersion % "test",
      ) ++ commonUnitTestDependencies
    )
}

////////////////////////////////////////////////////////
//                   Spark Client                     //
////////////////////////////////////////////////////////

object Spark24 extends SparkClientProjects {

  val sparkClientProjectPath = "client-spark/spark-2"
  val sparkClientProjectName = "celeborn-client-spark-2"
  val sparkClientShadedProjectPath = "client-spark/spark-2-shaded"
  val sparkClientShadedProjectName = "celeborn-client-spark-2-shaded"

  // val jacksonVersion = "2.5.7"
  // val jacksonDatabindVersion = "2.6.7.3"
  val lz4JavaVersion = "1.4.0"
  val sparkProjectScalaVersion = "2.11.12"
  // scalaBinaryVersion
  // val scalaBinaryVersion = "2.11"
  val sparkVersion = "2.4.8"
  val zstdJniVersion = "1.4.4-3"
}

object Spark30 extends SparkClientProjects {

  val sparkClientProjectPath = "client-spark/spark-3"
  val sparkClientProjectName = "celeborn-client-spark-3"
  val sparkClientShadedProjectPath = "client-spark/spark-3-shaded"
  val sparkClientShadedProjectName = "celeborn-client-spark-3-shaded"

  val lz4JavaVersion = "1.7.1"
  val sparkProjectScalaVersion = "2.12.10"

  val sparkVersion = "3.0.3"
  val zstdJniVersion = "1.4.4-3"
}

object Spark31 extends SparkClientProjects {

  val sparkClientProjectPath = "client-spark/spark-3"
  val sparkClientProjectName = "celeborn-client-spark-3"
  val sparkClientShadedProjectPath = "client-spark/spark-3-shaded"
  val sparkClientShadedProjectName = "celeborn-client-spark-3-shaded"

  val lz4JavaVersion = "1.7.1"
  val sparkProjectScalaVersion = "2.12.10"

  val sparkVersion = "3.1.3"
  val zstdJniVersion = "1.4.8-1"
}

object Spark32 extends SparkClientProjects {

  val sparkClientProjectPath = "client-spark/spark-3"
  val sparkClientProjectName = "celeborn-client-spark-3"
  val sparkClientShadedProjectPath = "client-spark/spark-3-shaded"
  val sparkClientShadedProjectName = "celeborn-client-spark-3-shaded"

  val lz4JavaVersion = "1.7.1"
  val sparkProjectScalaVersion = "2.12.15"

  val sparkVersion = "3.2.4"
  val zstdJniVersion = "1.5.0-4"
}

object Spark33 extends SparkClientProjects {

  val sparkClientProjectPath = "client-spark/spark-3"
  val sparkClientProjectName = "celeborn-client-spark-3"
  val sparkClientShadedProjectPath = "client-spark/spark-3-shaded"
  val sparkClientShadedProjectName = "celeborn-client-spark-3-shaded"

  // val jacksonVersion = "2.13.4"
  // val jacksonDatabindVersion = "2.13.4.2"
  val lz4JavaVersion = "1.8.0"
  val sparkProjectScalaVersion = "2.12.15"
  // scalaBinaryVersion
  // val scalaBinaryVersion = "2.12"
  val sparkVersion = "3.3.2"
  val zstdJniVersion = "1.5.2-1"
}

object Spark34 extends SparkClientProjects {

  val sparkClientProjectPath = "client-spark/spark-3"
  val sparkClientProjectName = "celeborn-client-spark-3"
  val sparkClientShadedProjectPath = "client-spark/spark-3-shaded"
  val sparkClientShadedProjectName = "celeborn-client-spark-3-shaded"

  val lz4JavaVersion = "1.8.0"
  val sparkProjectScalaVersion = "2.12.17"

  val sparkVersion = "3.4.1"
  val zstdJniVersion = "1.5.2-5"

  lazy val deps = Seq(
    // Spark Use `log4j-slf4j2-impl` instead of `log4j-slf4j-impl` in SPARK-40511
    // to fix the error:
    // ```
    //   java.lang.NoSuchMethodError: org.apache.logging.slf4j.Log4jLoggerFactory.<init>(Lorg/apache/logging/slf4j/Log4jMarkerFactory;)V
    // ```
    "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.19.0" % "test"
  )

  override def sparkCommon: Project = {
    super.sparkCommon
      .settings(libraryDependencies ++= deps)
  }

  override def sparkClient: Project = {
    super.sparkClient
      .settings(libraryDependencies ++= deps)
  }

  override def sparkIt: Project = {
    super.sparkIt
      .settings(libraryDependencies ++= deps)
  }
}

trait SparkClientProjects {

  val sparkClientProjectPath: String
  val sparkClientProjectName: String
  val sparkClientShadedProjectPath: String
  val sparkClientShadedProjectName: String

  val lz4JavaVersion: String
  val sparkProjectScalaVersion: String
  val sparkVersion: String
  val zstdJniVersion: String

  def modules: Seq[Project] = Seq(sparkCommon, sparkClient, sparkIt, sparkGroup, sparkClientShade)

  // for test only, don't use this group for any other projects
  lazy val sparkGroup = (project withId "celeborn-spark-group")
    .aggregate(sparkCommon, sparkClient, sparkIt)

  def sparkCommon: Project = {
    Project("celeborn-spark-common", file("client-spark/common"))
      .dependsOn(CelebornCommon.common)
      // ref: https://www.scala-sbt.org/1.x/docs/Multi-Project.html#Classpath+dependencies
      .dependsOn(CelebornClient.client % "test->test;compile->compile")
      .settings (
        commonSettings,
        libraryDependencies ++= Seq(
          "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
          "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
          "org.apache.spark" %% "spark-core" % sparkVersion % "test" classifier "tests"
        ) ++ commonUnitTestDependencies
      )
  }
  
  def sparkClient: Project = {
    Project(sparkClientProjectName, file(sparkClientProjectPath))
      .dependsOn(CelebornCommon.common, sparkCommon)
      // ref: https://www.scala-sbt.org/1.x/docs/Multi-Project.html#Classpath+dependencies
      .dependsOn(CelebornClient.client % "test->test;compile->compile")
      .settings (
        commonSettings,
        libraryDependencies ++= Seq(
          "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
          "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
        ) ++ commonUnitTestDependencies
      )
  }
  
  def sparkIt: Project = {
    Project("celeborn-spark-it", file("tests/spark-it"))
      // ref: https://www.scala-sbt.org/1.x/docs/Multi-Project.html#Classpath+dependencies
      .dependsOn(CelebornCommon.common % "test->test;compile->compile")
      .dependsOn(CelebornClient.client % "test->test;compile->compile")
      .dependsOn(CelebornMaster.master % "test->test;compile->compile")
      .dependsOn(CelebornWorker.worker % "test->test;compile->compile")
      .dependsOn(sparkClient % "test->test;compile->compile")
      .settings (
        commonSettings,
        libraryDependencies ++= Seq(
          "org.apache.spark" %% "spark-core" % sparkVersion % "test",
          "org.apache.spark" %% "spark-sql" % sparkVersion % "test",
          "org.apache.spark" %% "spark-core" % sparkVersion % "test" classifier "tests",
          "org.apache.spark" %% "spark-sql" % sparkVersion % "test" classifier "tests"
        ) ++ commonUnitTestDependencies
      )
  }

  def sparkClientShade: Project = {
    Project(sparkClientShadedProjectName, file(sparkClientShadedProjectPath))
      .dependsOn(sparkClient)
      .settings (
        commonSettings,

        // align final shaded jar name with maven.
        (assembly / assemblyJarName) := {
          val extension = artifact.value.extension
          s"${moduleName.value}_${scalaBinaryVersion.value}-${version.value}.$extension"
        },
  
        (assembly / test) := { },
  
        (assembly / logLevel) := Level.Info,
  
        // Exclude `scala-library` from assembly.
        (assembly / assemblyPackageScala / assembleArtifact) := false,
  
        (assembly / assemblyExcludedJars) := {
          val cp = (assembly / fullClasspath).value
          cp filter { v =>
            val name = v.data.getName
            !(name.startsWith("celeborn-") || name.startsWith("protobuf-java-") ||
              name.startsWith("guava-") || name.startsWith("netty-") || name.startsWith("commons-lang3-"))
          }
        },
  
        (assembly / assemblyShadeRules) := Seq(
          ShadeRule.rename("com.google.protobuf.**" -> "org.apache.celeborn.shaded.com.google.protobuf.@1").inAll,
          ShadeRule.rename("com.google.common.**" -> "org.apache.celeborn.shaded.com.google.common.@1").inAll,
          ShadeRule.rename("io.netty.**" -> "org.apache.celeborn.shaded.io.netty.@1").inAll,
          ShadeRule.rename("org.apache.commons.**" -> "org.apache.celeborn.shaded.org.apache.commons.@1").inAll
        ),
  
        (assembly / assemblyMergeStrategy) := {
          case m if m.toLowerCase(Locale.ROOT).endsWith("manifest.mf") => MergeStrategy.discard
          // Drop all proto files that are not needed as artifacts of the build.
          case m if m.toLowerCase(Locale.ROOT).endsWith(".proto") => MergeStrategy.discard
          case m if m.toLowerCase(Locale.ROOT).startsWith("meta-inf/native-image") => MergeStrategy.discard
          // Drop netty jnilib
          case m if m.toLowerCase(Locale.ROOT).endsWith(".jnilib") => MergeStrategy.discard
          // rename netty native lib
          case "META-INF/native/libnetty_transport_native_epoll_x86_64.so" => CustomMergeStrategy.rename( _ => "META-INF/native/liborg_apache_celeborn_shaded_netty_transport_native_epoll_x86_64.so" )
          case "META-INF/native/libnetty_transport_native_epoll_aarch_64.so" => CustomMergeStrategy.rename( _ => "META-INF/native/liborg_apache_celeborn_shaded_netty_transport_native_epoll_aarch_64.so" )
          case _ => MergeStrategy.first
        }
      )
  }
}

////////////////////////////////////////////////////////
//                   Flink Client                     //
////////////////////////////////////////////////////////

object Flink114 extends FlinkClientProjects {
  val flinkVersion = "1.14.6"

  // note that SBT does not allow using the period symbol (.) in project names.
  val flinkClientProjectPath = "client-flink/flink-1.14"
  val flinkClientProjectName = "celeborn-client-flink-1_14"
  val flinkClientShadedProjectPath: String = "client-flink/flink-1.14-shaded"
  val flinkClientShadedProjectName: String = "celeborn-client-flink-1_14-shaded"

  override lazy val flinkStreamingDependency: ModuleID = "org.apache.flink" %% "flink-streaming-java" % flinkVersion % "test"
  override lazy val flinkClientsDependency: ModuleID = "org.apache.flink" %% "flink-clients" % flinkVersion % "test"
  override lazy val flinkRuntimeWebDependency: ModuleID = "org.apache.flink" %% "flink-runtime-web" % flinkVersion % "test"
}

object Flink115 extends FlinkClientProjects {
  val flinkVersion = "1.15.4"

  // note that SBT does not allow using the period symbol (.) in project names.
  val flinkClientProjectPath = "client-flink/flink-1.15"
  val flinkClientProjectName = "celeborn-client-flink-1_15"
  val flinkClientShadedProjectPath: String = "client-flink/flink-1.15-shaded"
  val flinkClientShadedProjectName: String = "celeborn-client-flink-1_15-shaded"
}

object Flink117 extends FlinkClientProjects {
  val flinkVersion = "1.17.0"

  // note that SBT does not allow using the period symbol (.) in project names.
  val flinkClientProjectPath = "client-flink/flink-1.17"
  val flinkClientProjectName = "celeborn-client-flink-1_17"
  val flinkClientShadedProjectPath: String = "client-flink/flink-1.17-shaded"
  val flinkClientShadedProjectName: String = "celeborn-client-flink-1_17-shaded"
}

trait FlinkClientProjects {

  val flinkVersion: String

  // note that SBT does not allow using the period symbol (.) in project names.
  val flinkClientProjectPath: String
  val flinkClientProjectName: String
  val flinkClientShadedProjectPath: String
  val flinkClientShadedProjectName: String

  lazy val flinkStreamingDependency: ModuleID = "org.apache.flink" % "flink-streaming-java" % flinkVersion % "test"
  lazy val flinkClientsDependency: ModuleID = "org.apache.flink" % "flink-clients" % flinkVersion % "test"
  lazy val flinkRuntimeWebDependency: ModuleID = "org.apache.flink" % "flink-runtime-web" % flinkVersion % "test"

  def modules: Seq[Project] = Seq(flinkCommon, flinkClient, flinkIt, flinkGroup, flinkClientShade)

  // for test only, don't use this group for any other projects
  lazy val flinkGroup = (project withId "celeborn-flink-group")
    .aggregate(flinkCommon, flinkClient, flinkIt)

  // get flink major version. e.g:
  //   1.17.0 -> 1.17
  //   1.15.4 -> 1.15
  //   1.14.6 -> 1.14
  lazy val flinkMajorVersion: String = flinkVersion.split("\\.").take(2).reduce(_ + "." + _)

  // the output would be something like: celeborn-client-flink-1.17_2.12-0.4.0-SNAPSHOT.jar
  def flinkClientJarName(
      module: ModuleID,
      artifact: Artifact,
      scalaBinaryVersionString: String): String =
    s"celeborn-client-flink-${flinkMajorVersion}_$scalaBinaryVersionString" + "-" + module.revision + "." + artifact.extension

  // the output would be something like: celeborn-client-flink-1.17-shaded_2.12-0.4.0-SNAPSHOT.jar
  def flinkClientShadeJarName(
      revision: String,
      artifact: Artifact,
      scalaBinaryVersionString: String): String =
    s"celeborn-client-flink-$flinkMajorVersion-shaded_$scalaBinaryVersionString" + "-" + revision + "." + artifact.extension

  def flinkCommon: Project = {
    Project("celeborn-flink-common", file("client-flink/common"))
      .dependsOn(CelebornCommon.common, CelebornClient.client)
      .settings (
        commonSettings,
        libraryDependencies ++= Seq(
          "org.apache.flink" % "flink-runtime" % flinkVersion % "provided"
        ) ++ commonUnitTestDependencies
      )
  }

  def flinkClient: Project = {
    Project(flinkClientProjectName, file(flinkClientProjectPath))
      .dependsOn(CelebornCommon.common, CelebornClient.client, flinkCommon)
      .settings (
        commonSettings,

        // 1. reference for modifying the jar name.
        // https://stackoverflow.com/questions/52771831/how-to-modify-jar-name-generate-by-cmd-sbt-package
        // 2. since SBT doesn't allow using `.` in the project name, explicitly setting the artifact Name
        artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
          flinkClientJarName(module, artifact, scalaBinaryVersion.value)
        },

        libraryDependencies ++= Seq(
          "org.apache.flink" % "flink-runtime" % flinkVersion % "provided",
          "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version % "test",
          "org.apache.logging.log4j" % "log4j-1.2-api" % log4j2Version % "test"
        ) ++ commonUnitTestDependencies
      )
  }

  def flinkIt: Project = {
    Project("celeborn-flink-it", file("tests/flink-it"))
      // ref: https://www.scala-sbt.org/1.x/docs/Multi-Project.html#Classpath+dependencies
      .dependsOn(CelebornCommon.common % "test->test;compile->compile")
      .dependsOn(CelebornClient.client % "test->test;compile->compile")
      .dependsOn(CelebornMaster.master % "test->test;compile->compile")
      .dependsOn(CelebornWorker.worker % "test->test;compile->compile")
      .dependsOn(flinkClient % "test->test;compile->compile")
      .settings (
        commonSettings,
        libraryDependencies ++= Seq(
          "org.apache.flink" % "flink-runtime" % flinkVersion % "test",
          "org.apache.flink" %% "flink-scala" % flinkVersion % "test",
          flinkStreamingDependency,
          flinkClientsDependency,
          flinkRuntimeWebDependency
        ) ++ commonUnitTestDependencies
      )
  }

  def flinkClientShade: Project = {
    Project(flinkClientShadedProjectName, file(flinkClientShadedProjectPath))
      .dependsOn(flinkClient)
      .settings (
        commonSettings,
  
        (assembly / test) := { },

        (assembly / assemblyJarName) := {
            val revision: String = version.value
            val artifactValue: Artifact = artifact.value
            flinkClientShadeJarName(revision, artifactValue, scalaBinaryVersion.value)
        },
  
        (assembly / logLevel) := Level.Info,
  
        // Exclude `scala-library` from assembly.
        (assembly / assemblyPackageScala / assembleArtifact) := false,
  
        (assembly / assemblyExcludedJars) := {
          val cp = (assembly / fullClasspath).value
          cp filter { v =>
            val name = v.data.getName
            !(name.startsWith("celeborn-") ||
                name.startsWith("protobuf-java-") ||
                name.startsWith("guava-") ||
                name.startsWith("netty-") ||
                name.startsWith("commons-lang3-") ||
                name.startsWith("RoaringBitmap-"))
          }
        },
  
        (assembly / assemblyShadeRules) := Seq(
          ShadeRule.rename("com.google.protobuf.**" -> "org.apache.celeborn.shaded.com.google.protobuf.@1").inAll,
          ShadeRule.rename("com.google.common.**" -> "org.apache.celeborn.shaded.com.google.common.@1").inAll,
          ShadeRule.rename("io.netty.**" -> "org.apache.celeborn.shaded.io.netty.@1").inAll,
          ShadeRule.rename("org.apache.commons.**" -> "org.apache.celeborn.shaded.org.apache.commons.@1").inAll,
          ShadeRule.rename("org.roaringbitmap.**" -> "org.apache.celeborn.shaded.org.roaringbitmap.@1").inAll
        ),
  
        (assembly / assemblyMergeStrategy) := {
          case m if m.toLowerCase(Locale.ROOT).endsWith("manifest.mf") => MergeStrategy.discard
          // Drop all proto files that are not needed as artifacts of the build.
          case m if m.toLowerCase(Locale.ROOT).endsWith(".proto") => MergeStrategy.discard
          case m if m.toLowerCase(Locale.ROOT).startsWith("meta-inf/native-image") => MergeStrategy.discard
          // Drop netty jnilib
          case m if m.toLowerCase(Locale.ROOT).endsWith(".jnilib") => MergeStrategy.discard
          // rename netty native lib
          case "META-INF/native/libnetty_transport_native_epoll_x86_64.so" => CustomMergeStrategy.rename( _ => "META-INF/native/liborg_apache_celeborn_shaded_netty_transport_native_epoll_x86_64.so" )
          case "META-INF/native/libnetty_transport_native_epoll_aarch_64.so" => CustomMergeStrategy.rename( _ => "META-INF/native/liborg_apache_celeborn_shaded_netty_transport_native_epoll_aarch_64.so" )
          case _ => MergeStrategy.first
        }
      )
  }
}
