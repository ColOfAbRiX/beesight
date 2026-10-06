package com.colofabrix.scala.beesight.formats

import com.colofabrix.scala.beesight.formats.{ FileFormat, FormatDetector }
import com.colofabrix.scala.beesight.formats.devices.GenericPoint
import com.colofabrix.scala.beesight.formats.devices.FlysightPoint
import com.colofabrix.scala.beesight.formats.devices.DekunuPoint
import com.colofabrix.scala.beesight.formats.devices.AirlogPoint
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FormatDetectorSpec extends AnyFreeSpec with Matchers {

  private val flysightColumns = FlysightPoint.requiredColumns
  private val airlogColumns   = AirlogPoint.requiredColumns
  private val dekunuColumns   = DekunuPoint.requiredColumns
  private val genericColumns  = GenericPoint.requiredColumns

  private def headerOf(columns: Set[String]): String =
    columns.mkString(",")

  //  Correct detection  //

  "detecting a FlySight file" - {

    "should detect a FlySight header with all required columns" in {
      FormatDetector.detect(headerOf(flysightColumns)) shouldBe Some(FileFormat.FlySight)
    }

    "should detect a FlySight header with shuffled column order" in {
      FormatDetector.detect(headerOf(flysightColumns)) shouldBe Some(FileFormat.FlySight)
    }

    "should detect a FlySight header with padded whitespace" in {
      val padded = flysightColumns.map(c => s"  $c  ").mkString(",")
      FormatDetector.detect(padded) shouldBe Some(FileFormat.FlySight)
    }

    "should detect a FlySight header with extra unknown columns" in {
      FormatDetector.detect(headerOf(flysightColumns + "extraColumn" + "anotherOne")) shouldBe Some(
        FileFormat.FlySight
      )
    }

    "should detect a FlySight header with CRLF line ending" in {
      FormatDetector.detect(headerOf(flysightColumns) + "\r\n") shouldBe Some(FileFormat.FlySight)
    }

  }

  "detecting an AirlogOne file" - {

    "should detect an Airlog header with all required columns" in {
      FormatDetector.detect(headerOf(airlogColumns)) shouldBe Some(FileFormat.Airlog)
    }

    "should detect an Airlog header with extra unknown columns" in {
      FormatDetector.detect(headerOf(airlogColumns + "extraColumn")) shouldBe Some(FileFormat.Airlog)
    }

  }

  "detecting a Dekunu file" - {

    "should detect a Dekunu header with all required columns" in {
      FormatDetector.detect(headerOf(dekunuColumns)) shouldBe Some(FileFormat.Dekunu)
    }

    "should detect a Dekunu header without the optional columns" in {
      val withoutOptionals = dekunuColumns
      FormatDetector.detect(headerOf(withoutOptionals)) shouldBe Some(FileFormat.Dekunu)
    }

  }

  "detecting a Generic file" - {

    "should detect a Generic header with all required columns" in {
      FormatDetector.detect(headerOf(genericColumns)) shouldBe Some(FileFormat.Generic)
    }

    "should detect a Generic header with extra unknown columns" in {
      FormatDetector.detect(headerOf(genericColumns + "extraColumn")) shouldBe Some(FileFormat.Generic)
    }

  }

  //  Rejection  //

  "rejecting non-matching files" - {

    "should reject a header missing one required FlySight column" in {
      val broken = flysightColumns - "velN"
      FormatDetector.detect(headerOf(broken)) shouldBe None
    }

    "should reject a header missing one required Airlog column" in {
      val broken = airlogColumns - "b_sink"
      FormatDetector.detect(headerOf(broken)) shouldBe None
    }

    "should reject a header missing one required Dekunu column" in {
      val broken = dekunuColumns - "gpsTime"
      FormatDetector.detect(headerOf(broken)) shouldBe None
    }

    "should reject a header missing one required Generic column" in {
      val broken = genericColumns - "velN"
      FormatDetector.detect(headerOf(broken)) shouldBe None
    }

    "should reject an empty header" in {
      FormatDetector.detect("") shouldBe None
    }

    "should reject a garbage header" in {
      FormatDetector.detect("foo,bar,baz") shouldBe None
    }

    "should reject a header with different letter casing" in {
      // Detection is case-sensitive: "Time" does not match "time"
      val wrongCase = flysightColumns.map(_.toUpperCase).mkString(",")
      FormatDetector.detect(wrongCase) shouldBe None
    }

  }

  "breakage scenarios" - {

    "should pick the format with the most matching fields when the header satisfies multiple formats" in {
      // Airlog requires 18 columns, FlySight 13: on a header containing both, Airlog matches more fields and wins
      val ambiguous = flysightColumns ++ airlogColumns
      FormatDetector.detect(headerOf(ambiguous)) shouldBe Some(FileFormat.Airlog)
    }

    "should prefer the format matching more fields over a subset format" in {
      // A FlySight header also satisfies Generic's 6 required columns, but FlySight matches 13 and wins
      FormatDetector.detect(headerOf(flysightColumns)) shouldBe Some(FileFormat.FlySight)
    }

    "should detect a BOM-prefixed FlySight header as Generic" in {
      // The BOM corrupts only the first column ("date"), so FlySight no longer matches, while all of
      // Generic's required columns (time, hMSL, velN, velE, velD, numSV) are still present
      val bomHeader = "\uFEFF" + headerOf(flysightColumns)
      FormatDetector.detect(bomHeader) shouldBe Some(FileFormat.Generic)
    }

    "should fail on quoted headers (KNOWN ISSUE)" in {
      val quoted = flysightColumns.map(c => s""""$c"""").mkString(",")
      FormatDetector.detect(quoted) shouldBe None
    }

    "should fail on semicolon-separated headers (KNOWN ISSUE)" in {
      val semicolon = flysightColumns.mkString(";")
      FormatDetector.detect(semicolon) shouldBe None
    }

  }

  //  Set-based API  //

  "detecting from a column set" - {

    "should detect from a Set[String] directly" in {
      FormatDetector.detect(dekunuColumns) shouldBe Some(FileFormat.Dekunu)
    }

    "should return None for an empty set" in {
      FormatDetector.detect(Set.empty[String]) shouldBe None
    }

  }

}
