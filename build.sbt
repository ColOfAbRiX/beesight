import org.typelevel.scalacoptions.ScalacOptions

val scala3Version = "3.8.1"

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
lazy val root =
  project
    .in(file("."))
    .aggregate(beesightDetection)
    .dependsOn(beesightDetection % "compile->compile;test->test")
    .enablePlugins(GraalVMNativeImagePlugin)
    .settings(commonSettings)
    .settings(
      name := "beesight-cli",
      libraryDependencies ++= List(
        "co.fs2"                %% "fs2-core"                   % "3.10.2",
        "co.fs2"                %% "fs2-io"                     % "3.10.2",
        "com.colofabrix.scala"  %% "declinio"                   % "1.0.0",
        "com.monovore"          %% "decline"                    % "2.4.1",
        "de.lhns"               %% "fs2-compress-zip"           % "2.3.2",
        "io.github.iltotore"    %% "iron-decline"               % "3.3.0",
        "io.github.iltotore"    %% "iron"                       % "3.3.0",
        "org.gnieh"             %% "fs2-data-csv"               % "1.11.1",
        "com.github.pureconfig" %% "pureconfig-core"            % "0.17.8",
        "com.github.pureconfig" %% "pureconfig-generic-scala3"  % "0.17.8",
        "org.scalatest"         %% "scalatest"                  % "3.2.18" % Test,
        "org.typelevel"         %% "cats-core"                  % "2.12.0",
        "org.typelevel"         %% "cats-effect-testing-scalatest" % "1.7.0" % Test,
        "org.typelevel"         %% "cats-effect"                % "3.5.4",
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
        "co.fs2"                %% "fs2-core"                   % "3.10.2",
        "io.github.iltotore"    %% "iron"                       % "3.3.0",
        "org.typelevel"         %% "cats-core"                  % "2.12.0",
        "org.typelevel"         %% "cats-effect"                % "3.5.4",
        "org.scalatest"         %% "scalatest"                  % "3.2.18" % Test,
        "org.typelevel"         %% "cats-effect-testing-scalatest" % "1.7.0" % Test,
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
