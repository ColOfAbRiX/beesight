package com.colofabrix.scala.beesight.config

final case class DetectionConfig(
  //  Takeoff parameters  //
  TakeoffSpeedThreshold: Double, // m/s - Horizontal speed above this indicates takeoff
  TakeoffClimbRate: Double,      // m/s - VerticalSpeed below this indicates climbing (negative means climbing)
  TakeoffMaxAltitude: Double,    // m - Takeoff cannot happen above this altitude

  //  Freefall parameters  //
  FreefallVerticalSpeedThreshold: Double, // m/s - VerticalSpeed above this indicates freefall
  FreefallAccelThreshold: Double,         // m/s per sample - Rapid verticalSpeed increase indicates exit
  FreefallAccelMinVelocity: Double,       // m/s - Minimum verticalSpeed for accel-based detection
  FreefallMinAltitudeAbove: Double,       // m - Freefall must be at least this high above takeoff
  FreefallMinAltitudeAbsolute: Double,    // m - Freefall min altitude when takeoff missed

  //  Canopy parameters  //
  CanopyVerticalSpeedMax: Double, // m/s - VerticalSpeed below this after freefall indicates canopy

  //  Landing parameters  //
  LandingSpeedMax: Double,             // m/s - Total speed below this indicates landing
  LandingStabilityThreshold: Double,   // m/s - Stddev of vertical speed must be below this
  LandingMeanVerticalSpeedMax: Double, // m/s - Mean vertical speed must be below this
  LandingAltitudeTolerance: Double,    // m - Landing must be within ±this of takeoff altitude
  LandingStabilityWindowSize: Int,     // points - Size of landing stability detection window

  //  Global parameters  //
  ClipAcceleration: Double,               // m/s² - Maximum allowed acceleration for clipping implausible values
  SmoothingVerticalSpeedWindowSize: Int, // points - Size of smoothing vertical speed window
  BacktrackVerticalSpeedWindowSize: Int, // points - Size of backtracking window for finding true transition points
)

object DetectionConfig {

  val default: DetectionConfig =
    DetectionConfig(
      TakeoffSpeedThreshold = 25.0,
      TakeoffClimbRate = -1.0,
      TakeoffMaxAltitude = 600.0,
      FreefallVerticalSpeedThreshold = 25.0,
      FreefallAccelThreshold = 3.0,
      FreefallAccelMinVelocity = 10.0,
      FreefallMinAltitudeAbove = 600.0,
      FreefallMinAltitudeAbsolute = 600.0,
      CanopyVerticalSpeedMax = 12.0,
      LandingSpeedMax = 3.0,
      LandingStabilityThreshold = 0.5,
      LandingMeanVerticalSpeedMax = 1.0,
      LandingAltitudeTolerance = 500.0,
      ClipAcceleration = 20.0,
      SmoothingVerticalSpeedWindowSize = 5,
      LandingStabilityWindowSize = 10,
      BacktrackVerticalSpeedWindowSize = 10,
    )
}
