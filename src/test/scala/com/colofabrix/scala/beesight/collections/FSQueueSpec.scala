package com.colofabrix.scala.beesight.collections

import cats.*
import cats.syntax.all.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.freespec.AnyFreeSpec

class FSQueueSpec extends AnyFreeSpec with Matchers {

  "FSQueue" - {

    "construction" - {

      "should create an empty queue with the specified maximum size" in {
        val queue = FSQueue[Int](5)

        queue.toVector shouldBe Vector.empty
        queue.size shouldBe 5
      }

      "should create a size-1 queue via FSQueue.empty" in {
        val queue = FSQueue.empty[String]

        queue.toVector shouldBe Vector.empty
        queue.size shouldBe 1
      }

    }

    "enqueue" - {

      "should add elements when below capacity" in {
        val queue =
          FSQueue[Int](3)
            .enqueue(1)
            .enqueue(2)
            .enqueue(3)

        queue.toVector shouldBe Vector(1, 2, 3)
      }

    }

    "push" - {

      "should return None when the queue is not full" in {
        val queue         = FSQueue[Int](3)
        val (out, queue2) = queue.push(1)

        out shouldBe None
        queue2.toVector shouldBe Vector(1)
      }

      "should return the outgoing element when the queue is full" in {
        val queue =
          FSQueue[Int](2)
            .enqueue(1)
            .enqueue(2)

        val (out, queue2) = queue.push(3)

        out shouldBe Some(1)
        queue2.toVector shouldBe Vector(2, 3)
      }

      "should handle a single-element queue correctly" in {
        val queue = FSQueue[Int](1).enqueue(1)

        queue.toVector shouldBe Vector(1)

        val (out, queue2) = queue.push(2)
        out shouldBe Some(1)
        queue2.toVector shouldBe Vector(2)
      }

      "should handle continuous overflow correctly" in {
        var queue   = FSQueue[Int](3)
        var outputs = List.empty[Int]

        for (i <- 1 to 10) {
          val (out, newQueue) = queue.push(i)
          out.foreach(o => outputs = outputs :+ o)
          queue = newQueue
        }

        outputs shouldBe List(1, 2, 3, 4, 5, 6, 7)
        queue.toVector shouldBe Vector(8, 9, 10)
      }

    }

    "pushMap" - {

      "should transform the outgoing element before returning it" in {
        val queue =
          FSQueue[Int](2)
            .enqueue(10)
            .enqueue(20)

        val (out, queue2) = queue.pushMap(30)(_ * 2)

        out shouldBe Some(20) // 10 * 2
        queue2.toVector shouldBe Vector(20, 30)
      }

    }

    "oldest" - {

      "should return the first element" in {
        val queue =
          FSQueue[Int](3)
            .enqueue(1)
            .enqueue(2)
            .enqueue(3)

        queue.oldest shouldBe Some(1)
      }

      "should return None for an empty queue" in {
        FSQueue[Int](3).oldest shouldBe None
      }

      "should return the first n elements when given a count" in {
        val queue =
          FSQueue[Int](5)
            .enqueue(1)
            .enqueue(2)
            .enqueue(3)
            .enqueue(4)
            .enqueue(5)

        queue.oldest(2) shouldBe Vector(1, 2)
        queue.oldest(3) shouldBe Vector(1, 2, 3)
      }

    }

    "newest" - {

      "should return the last element" in {
        val queue =
          FSQueue[Int](3)
            .enqueue(1)
            .enqueue(2)
            .enqueue(3)

        queue.newest shouldBe Some(3)
      }

      "should return None for an empty queue" in {
        FSQueue[Int](3).newest shouldBe None
      }

      "should return the last n elements when given a count" in {
        val queue =
          FSQueue[Int](5)
            .enqueue(1)
            .enqueue(2)
            .enqueue(3)
            .enqueue(4)
            .enqueue(5)

        queue.newest(2) shouldBe Vector(4, 5)
        queue.newest(3) shouldBe Vector(3, 4, 5)
      }

    }

    "setSize" - {

      "should increase capacity while preserving existing elements" in {
        val queue =
          FSQueue[Int](2)
            .enqueue(1)
            .enqueue(2)
            .setSize(5)
            .enqueue(3)

        queue.toVector shouldBe Vector(1, 2, 3)
        queue.size shouldBe 5
      }

      "should decrease capacity and truncate elements to fit keeping only the newest" in {
        val queue =
          FSQueue[Int](5)
            .enqueue(1)
            .enqueue(2)
            .enqueue(3)
            .enqueue(4)
            .enqueue(5)
            .setSize(2)

        queue.toVector shouldBe Vector(4, 5)
        queue.size shouldBe 2
      }

    }

    "Cats instances" - {

      "Functor" - {

        "should transform all elements via map" in {
          val queue =
            FSQueue[Int](3)
              .enqueue(1)
              .enqueue(2)
              .enqueue(3)

          val mapped = Functor[FSQueue].map(queue)(_ * 10)

          mapped.toVector shouldBe Vector(10, 20, 30)
        }

      }

      "Monad" - {

        "should create a single-element queue via pure" in {
          val queue = Monad[FSQueue].pure(42)

          queue.toVector shouldBe Vector(42)
        }

        "should chain queue operations via flatMap" in {
          val queue =
            FSQueue[Int](2)
              .enqueue(1)
              .enqueue(2)

          val result = Monad[FSQueue].flatMap(queue)(x => Monad[FSQueue].pure(x * 10))

          result.toVector shouldBe Vector(10, 20)
        }

      }

    }

  }

}
