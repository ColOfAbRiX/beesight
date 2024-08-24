package com.colofabrix.scala.beesight

import cats.effect.*
import cats.implicits.*
import com.colofabrix.scala.beesight.PeakDetection.Peak
import fs2.*
import fs2.data.csv.*
import fs2.io.file.{ Files, Flags, Path }

object Main extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    analyzeFile("resources/sample.csv", "resources/output.csv")
      .compile
      .drain
      .handleError(_ => ExitCode.Error)
      .as(ExitCode.Success)

  enum ScanState:
    case Init
    case Skip(previous: Stream[IO, FlysightPoint])
    case Detection(previous: Stream[IO, FlysightPoint], value: FlysightPoint)
    case Output(value: FlysightPoint)

  def analyzeFile(inputPath: String, outputPath: String): Stream[IO, ?] =
    readCsv(inputPath)
      .through(PeakDetection(300, 4.0, 0.0).internalDetect(_.hMSL))
      .scan(ScanState.Init) {
        case (ScanState.Init, (value, Peak.Stable)) =>
          println(s"INIT->SKIP: ${value.hMSL}")
          ScanState.Skip(Stream.emit(value))
        case (ScanState.Init, (value, Peak.PositivePeak | Peak.NegativePeak)) =>
          println(s"INIT->DETECTION: ${value.hMSL}")
          ScanState.Detection(Stream.empty, value)

        case (ScanState.Skip(previous), (value, Peak.Stable)) =>
          println(s"SKIP->SKIP: ${value.hMSL}")
          val output = value.copy(hMSL = 0.0)
          ScanState.Skip(previous.append(Stream.emit(output)))
        case (ScanState.Skip(previous), (value, Peak.PositivePeak | Peak.NegativePeak)) =>
          println(s"SKIP->DETECTION: ${value.hMSL}")
          ScanState.Detection(previous, value)

        case (ScanState.Detection(previous, _), (value, _)) =>
          println(s"DETECTION->OUTPUT: ${value.hMSL}")
          ScanState.Output(value)
        case (ScanState.Output(_), (value, _)) =>
          // println(s"OUTPUT: ${value.hMSL}")
          ScanState.Output(value)
      }
      .collect {
        case ScanState.Detection(previous, value) => previous append Stream.emit(value)
        case ScanState.Output(value)              => Stream.emit(value)
      }
      .flatten
      .through(encodeUsingFirstHeaders(fullRows = true))
      .through(writeCsv(outputPath))

  def readCsv(filePath: String): Stream[IO, FlysightPoint] =
    Files[IO]
      .readAll(Path(filePath), 1024, Flags.Read)
      .through(fs2.text.utf8.decode)
      .through(lenient.attemptDecodeUsingHeaders[FlysightPoint]())
      .collect { case Right(decoded) => decoded }

  def writeCsv(filePath: String): Pipe[IO, String, Nothing] =
    data =>
      data
        .through(text.utf8.encode)
        .through(Files[IO].writeAll(Path(filePath)))
