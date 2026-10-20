package com.colofabrix.scala.beesight.pipeline

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class ResolvedStagesSpec extends AnyWordSpec, Matchers {

  "ResolvedStages.of" should {

    "add the transitive prerequisites of the requested stage" in {
      val expected = List(Stage.JumpEvents, Stage.Swoop)

      val actual = ResolvedStages.of(Set(Stage.Swoop)).stages

      actual shouldBe expected
    }

    "deduplicate stages that already include their prerequisites" in {
      val expected = List(Stage.JumpEvents, Stage.Charts)

      val actual = ResolvedStages.of(Set(Stage.JumpEvents, Stage.Charts)).stages

      actual shouldBe expected
    }

    "keep the canonical execution order regardless of the requested order" in {
      val expected = List(Stage.JumpEvents, Stage.Cut, Stage.Metrics, Stage.Swoop, Stage.Charts, Stage.Convert)

      val actual = ResolvedStages.of(
        Set(Stage.Convert, Stage.Charts, Stage.Swoop, Stage.Metrics, Stage.Cut, Stage.JumpEvents),
      ).stages

      actual shouldBe expected
    }

    "resolve to an empty plan when nothing is requested" in {
      val expected = List.empty[Stage]

      val actual = ResolvedStages.of(Set.empty).stages

      actual shouldBe expected
    }

    "resolve an independent stage without adding prerequisites" in {
      val expected = List(Stage.Convert)

      val actual = ResolvedStages.of(Set(Stage.Convert)).stages

      actual shouldBe expected
    }

  }

  "Stage.withPrerequisites" should {

    "include the stage itself and its transitive prerequisites" in {
      val expected = Set(Stage.Swoop, Stage.JumpEvents)

      val actual = Stage.Swoop.withPrerequisites

      actual shouldBe expected
    }

    "be a singleton for a stage without prerequisites" in {
      val expected = Set(Stage.Cut)

      val actual = Stage.Cut.withPrerequisites

      actual shouldBe expected
    }

  }

}
