package com.colofabrix.scala.beesight.utils

import cats.effect.IO
import com.colofabrix.scala.beesight.IOValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class StreamBypassSpec extends AnyFreeSpec with Matchers with IOValues {

  import com.colofabrix.scala.beesight.utils.*

  "processBypass" - {

    "split and recombine" - {

      "should split input, process one part, and recombine" in {
        // Split (Int, String) into Int for processing and String as bypass
        given Splitter[(Int, String), Int, String] with
          def split(a: (Int, String)): (Int, String) = a

        given Combiner[Int, String, (Int, String)] with
          def combine(processed: Int, bypass: String): (Int, String) = (processed, bypass)

        val input = List((1, "a"), (2, "b"), (3, "c"))

        val result =
          fs2.Stream
            .emits[IO, (Int, String)](input)
            .processBypass[Int, Int, String, (Int, String)](_.map(_ * 10))
            .compile
            .toList
            .result()

        result shouldBe List((10, "a"), (20, "b"), (30, "c"))
      }

      "should preserve bypass data unchanged" in {
        given Splitter[(Int, String), Int, String] with
          def split(a: (Int, String)): (Int, String) = a

        given Combiner[String, String, (String, String)] with
          def combine(processed: String, bypass: String): (String, String) = (processed, bypass)

        val input = List((1, "hello"), (2, "world"))

        val result =
          fs2.Stream
            .emits[IO, (Int, String)](input)
            .processBypass[Int, String, String, (String, String)](_.map(_.toString))
            .compile
            .toList
            .result()

        result.map(_._2) shouldBe List("hello", "world")
      }

    }

    "empty stream" - {

      "should produce empty output" in {
        given Splitter[Int, Int, Int] with
          def split(a: Int): (Int, Int) = (a, a)

        given Combiner[Int, Int, Int] with
          def combine(processed: Int, bypass: Int): Int = processed + bypass

        val result =
          fs2.Stream
            .empty
            .covary[IO]
            .processBypass[Int, Int, Int, Int](_.map(_ * 2))
            .compile
            .toList
            .result()

        result shouldBe empty
      }

    }

    "processing pipe transformation" - {

      "should apply the pipe transformation to the processable part only" in {
        given Splitter[Int, Int, Int] with
          def split(a: Int): (Int, Int) = (a, a)

        given Combiner[Int, Int, String] with
          def combine(processed: Int, bypass: Int): String =
            s"processed=$processed,original=$bypass"

        val result =
          fs2.Stream
            .emits[IO, Int](List(5, 10, 15))
            .processBypass[Int, Int, Int, String](_.map(_ + 100))
            .compile
            .toList
            .result()

        result shouldBe List(
          "processed=105,original=5",
          "processed=110,original=10",
          "processed=115,original=15",
        )
      }

    }

  }

}
