package com.colofabrix.scala.beesight

import better.files.File

/**
  * Configuration for the tool
  *
  * @param input Input directory
  * @param output Output directory
  * @param processLimit Limits the number of files to process
  * @param dryRun Do a test run without creating output files
  * @param detectionConfig Parameters for the flight stages detection algorithm
  */
final case class Config(
  input: File,
  output: Option[File],
  processLimit: Option[Int],
  dryRun: Boolean,
  detectionConfig: DetectionConfig,
)

/**
  * Parameters for the flight stages detection algorithm
  *
  * @param WindowTime The lag of the moving window (in Flysight steps, usually one every 0.2 sec) that calculates the mean and standard deviation of historical data.
  * @param TakeoffThreshold The z-score at which the algorithm signals that a takeoff has happened
  * @param Influence The influence of new signals on the mean and standard deviation
  * @param LandingThreshold The z-score at which the algorithm signals that a landing has happened
  * @param IgnoreLandingAbove Ignore landing detection above a specified height
  * @param BufferPoints Number of points to keep before or after a landing or takeoff has been detected
  * @param MinRetainedPoints Percentage of minimum point the tool must keep. If less that this percentage the whole data will be retained
  */
final case class DetectionConfig(
  WindowTime: Int,
  TakeoffThreshold: Double,
  Influence: Double,
  LandingThreshold: Double,
  IgnoreLandingAbove: Double,
  BufferPoints: Int,
  MinRetainedPoints: Double
)

object DetectionConfig:

  lazy val Default: DetectionConfig =
    DetectionConfig(
      WindowTime = 30 * 5,
      TakeoffThreshold = 3.5,
      Influence = 0.9,
      LandingThreshold = 1.0,
      IgnoreLandingAbove = 600.0,
      BufferPoints = 500,
      MinRetainedPoints = 0.1
    )
