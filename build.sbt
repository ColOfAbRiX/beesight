import org.typelevel.scalacoptions.ScalacOptions

val scala3Version = "3.8.4"

Global / run / fork              := true
Global / tpolecatExcludeOptions ++=
  Set(
    ScalacOptions.warnUnusedImports,
    ScalacOptions.warnUnusedPrivates,
    ScalacOptions.warnUnusedLocals,
    ScalacOptions.privateKindProjector,
  )
lazy val commonSettings = Seq(
  organization      := "com.colofabrix.scala",
  version           := "1.0.0",
  scalaVersion      := scala3Version,
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  scalacOptions     += "-preview",
  Test / tpolecatScalacOptions := Set.empty,
  Test / fork                  := true,
)

// The CLI application is the root project. The detection engine is a library subproject.
lazy val beesight =
  project
    .in(file("."))
    .aggregate(beesightDetection)
    .dependsOn(beesightDetection % "compile->compile;test->test")
    .enablePlugins(GraalVMNativeImagePlugin)
    .settings(commonSettings)
    .settings(
      name := "beesight",
      libraryDependencies ++= List(
        "co.fs2"                %% "fs2-core"                   % "3.13.0",
        "co.fs2"                %% "fs2-io"                     % "3.13.0",
        "com.colofabrix.scala"  %% "declinio"                   % "1.0.0",
        "com.monovore"          %% "decline"                    % "2.6.2",
        "de.lhns"               %% "fs2-compress-zip"           % "2.3.2",
        "io.github.iltotore"    %% "iron-decline"               % "3.3.2",
        "io.github.iltotore"    %% "iron"                       % "3.3.2",
        "org.gnieh"             %% "fs2-data-csv"               % "1.14.1",
        "com.github.pureconfig" %% "pureconfig-core"            % "0.17.10",
        "com.github.pureconfig" %% "pureconfig-generic-scala3"  % "0.17.10",
        "org.scalatest"         %% "scalatest"                  % "3.2.20" % Test,
        "org.typelevel"         %% "cats-core"                  % "2.13.0",
        "org.typelevel"         %% "cats-effect-testing-scalatest" % "1.8.0" % Test,
        "org.typelevel"         %% "cats-effect"                % "3.7.0",
      ),
      graalVMNativeImageOptions ++= Seq(
        "--no-fallback",
        "--initialize-at-build-time",
        "-H:+UnlockExperimentalVMOptions",
        "-H:IncludeResources=reference.conf",
        "-H:ConfigurationFileDirectories=src/main/resources/META-INF/native-image",
      ),
    )

lazy val beesightDetection =
  project
    .in(file("beesight-detection"))
    .settings(commonSettings)
    .settings(
      name := "beesight-detection",
      libraryDependencies ++= List(
        "co.fs2"                %% "fs2-core"                   % "3.13.0",
        "io.github.iltotore"    %% "iron"                       % "3.3.2",
        "org.gnieh"             %% "fs2-data-csv"               % "1.14.1" % Test,
        "org.typelevel"         %% "cats-core"                  % "2.13.0",
        "org.typelevel"         %% "cats-effect"                % "3.7.0",
        "org.scalatest"         %% "scalatest"                  % "3.2.20" % Test,
        "org.typelevel"         %% "cats-effect-testing-scalatest" % "1.8.0" % Test,
      ),
    )

addCommandAlias(
  "styleApply",
  "; set ThisBuild / scalacOptions += \"-Wunused:all\"; scalafixAll; session clear; scalafmtAll"
)
addCommandAlias(
  "styleCheck",
  "; set ThisBuild / scalacOptions += \"-Wunused:all\"; scalafixAll --check; session clear; scalafmtCheckAll"
)
addCommandAlias(
  "nativeImage",
  "GraalVMNativeImage / packageBin"
)
