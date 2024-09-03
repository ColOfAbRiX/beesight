package com.colofabrix.scala.beesight

import cats.effect.*
import cats.implicits.*
import cats.Show
import com.colofabrix.scala.beesight.PeakDetection.*
import fs2.*

object Main extends IOApp:

  val WindowTime: Int            = 30 * 5
  val TakeoffThreshold: Double   = 4.0
  val LandingThreshold: Double   = 0.5
  val IgnoreLandingAbove: Double = 600.0
  val BufferPoints: Int          = 500

  def run(args: List[String]): IO[ExitCode] =
    analyzeFile("resources/sample.csv", "resources/output.csv")
      .compile
      .drain
      .handleError(_ => ExitCode.Error)
      .as(ExitCode.Success)

  def analyzeFile(inputPath: String, outputPath: String): Stream[IO, ?] =
    FileOps.readCsv[FlysightPoint](inputPath)
      .through(cutoffData)
      .through(FileOps.writeCsv(outputPath))

  final case class Cutoffs(
    takeoff: Option[Long] = None,
    takeoffGround: Option[Double] = None,
    landing: Option[Long] = None,
    landingGround: Option[Double] = None,
  )

  // def listFile =
  //   os.list()

  val cutoffData: Pipe[IO, FlysightPoint, FlysightPoint] =
    data =>
      data
        .through(PeakDetection(WindowTime, TakeoffThreshold, 0.9).detectStats(_.hMSL))
        .zipWithIndex
        .fold(Cutoffs()) {
          case (Cutoffs(None, None, _, _), ((_, Peak.Stable, stats), i)) =>
            // Initial state, assuming ground
            Cutoffs(None)

          case (cutoffs @ Cutoffs(None, None, _, _), ((value, _, stats), i)) =>
            // Peak found, takeoff detected
            Cutoffs(
              takeoff = Some(i),
              takeoffGround = Some(stats.average),
            )

          case (cutoffs @ Cutoffs(Some(_), _, None, _), ((value, _, stats), i)) =>
            val isAboveIgnoreLine = stats.average < IgnoreLandingAbove
            val isAfterBuffering  = i > cutoffs.takeoff.get + WindowTime
            val isDataStable      = stats.stdDev < LandingThreshold

            if isAboveIgnoreLine && isAfterBuffering && isDataStable then
              // Stable altitude, landing detected
              cutoffs.copy(
                landing = Some(i),
                landingGround = Some(stats.average),
              )
            else
              // In flight
              cutoffs

          case (cutoffs, _) =>
            // Landed
            cutoffs
        }
        .evalTap(printCutoffStats)
        .collect {
          case Cutoffs(None, None, _, _) =>
            data
          case Cutoffs(Some(takeoff), None, _, _) =>
            data.drop(takeoff - BufferPoints)
          case Cutoffs(None, _, Some(landing), _) =>
            data.take(landing + BufferPoints)
          case Cutoffs(Some(takeoff), _, Some(landing), _) =>
            data.drop(takeoff - BufferPoints).take(landing + BufferPoints)
        }
        .flatten

  def printCutoffStats(cutoffs: Cutoffs): IO[Unit] =
    IO.println(s"Cutoff points:") *>
    IO.println(s"    Takeoff: ${cutoffs.takeoff.show}") *>
    IO.println(s"    Takeoff ground level: ${cutoffs.takeoffGround.show}") *>
    IO.println(s"    Landing: ${cutoffs.landing.show}") *>
    IO.println(s"    Landing ground level: ${cutoffs.landingGround.show}")

  given niceOptionShow[A: Show]: Show[Option[A]] =
    _.fold("N/A")(Show[A].show(_))

  given niceDouble: Show[Double] =
    value => f"${value}%.2f"
