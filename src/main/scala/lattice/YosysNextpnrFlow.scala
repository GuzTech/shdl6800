package lattice

import java.io.{File, FileWriter}
import java.nio.file.Paths

import org.apache.commons.io.FileUtils
import spinal.lib.eda.bench.Report

import scala.sys.process.Process

object DeviceName extends Enumeration {
  type Main = Value

  val iCE40LP384 = Value("--lp384")
  val iCE40LP1K  = Value("--lp1k")
  val iCE40LP4K  = Value("--lp8k")
  val iCE40LP8K  = Value("--lp8k")
  val iCE40HX1K  = Value("--hx1k")
  val iCE40HX4K  = Value("--hx8k")
  val iCE40HX8K  = Value("--hx8k")
  val iCE40UP3K  = Value("--up5k")
  val iCE40UP5K  = Value("--up5k")
  val iCE5LP1K   = Value("--u4k")
  val iCE5LP2K   = Value("--u4k")
  val iCE5LP4K   = Value("--u4k")
}

object PackageType extends Enumeration {
  type Main = Value

  val SWG16TR = Value("sqg16tr")
  val UWG30   = Value("uwg30")
  val CM36    = Value("cm36")
  val CM49    = Value("cm49")
  val CM81    = Value("cm81")
  val CM121   = Value("cm121")
  val CM225   = Value("cm225")
  val QN32    = Value("qn32")
  val SG48    = Value("sg48")
  val QN48    = Value("qn48")
  val QN84    = Value("qn84")
  val CB81    = Value("cb81")
  val CB121   = Value("cb121")
  val CB132   = Value("cb132")
  val VQ100   = Value("vq100")
  val TQ144   = Value("tq144")
  val BG121   = Value("bg121")
  val CT256   = Value("ct256")
}

object YosysNextpnrFlow {
  object NextpnrOptions {
    val valid_packages = Map(
      DeviceName.iCE40LP384 -> Array(
        PackageType.CM36,
        PackageType.QN32
      ),
      DeviceName.iCE40LP1K -> Array(
        PackageType.SWG16TR,
        PackageType.CM36,
        PackageType.CM49,
        PackageType.CM81,
        PackageType.CM121,
        PackageType.QN84,
        PackageType.CB81,
        PackageType.CB121
      ),
      DeviceName.iCE40LP4K -> Array(
        PackageType.CM81,
        PackageType.CM121,
        PackageType.CM225
      ),
      DeviceName.iCE40LP8K -> Array(
        PackageType.CM81,
        PackageType.CM121,
        PackageType.CM225
      ),
      DeviceName.iCE40HX1K -> Array(
        PackageType.CB132,
        PackageType.VQ100,
        PackageType.TQ144
      ),
      DeviceName.iCE40HX4K -> Array(
        PackageType.CB132,
        PackageType.TQ144,
        PackageType.BG121
      ),
      DeviceName.iCE40HX8K -> Array(
        PackageType.CM225,
        PackageType.CB132,
        PackageType.TQ144,
        PackageType.BG121,
        PackageType.CT256
      ),
      DeviceName.iCE40UP3K -> Array(
        PackageType.UWG30
      ),
      DeviceName.iCE40UP5K -> Array(
        PackageType.UWG30,
        PackageType.SG48
      ),
      DeviceName.iCE5LP1K -> Array(

      ),
      DeviceName.iCE5LP2K -> Array(

      ),
      DeviceName.iCE5LP4K -> Array(

      )
    )
  }

  val isWindows = System.getProperty("os.name").toLowerCase().contains("win")

  def doCmd(cmd: String): Unit = {
    if(isWindows)
      Process("cmd /C " + cmd) !!
    else
      Process(cmd) !!
  }

  def doCmd(cmd: String, path: String): Unit = {
    if(isWindows)
      Process("cmd /C " + cmd, new java.io.File(path)) !!
    else
      Process(cmd, new java.io.File((path))) !!
  }

  def doCmd(cmd: Seq[String]): Unit = {
    if(isWindows)
      Process(Seq("cmd /C ") ++ cmd) !!
    else
      Process(cmd) !!
  }

  def doCmd(cmd: Seq[String], path: String): Unit = {
    if(isWindows)
      Process(Seq("cmd /C ") ++ cmd, new java.io.File(path)) !!
    else
      Process(cmd, new java.io.File((path))) !!
  }

  def checkPackage(deviceName: DeviceName.Value, packageType: PackageType.Value): Boolean = {
    NextpnrOptions.valid_packages(deviceName).contains(packageType)
  }

