package com.colofabrix.scala.beesight.input

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.config.Config
import fs2.data.csv.*
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files as JFiles, Path }
import java.util.zip.{ ZipEntry, ZipOutputStream }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class CsvFileReaderSpec extends AnyFreeSpec with Matchers with IOConfigValues with BeforeAndAfterAll {

  "CsvFileReader" - {

    "reading from files" - {

      "should decode all data rows from a plain CSV file" in {
        val path   = writeCsv("header_and_data.csv", headerAndData)
        val result = CsvFileReader.readCsv[TestRow](path).compile.toList.result(Config.default)

        result should have size 3
        result(0) shouldBe TestRow(1, 2.5, "hello")
        result(1) shouldBe TestRow(2, 3.5, "world")
        result(2) shouldBe TestRow(3, 4.5, "test")
      }

      "should decode CSV from a ZIP file" in {
        val path   = writeZip("data.zip", headerAndData)
        val result = CsvFileReader.readCsv[TestRow](path).compile.toList.result(Config.default)

        result should have size 3
        result(0) shouldBe TestRow(1, 2.5, "hello")
        result(1) shouldBe TestRow(2, 3.5, "world")
        result(2) shouldBe TestRow(3, 4.5, "test")
      }

    }

    "header and units row handling" - {

      "should skip a text units row after the header" in {
        val path   = writeCsv("header_units.csv", headerUnitsData)
        val result = CsvFileReader.readCsv[TestRow](path).compile.toList.result(Config.default)

        result should have size 3
        result(0) shouldBe TestRow(1, 2.5, "hello")
        result(1) shouldBe TestRow(2, 3.5, "world")
        result(2) shouldBe TestRow(3, 4.5, "test")
      }

      "should raise error when a second non-decodable row appears after header" in {
        val path = writeCsv("two_junk.csv", twoJunkRowsAfterHeader)
        val io   = CsvFileReader.readCsv[TestRow](path).compile.toList

        io.isException(Config.default) shouldBe true
        io.exception(Config.default) shouldBe a[DecoderError]
      }

      "should keep data when first row after header is valid" in {
        val path   = writeCsv("no_units.csv", headerAndData)
        val result = CsvFileReader.readCsv[TestRow](path).compile.toList.result(Config.default)

        result should have size 3
        result.head shouldBe TestRow(1, 2.5, "hello")
      }

      "should return empty stream for header-only CSV" in {
        val path   = writeCsv("header_only.csv", headerOnly)
        val result = CsvFileReader.readCsv[TestRow](path).compile.toList.result(Config.default)

        result shouldBe empty
      }

    }

    "line filtering" - {

      "should filter out comment lines starting with #" in {
        val path   = writeCsv("comments.csv", withComments)
        val result = CsvFileReader.readCsv[TestRow](path).compile.toList.result(Config.default)

        result should have size 2
        result(0) shouldBe TestRow(1, 2.5, "hello")
        result(1) shouldBe TestRow(2, 3.5, "world")
      }

      "should filter out empty lines" in {
        val path   = writeCsv("empty_lines.csv", withEmptyLines)
        val result = CsvFileReader.readCsv[TestRow](path).compile.toList.result(Config.default)

        result should have size 2
        result(0) shouldBe TestRow(1, 2.5, "hello")
        result(1) shouldBe TestRow(2, 3.5, "world")
      }

    }

    "line ending normalization" - {

      "should handle Windows CRLF line endings" in {
        val content = "a,b,c\r\n1,2.5,hello\r\n2,3.5,world\r\n"
        val path    = writeCsvBytes("crlf.csv", content.getBytes(StandardCharsets.UTF_8))
        val result  = CsvFileReader.readCsv[TestRow](path).compile.toList.result(Config.default)

        result should have size 2
        result(0) shouldBe TestRow(1, 2.5, "hello")
        result(1) shouldBe TestRow(2, 3.5, "world")
      }

    }

    "error handling" - {

      "should raise error when any data row after the header and optional units row fails to decode" in {
        val path = writeCsv("bad_data.csv", badDataAfterRow2)
        val io   = CsvFileReader.readCsv[TestRow](path).compile.toList

        io.isException(Config.default) shouldBe true
        io.exception(Config.default) shouldBe a[DecoderError]
      }

    }

  }

  // Simple test data type
  private case class TestRow(a: Int, b: Double, c: String)

  private given CsvRowDecoder[TestRow, String] with {

    def apply(row: CsvRow[String]): DecoderResult[TestRow] =
      for
        a <- row.as[Int]("a")
        b <- row.as[Double]("b")
        c <- row.as[String]("c")
      yield TestRow(a, b, c)

  }

  private val tempDir =
    JFiles.createTempDirectory("csv_file_reader_spec")

  override def afterAll(): Unit =
    JFiles
      .walk(tempDir)
      .sorted(java.util.Comparator.reverseOrder())
      .forEach(JFiles.deleteIfExists)
    super.afterAll()

  private def writeCsv(name: String, content: String): Path =
    val path = tempDir.resolve(name)
    JFiles.writeString(path, content, StandardCharsets.UTF_8)
    path

  private def writeCsvBytes(name: String, bytes: Array[Byte]): Path =
    val path = tempDir.resolve(name)
    JFiles.write(path, bytes)
    path

  private def writeZip(name: String, csvContent: String): Path =
    val path = tempDir.resolve(name)
    val zos  = new ZipOutputStream(new FileOutputStream(path.toFile))
    zos.putNextEntry(new ZipEntry("data.csv"))
    zos.write(csvContent.getBytes(StandardCharsets.UTF_8))
    zos.closeEntry()
    zos.close()
    path

  //  Test data  //

  private val headerAndData =
    """a,b,c
      |1,2.5,hello
      |2,3.5,world
      |3,4.5,test""".stripMargin

  private val headerUnitsData =
    """a,b,c
      |(m),(s),(kg)
      |1,2.5,hello
      |2,3.5,world
      |3,4.5,test""".stripMargin

  private val twoJunkRowsAfterHeader =
    """a,b,c
      |units,(m),(s)
      |more_junk,x,y
      |1,2.5,hello
      |2,3.5,world""".stripMargin

  private val withComments =
    """# This is a comment
      |a,b,c
      |# Another comment
      |1,2.5,hello
      |2,3.5,world""".stripMargin

  private val withEmptyLines =
    """a,b,c
      |
      |1,2.5,hello
      |
      |2,3.5,world
      |""".stripMargin

  private val headerOnly =
    "a,b,c\n"

  private val badDataAfterRow2 =
    """a,b,c
      |1,2.5,hello
      |2,3.5,world
      |bad,not_a_number,corrupt""".stripMargin

}
