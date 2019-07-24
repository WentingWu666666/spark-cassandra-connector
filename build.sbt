import com.timushev.sbt.updates.UpdatesPlugin.autoImport.dependencyUpdatesFilter
import sbt.moduleFilter
// factor out common settings
ThisBuild / organization := "com.datastax"
ThisBuild / scalaVersion := "2.11.12"
// set the Scala version used for the project
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalacOptions += "-target:jvm-1.8"

lazy val IntegrationTest = config("it") extend Test

lazy val integrationTestsWithFixtures = taskKey[Map[TestDefinition, Seq[String]]]("Evaluates names of all " +
  "Fixtures sub-traits for each test. Sets of fixture sub-traits names are used to form group tests.")

lazy val commonSettings = Seq(
  // dependency updates check
  dependencyUpdatesFailBuild := true,
  dependencyUpdatesFilter -= moduleFilter(organization = "org.scala-lang" | "org.eclipse.jetty")
)

val annotationProcessor = Seq(
//  "-processor", "com.datastax.oss.driver.internal.mapper.processor.MapperProcessor",
  /*"-proc:only",*/ "-XprintRounds", "-XprintProcessorInfo"
)

lazy val root = (project in file("connector"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*) //This and above enables the "it" suite
  .settings(commonSettings)
  .settings(
    // set the name of the project
    name := "DS Analytics Connector",

    // append several options to the list of options passed to the Java compiler
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),

    // append -deprecation to the options passed to the Scala compiler
    scalacOptions += "-deprecation",

    // fork a new JVM for 'run' and 'test:run'
    fork := true,
    parallelExecution := true,
    testForkedParallel := false,

    // test grouping and parallel execution restrictions
    integrationTestsWithFixtures := {
      Testing.testsWithFixtures((testLoader in IntegrationTest).value, (definedTests in IntegrationTest).value)
    },

    Test / javacOptions ++= annotationProcessor ++ Seq("-d", (classDirectory in Test).value.toString),

    IntegrationTest / testGrouping := Testing.makeTestGroups(integrationTestsWithFixtures.value),
    IntegrationTest / testOptions += Tests.Argument("-oF"),

    Global / concurrentRestrictions := Seq(Tags.limitAll(Testing.parallelTasks)),

    libraryDependencies ++= Dependencies.Spark.dependencies
      ++ Dependencies.DataStax.dependencies
      ++ Dependencies.Test.dependencies
      ++ Dependencies.Jetty.dependencies
  )
  .dependsOn(testSupport % "test")

lazy val testSupport = (project in file("test-support"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Dependencies.TestSupport.dependencies
  )
