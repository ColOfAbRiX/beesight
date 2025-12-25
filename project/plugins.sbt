// Sonatype publishing
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.21")
addSbtPlugin("com.github.sbt" % "sbt-pgp"      % "2.2.1")

// Code quality and formatting
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"              % "0.12.1")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"              % "2.5.2")
addSbtPlugin("org.typelevel"    % "sbt-tpolecat"              % "0.5.2")
