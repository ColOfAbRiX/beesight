import org.typelevel.scalacoptions.ScalacOptions

val scala3Version = "3.7.1"

Global / run / fork           := true
Global / onChangedBuildSource := ReloadOnSourceChanges
Global / tpolecatExcludeOptions ++=
  Set(
    ScalacOptions.warnUnusedImports,
    ScalacOptions.warnUnusedPrivates,
    ScalacOptions.warnUnusedLocals,
    ScalacOptions.privateKindProjector,
  )

lazy val decline =
  project
    .in(file("decline"))
    .settings(
      name              := "decline",
      organization      := "com.colofabrix.scala",
      version           := "0.1.0-SNAPSHOT",
      scalaVersion      := scala3Version,
      semanticdbEnabled := true,
      semanticdbVersion := scalafixSemanticdb.revision,
      libraryDependencies ++= List(
        "com.monovore"  %% "decline"     % "2.4.1", // Command line arguments parser
        "org.typelevel" %% "cats-core"   % "2.12.0",
        "org.typelevel" %% "cats-effect" % "3.5.4",
        // Testing
        "org.scalameta" %% "munit"             % "1.0.0" % Test,
        "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test,
      ),
    )

lazy val root =
  project
    .in(file("."))
    .dependsOn(decline)
    .aggregate(decline)
    .settings(
      name              := "beesight",
      organization      := "com.colofabrix.scala",
      version           := "0.1.0-SNAPSHOT",
      scalaVersion      := scala3Version,
      semanticdbEnabled := true,
      semanticdbVersion := scalafixSemanticdb.revision,
      libraryDependencies ++= List(
        "co.fs2"               %% "fs2-core"             % "3.10.2",
        "co.fs2"               %% "fs2-io"               % "3.10.2", // IO with FS2
        "com.github.pathikrit" %% "better-files"         % "3.9.2",  // Better handling of file system files
        "com.monovore"         %% "decline"              % "2.4.1",  // Command line arguments parser
        "io.github.pityka"     %% "nspl-awt"             % "0.10.0", // Graphs
        "org.gnieh"            %% "fs2-data-csv-generic" % "1.11.1", // CSV manipulation with FS2
        "org.gnieh"            %% "fs2-data-csv"         % "1.11.1", // CSV manipulation with FS2
        "org.scalanlp"         %% "breeze"               % "2.1.0",  // Statistics
        "org.typelevel"        %% "cats-core"            % "2.12.0",
        "org.typelevel"        %% "cats-effect"          % "3.5.4",
        // Testing
        "org.scalameta" %% "munit"             % "1.0.0" % Test,
        "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test,
      ),
    )
