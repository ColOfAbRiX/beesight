import org.typelevel.scalacoptions.ScalacOptions

val scala3Version = "3.4.2"

Global / run / fork           := true
Global / onChangedBuildSource := ReloadOnSourceChanges
Global / tpolecatExcludeOptions ++=
  Set(
    ScalacOptions.warnUnusedImports,
    ScalacOptions.warnUnusedPrivates,
    ScalacOptions.warnUnusedLocals,
  )

lazy val root =
  project
    .in(file("."))
    .settings(
      name              := "beesight",
      organization      := "com.colofabrix.scala",
      version           := "0.1.0-SNAPSHOT",
      scalaVersion      := scala3Version,
      scalaVersion      := scala3Version,
      semanticdbEnabled := true,
      semanticdbVersion := scalafixSemanticdb.revision,
      libraryDependencies ++= List(
        "co.fs2"        %% "fs2-core"             % "3.10.2",
        "co.fs2"        %% "fs2-io"               % "3.10.2",
        "org.gnieh"     %% "fs2-data-csv-generic" % "1.11.1",
        "org.gnieh"     %% "fs2-data-csv"         % "1.11.1",
        "org.scalameta" %% "munit"                % "1.0.0" % Test,
        "org.scalanlp"  %% "breeze"               % "2.1.0",
        "org.typelevel" %% "cats-core"            % "2.12.0",
        "org.typelevel" %% "cats-effect"          % "3.5.4",
        // Temporary
        "org.apache.commons" % "commons-math3" % "3.6.1",
      ),
    )
