package com.colofabrix.scala.beesight.pipeline

/**
 * Canonical stages of the processing pipeline, declared in execution order.
 */
enum Stage(val prerequisites: Set[Stage] = Set.empty) {

  case JumpEvents
  case Cut
  case Metrics extends Stage(Set(JumpEvents))
  case Swoop   extends Stage(Set(JumpEvents))
  case Charts  extends Stage(Set(JumpEvents))
  case Convert

}

object Stage {

  val all: List[Stage] =
    values.toList

  extension (self: Stage) {

    /** The stage together with all its transitive prerequisites */
    def withPrerequisites: Set[Stage] =
      self.prerequisites.flatMap(_.withPrerequisites) + self

    /** True when executing the stage produces the processed CSV file */
    def producesProcessedCsv: Boolean =
      self == JumpEvents || self == Cut

  }

}

/**
 * The stages to execute in a run, with prerequisites auto-satisfied and ordered for execution.
 */
final case class ResolvedStages(stages: List[Stage]) {

  /** True when the given stage is part of the plan */
  def wants(stage: Stage): Boolean =
    stages.contains(stage)

}

object ResolvedStages {

  /** Resolves the wanted stages into an execution plan, adding any missing prerequisite */
  def of(wanted: Set[Stage]): ResolvedStages =
    val closure = wanted.flatMap(_.withPrerequisites)
    ResolvedStages(Stage.all.filter(closure.contains))

}
