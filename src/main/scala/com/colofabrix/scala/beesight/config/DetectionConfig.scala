package com.colofabrix.scala.beesight.config

final case class DetectionConfig(
  TakeoffSpeedThreshold: Double,          // m/s - horizontal speed above this indicates takeoff
  TakeoffClimbRate: Double,               // m/s - verticalSpeed below this indicates climbing (negative)
  TakeoffMaxAltitude: Double,             // m - takeoff cannot happen above this altitude
  FreefallWindow: Int,                    // Data Points - Size of the CUSUM Freefall detection window
  FreefallVerticalSpeedThreshold: Double, // m/s - verticalSpeed above this indicates freefall
  FreefallAccelThreshold: Double,         // m/s per sample - rapid verticalSpeed increase indicates exit
  FreefallAccelMinVelocity: Double,       // m/s - minimum verticalSpeed for accel-based detection
  FreefallMinAltitudeAbove: Double,       // m - freefall must be at least this high above takeoff
  FreefallMinAltitudeAbsolute: Double,    // m - freefall min altitude when takeoff missed
  CanopyWindow: Int,                      // Data Points - Size of the CUSUM Canopy detection window
  CanopyVerticalSpeedMax: Double,         // m/s - verticalSpeed below this after freefall indicates canopy
  LandingSpeedMax: Double,                // m/s - total speed below this indicates landing
  LandingStabilityThreshold: Double,      // m/s - stddev of vertical speed must be below this
  LandingMeanVerticalSpeedMax: Double,    // m/s - mean vertical speed must be below this
  LandingAltitudeTolerance: Double,       // m - landing must be within ±this of takeoff altitude
  MedianFilterWindow: Int,                // points - window size for median filter
  BacknumberWindow: Int,                  // points - how far back to look for true exit point
)

object DetectionConfig {

  val default: DetectionConfig =
    DetectionConfig(
      TakeoffSpeedThreshold = 25.0,
      TakeoffClimbRate = -1.0,
      TakeoffMaxAltitude = 600.0,
      FreefallWindow = 25,
      FreefallVerticalSpeedThreshold = 25.0,
      FreefallAccelThreshold = 3.0,
      FreefallAccelMinVelocity = 10.0,
      FreefallMinAltitudeAbove = 600.0,
      FreefallMinAltitudeAbsolute = 600.0,
      CanopyWindow = 25,
      CanopyVerticalSpeedMax = 12.0,
      LandingSpeedMax = 3.0,
      LandingStabilityThreshold = 0.5,
      LandingMeanVerticalSpeedMax = 1.0,
      LandingAltitudeTolerance = 500.0,
      MedianFilterWindow = 5,
      BacknumberWindow = 10,
    )

}