  def apply(toplevelName: String, deviceName: DeviceName.Value, packageType: PackageType.Value, buildDir: String): Report = {
    if(checkPackage(deviceName, packageType)) {
      // Create a fresh new build directory
      val buildDirFile = new File(buildDir)
      FileUtils.deleteDirectory(buildDirFile)
      buildDirFile.mkdir()
      FileUtils.copyFileToDirectory(new File(s"${toplevelName}.v"), buildDirFile)

      // Create a constraints (.pcf) file
      val pcf = new FileWriter(Paths.get(buildDir, s"${toplevelName}.pcf").toFile)
//      for(constraint <- constraints) {
//        pcf.write(s"")
//      }

      // Call Yosys, Nextpnr, and icepack
      val yosys = Seq(
        "yosys",
        "-l", s"${toplevelName}.rpt",
        "-p", s"synth_ice40 -top ${toplevelName} -json ${toplevelName}.json",
        s"${toplevelName}.v")
      doCmd(yosys, buildDir)
      val nextpnr = Seq(
        "nextpnr-ice40",
        "-l", s"${toplevelName}.tim",
        deviceName.toString + (deviceName.toString.contains("4k") match {
          case true => ":4k"
          case _ => ""
        }),
        "--package", packageType.toString,
        "--pcf", s"${toplevelName}.pcf",
        "--json", s"${toplevelName}.json",
        "--asc", s"${toplevelName}.asc")
      doCmd(nextpnr, buildDir)
      doCmd(s"icepack ${toplevelName}.asc ${toplevelName}.bin", buildDir)

      new Report {
        override def getFMax(): Double = {
          import scala.io.Source
          val report = Source.fromFile(s"${buildDir + "/" + toplevelName}.tim").getLines().mkString
          val max_freq = try {
            val all_freqs = "[0-9]+.[0-9]+ MHz ".r.findAllIn(report)
            if (all_freqs.nonEmpty) {
              val list = all_freqs.toList

              // The last one is the actual maximum clock frequency
              list.tail.mkString.split(' ').head.toDouble
            } else {
              // If nothing matches the regex, then something went wrong.
              -1.0
            }
          } catch {
            case e: Exception => -1.0
          }
          return max_freq
        }

        override def getArea(): String = {
          import scala.io.Source
          val report = Source.fromFile(s"${buildDir + "/" + toplevelName}.rpt").getLines().mkString("\n")

          val num_cells = {
            val cells = "cells: +([0-9]+)\\n".r.findAllIn(report)
            if (cells.nonEmpty) {
              cells.toList.last.mkString.split(' ').last
            } else {
              ""
            }
          }
          val num_lut4 = {
            val lut4 = "SB_LUT4 +([0-9]+)\\n".r.findAllIn(report)
            if (lut4.nonEmpty) {
              lut4.toList.last.mkString.split(' ').last
            } else {
              ""
            }
          }
          val num_dff = {
            val dff = "SB_DFF +([0-9]+)\\n".r.findAllIn(report)
            if (dff.nonEmpty) {
              dff.toList.last.mkString.split(' ').last
            } else {
              ""
            }
          }
          val num_dffe = {
            val dffe = "SB_DFFE +([0-9]+)\\n".r.findAllIn(report)
            if (dffe.nonEmpty) {
              dffe.toList.last.mkString.split(' ').last
            } else {
              ""
            }
          }
          val num_dffer = {
            val dffer = "SB_DFFER +([0-9]+)\\n".r.findAllIn(report)
            if (dffer.nonEmpty) {
              dffer.toList.last.mkString.split(' ').last
            } else {
              ""
            }
          }
          val num_dffr = {
            val dffr = "SB_DFFR +([0-9]+)\\n".r.findAllIn(report)
            if (dffr.nonEmpty) {
              dffr.toList.last.mkString.split(' ').last
            } else {
              ""
            }
          }
          val num_dffs = {
            val dffs = "SB_DFFS +([0-9]+)\\n".r.findAllIn(report)
            if (dffs.nonEmpty) {
              dffs.toList.last.mkString.split(' ').last
            } else {
              ""
            }
          }
          val num_carry = {
            val carry = "SB_CARRY +[0-9]+\\s".r.findAllIn(report)
            if (carry.nonEmpty) {
              carry.toList.last.mkString.split(' ').last
            } else {
              ""
            }
          }
          s"""Number of cells: ${num_cells}
             |Number of LUT4:  ${num_lut4}
             |Number of DFF:   ${num_dff}
             |Number of DFFE:  ${num_dffe}
             |Number of DFFER: ${num_dffer}
             |Number of DFFR:  ${num_dffr}
             |Number of DFFS:  ${num_dffs}
             |Number of CARRY: ${num_carry}
             |""".stripMargin
        }
      }
    } else {
      var errorMessage =
        s"""Package type ${packageType.toString} for device ${deviceName.toString} is invalid.
           |
           |Valid package types are:
           |""".stripMargin
      for(name <- NextpnrOptions.valid_packages(deviceName)) {
        errorMessage += name.toString.toUpperCase() + "\n"
      }
      error(errorMessage)
    }
  }
}
