package com.colofabrix.scala.beesight.config

final case class DetectionConfig(
  //  Takeoff parameters  //
  TakeoffSpeedThreshold: Double, // m/s - Horizontal speed above this indicates takeoff
  TakeoffClimbRate: Double,      // m/s - VerticalSpeed below this indicates climbing (negative means climbing)
  TakeoffMaxAltitude: Double,    // m - Takeoff cannot happen above this altitude

  //  Freefall parameters  //
  FreefallWindow: Int,                    // Data Points - Size of the CUSUM Freefall detection window
  FreefallVerticalSpeedThreshold: Double, // m/s - VerticalSpeed above this indicates freefall
  FreefallAccelThreshold: Double,         // m/s per sample - Rapid verticalSpeed increase indicates exit
  FreefallAccelMinVelocity: Double,       // m/s - Minimum verticalSpeed for accel-based detection
  FreefallMinAltitudeAbove: Double,       // m - Freefall must be at least this high above takeoff
  FreefallMinAltitudeAbsolute: Double,    // m - Freefall min altitude when takeoff missed

  //  Canopy parameters  //
  CanopyWindow: Int,              // Data Points - Size of the CUSUM Canopy detection window
  CanopyVerticalSpeedMax: Double, // m/s - VerticalSpeed below this after freefall indicates canopy

  //  Landing parameters  //
  LandingSpeedMax: Double,             // m/s - Total speed below this indicates landing
  LandingStabilityThreshold: Double,   // m/s - Stddev of vertical speed must be below this
  LandingMeanVerticalSpeedMax: Double, // m/s - Mean vertical speed must be below this
  LandingAltitudeTolerance: Double,    // m - Landing must be within ±this of takeoff altitude

  //  Global parameters  //
  SmoothingVertSpeedWindowSize: Int,      // points - size of smoothing vertical speed window
  PhaseDetectionVertSpeedWindowSize: Int, // points - size of phase detection vertical speed window
  BacktrackVertSpeedWindowSize: Int,      // points - size of backtracking vertical speed window
  MedianFilterWindow: Int,                // points - Window size for median filter
  BacknumberWindow: Int,                  // points - How far back to look for true exit point
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
      SmoothingVertSpeedWindowSize = 5,
      PhaseDetectionVertSpeedWindowSize = 10,
      BacktrackVertSpeedWindowSize = 10,
      MedianFilterWindow = 5,
      BacknumberWindow = 10,
    )
}
