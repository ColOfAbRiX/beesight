import org.typelevel.scalacoptions.ScalacOptions
import xerial.sbt.Sonatype._

// Project Information

val scala3Version     = "3.7.4"
val catsEffectVersion = "3.5.4"
val declineVersion    = "2.4.1"

// Global Settings

Global / run / fork              := true
Global / onChangedBuildSource    := ReloadOnSourceChanges
Global / tpolecatExcludeOptions ++= Set(ScalacOptions.warnUnusedLocals)

lazy val root =
  project
    .in(file("."))
    .settings(
      name         := "declinio",
      version      := "0.1.0",
      description  := "A Cats Effect integration for Decline command-line parser",
      organization := "com.colofabrix.scala",
      scalaVersion := scala3Version,
      libraryDependencies ++= Seq(
        "com.monovore"  %% "decline"            % declineVersion    % Provided,
        "org.typelevel" %% "cats-effect"        % catsEffectVersion % Provided,
        "org.typelevel" %% "cats-effect-std"    % catsEffectVersion % Provided,
        "org.typelevel" %% "cats-effect-kernel" % catsEffectVersion % Provided,
      ),
      semanticdbEnabled := true,
      semanticdbVersion := scalafixSemanticdb.revision,
    )
    .settings(publishSettings)

// Publishing Settings

lazy val publishSettings =
  Seq(
    homepage             := Some(url("https://github.com/ColOfAbRiX/declinio")),
    startYear            := Some(2025),
    organizationName     := "ColOfAbRiX",
    organizationHomepage := Some(url("https://github.com/ColOfAbRiX")),
    licenses             := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/ColOfAbRiX/declinio"),
        "scm:git@github.com:ColOfAbRiX/declinio.git",
      ),
    ),
    developers := List(
      Developer(
        "ColOfAbRiX",
        "Fabrizio Colonna",
        "colofabrix@tin.it",
        url("https://github.com/ColOfAbRiX"),
      ),
    ),
    pomIncludeRepository := { _ => false },
    publishMavenStyle    := true,
    sonatypeProjectHosting := Some(
      GitHubHosting("ColOfAbRiX", "declinio", "colofabrix@tin.it"),
    ),
    publishTo := {
      if (isSnapshot.value)
        Some(Opts.resolver.sonatypeOssSnapshots.head)
      else
        Some(Opts.resolver.sonatypeStaging)
    },

    // Scaladoc settings
    Compile / doc / scalacOptions ++= Seq(
      "-doc-title",
      "Declinio API Documentation",
      "-doc-version",
      version.value,
      "-encoding",
      "UTF-8",
    ),
  )
