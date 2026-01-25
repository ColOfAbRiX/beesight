import org.typelevel.scalacoptions.ScalacOptions

// Project Information

val scala3Version     = "3.7.4"
val catsEffectVersion = "3.5.4"
val declineVersion    = "2.4.1"

// Global Settings

Global / run / fork              := true
Global / onChangedBuildSource    := ReloadOnSourceChanges
Global / tpolecatExcludeOptions ++= Set(ScalacOptions.warnUnusedLocals)
Test / tpolecatScalacOptions     := Set.empty

ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}

lazy val root =
  project
    .in(file("."))
    .settings(
      name         := "declinio",
      version      := "1.0.0",
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

    // Scaladoc settings
    Compile / doc / scalacOptions ++= Seq(
      "-doc-title",
      "Declinio API Documentation",
      "-doc-version",
      version.value,
      "-encoding",
      "UTF-8",
    ),

    // External API mappings for Scaladoc links (required because we use Provided)
    apiMappings ++= {
      val cp = (Compile / fullClasspath).value.map(_.data)

      def findJar(nameContains: String): Option[java.io.File] =
        cp.find(_.getName.contains(nameContains))

      val mappings = Map(
        findJar("cats-effect_3")        -> url("https://typelevel.org/cats-effect/api/3.x/"),
        findJar("cats-effect-kernel_3") -> url("https://typelevel.org/cats-effect/api/3.x/"),
        findJar("cats-effect-std_3")    -> url("https://typelevel.org/cats-effect/api/3.x/"),
        findJar("decline_3")            -> url("https://ben.kirw.in/decline/"),
        findJar("cats-core_3")          -> url("https://typelevel.org/cats/api/"),
      )

      mappings.collect { case (Some(jar), docUrl) => jar -> docUrl }
    },
  )
