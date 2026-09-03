package com.colofabrix.scala.beesight.preprocess

import com.colofabrix.scala.beesight.model.{ DataPoint, PointReader }

trait Preprocessor {

  def preprocess[F[_], A: PointReader]: fs2.Pipe[F, A, DataPoint]

}

object Preprocessor {

  def id: Preprocessor =
    new Preprocessor {
      def preprocess[F[_], A: PointReader]: fs2.Pipe[F, A, DataPoint] =
        _.map(DataPoint.fromReader)
    }

}
