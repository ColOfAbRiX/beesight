package com.colofabrix.scala.beesight.config

import better.files.File

/**
  * Configuration for the command line tool
  *
  * @param input Input directory
  * @param output Output directory
  * @param processLimit Limits the number of files to process
  * @param dryRun Do a test run without creating output files
  * @param bufferPoints Number of points to keep before or after a landing or takeoff has been detected
  * @param minRetainedPoints Percentage of minimum point the tool must keep. If less that this percentage the whole data will be retained
  * @param detectionConfig Parameters for the flight stages detection algorithm
  */
final case class Config(
  input: File,
  output: Option[File],
  processLimit: Option[Int],
  dryRun: Boolean,
  bufferPoints: Int,
  minRetainedPoints: Double,
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
  */
final case class DetectionConfig(
  WindowTime: Int,
  TakeoffThreshold: Double,
  Influence: Double,
  LandingThreshold: Double,
  IgnoreLandingAbove: Double,
)

object DetectionConfig:

  lazy val Default: DetectionConfig =
    DetectionConfig(
      WindowTime = 400,
      TakeoffThreshold = 3.5,
      Influence = 0.9,
      LandingThreshold = 1.0,
      IgnoreLandingAbove = 600.0,
    )
