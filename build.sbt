ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"
ThisBuild / semanticdbEnabled := true
ThisBuild / scalacOptions ++= Seq(
  "-Wunused:all",
  "-Wunused:unsafe-warn-patvars"
)

addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt")
addCommandAlias("fmtCheck", ";scalafmtCheckAll;scalafmtSbtCheck")
addCommandAlias("lint", ";compile;Test/compile;scalafixAll")
addCommandAlias("lintCheck", ";compile;Test/compile;scalafixAll --check")
addCommandAlias(
  "styleCheck",
  ";scalafmtCheckAll;scalafmtSbtCheck;compile;Test/compile;scalafixAll --check;test"
)

lazy val root = (project in file("."))
  .settings(
    name := "a-small-vm",
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test
  )
