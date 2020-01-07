/* shdl6800: A 6800 processor written in SpinalHDL
 *
 * Copyright (C) 2020 Oguz Meteer <info@guztech.nl>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package lattice

import java.io.{File, FileWriter}
import java.nio.file.Paths

import org.apache.commons.io.FileUtils
import spinal.core.{Component, HertzNumber, SpinalReport}
import spinal.lib.eda.bench.Report

import scala.collection.mutable.ListBuffer
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
      Process("cmd /C " + cmd) !
    else
      Process(cmd) !
  }

  def doCmd(cmd: String, path: String): Unit = {
    if(isWindows)
      Process("cmd /C " + cmd, new java.io.File(path)) !
    else
      Process(cmd, new java.io.File((path))) !
  }

  def doCmd(cmd: Seq[String]): Unit = {
    if(isWindows)
      Process(Seq("cmd /C ") ++ cmd) !
    else
      Process(cmd) !
  }

  def doCmd(cmd: Seq[String], path: String): Unit = {
    if(isWindows)
      Process(Seq("cmd /C ") ++ cmd, new java.io.File(path)) !
    else
      Process(cmd, new java.io.File((path))) !
  }

  def checkPackage(deviceName: DeviceName.Value, packageType: PackageType.Value): Boolean = {
    NextpnrOptions.valid_packages(deviceName).contains(packageType)
  }

  def apply[T <: Component](
    toplevel: SpinalReport[T],
    deviceName: DeviceName.Value,
    packageType: PackageType.Value,
    buildDir: String,
    pinMappings: Map[String, String],
    frequencyTarget : HertzNumber = null): Report =
  {
    val toplevelName = toplevel.toplevelName

    if(checkPackage(deviceName, packageType)) {
      // Create a fresh new build directory
      val buildDirFile = new File(buildDir)
      FileUtils.deleteDirectory(buildDirFile)
      buildDirFile.mkdir()
      FileUtils.copyFileToDirectory(new File(s"${toplevelName}.v"), buildDirFile)

      // Create a constraints (.pcf) file
      val pcf = new FileWriter(Paths.get(buildDir, s"${toplevelName}.pcf").toFile)
      for((name, pin) <- pinMappings) {
        val pins = pin.split(' ')

        if(pins.length == 1) {
          pcf.write(s"set_io ${name} ${pin}\n")
        } else {
          for(i <- pins.indices) {
            pcf.write(s"set_io ${name}[${i}] ${pins(i)}\n")
          }
        }
      }
      pcf.flush()
      pcf.close()

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
        "--freq", (if (frequencyTarget != null) {(frequencyTarget / 1000000.0).toString} else {"12"}),
        "--json", s"${toplevelName}.json",
        "--asc", s"${toplevelName}.asc")
      doCmd(nextpnr, buildDir)
      doCmd(s"icepack ${toplevelName}.asc ${toplevelName}.bin", buildDir)

      new Report {
        override def getFMax(): Double = {
          import scala.io.Source
          val report = Source.fromFile(s"${buildDir + "/" + toplevelName}.tim").getLines().mkString("\n")
          val max_freq = try {
            //val all_freqs = "[0-9]+.[0-9]+ MHz ".r.findAllIn(report)
            val all_freqs = "Max frequency for clock\\s*'.+': ([0-9]+)\\.([0-9]+)".r.findAllIn(report)
            if (all_freqs.nonEmpty) {
              val list = all_freqs.toList
              /* We divide by two, because the first half of the lines contain
               * frequencies after simulated placement.
               */
              val num_lines = list.length / 2

              if(num_lines > 1) {
                val freqs = new ListBuffer[Double]

                // Go through all reported frequencies
                for (i <- num_lines until list.length) {
                  freqs += list(i).split(' ').last.toDouble
                }

                // And take the lowest number
                freqs.min
              } else {
                // Take the last one
                list.last.split(' ').last.toDouble
              }
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
          val report_yosys = Source.fromFile(s"${buildDir + "/" + toplevelName}.rpt").getLines().mkString("\n")

          val num_cells = {
            val cells = "cells: +([0-9]+)\\n".r.findAllIn(report_yosys)
            if (cells.nonEmpty) {
              cells.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }
          val num_lut4 = {
            val lut4 = "SB_LUT4 +([0-9]+)\\n".r.findAllIn(report_yosys)
            if (lut4.nonEmpty) {
              lut4.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }
          val num_dff = {
            val dff = "SB_DFF +([0-9]+)\\n".r.findAllIn(report_yosys)
            if (dff.nonEmpty) {
              dff.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }
          val num_dffe = {
            val dffe = "SB_DFFE +([0-9]+)\\n".r.findAllIn(report_yosys)
            if (dffe.nonEmpty) {
              dffe.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }
          val num_dffer = {
            val dffer = "SB_DFFER +([0-9]+)\\n".r.findAllIn(report_yosys)
            if (dffer.nonEmpty) {
              dffer.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }
          val num_dffess = {
            val dffess = "SB_DFFESS +([0-9]+)\\n".r.findAllIn(report_yosys)
            if (dffess.nonEmpty) {
              dffess.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }
          val num_dffr = {
            val dffr = "SB_DFFR +([0-9]+)\\n".r.findAllIn(report_yosys)
            if (dffr.nonEmpty) {
              dffr.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }
          val num_dffs = {
            val dffs = "SB_DFFS +([0-9]+)\\n".r.findAllIn(report_yosys)
            if (dffs.nonEmpty) {
              dffs.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }
          val num_dffsr = {
            val dffsr = "SB_DFFSR +([0-9]+)\\n".r.findAllIn(report_yosys)
            if (dffsr.nonEmpty) {
              dffsr.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }
          val num_dffss = {
            val dffss = "SB_DFFSS +([0-9]+)\\n".r.findAllIn(report_yosys)
            if (dffss.nonEmpty) {
              dffss.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }
          val num_carry = {
            val carry = "SB_CARRY +[0-9]+\\s".r.findAllIn(report_yosys)
            if (carry.nonEmpty) {
              carry.toList.last.mkString.split(' ').last
            } else {
              "0"
            }
          }

          val report_nextpnr = Source.fromFile(s"${buildDir + "/" + toplevelName}.tim").getLines().mkString("\n")

          val num_LC = {
            val cells = "ICESTORM_LC: +([0-9]+)/ +([0-9]+) +([0-9]+)\\%".r.findAllIn(report_nextpnr)
            if (cells.nonEmpty) {
              cells.toList.last.replaceAll("ICESTORM_LC:\\s+", "")
            } else {
              "0"
            }
          }
          val num_RAM = {
            val ram = "ICESTORM_RAM: +([0-9]+)/ +([0-9]+) +([0-9]+)\\%".r.findAllIn(report_nextpnr)
            if (ram.nonEmpty) {
              ram.toList.last.replaceAll("ICESTORM_RAM:\\s+", "")
            } else {
              "0"
            }
          }
          val num_IO = {
            val io = "SB_IO: +([0-9]+)/ +([0-9]+) +([0-9]+)\\%".r.findAllIn(report_nextpnr)
            if (io.nonEmpty) {
              io.toList.last.replaceAll("SB_IO:\\s+", "")
            } else {
              "0"
            }
          }
          val num_GB = {
            val gb = "SB_GB: +([0-9]+)/ +([0-9]+) +([0-9]+)\\%".r.findAllIn(report_nextpnr)
            if (gb.nonEmpty) {
              gb.toList.last.replaceAll("SB_GB:\\s+", "")
            } else {
              "0"
            }
          }
          val num_PLL = {
            val gb = "ICESTORM_PLL: +([0-9]+)/ +([0-9]+) +([0-9]+)\\%".r.findAllIn(report_nextpnr)
            if (gb.nonEmpty) {
              gb.toList.last.replaceAll("ICESTORM_PLL:\\s+", "")
            } else {
              "0"
            }
          }

          // Finally return the utilization
          s"""Before PnR:
             |Number of cells:  ${num_cells}
             |Number of LUT4:   ${num_lut4}
             |Number of DFF:    ${num_dff}
             |Number of DFFE:   ${num_dffe}
             |Number of DFFER:  ${num_dffer}
             |Number of DFFESS: ${num_dffess}
             |Number of DFFR:   ${num_dffr}
             |Number of DFFS:   ${num_dffs}
             |Number of DFFSR:  ${num_dffsr}
             |Number of DFFSS:  ${num_dffss}
             |Number of CARRY:  ${num_carry}
             |\n
             |After PnR:
             |Number of cells:  ${num_LC}
             |Number of RAM:    ${num_RAM}
             |Number of IO:     ${num_IO}
             |Number of GB:     ${num_GB}
             |Number of PLL:    ${num_PLL}
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
