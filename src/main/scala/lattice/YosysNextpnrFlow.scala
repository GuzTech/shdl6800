package lattice

import java.io.File

import spinal.core.Device
import spinal.lib.eda.bench.Report

import scala.sys.process.Process

object NextpnrOptions {
  val device_options = Map(
    "iCE40LP384" -> "--lp384",
    "iCE40LP1K"  -> "--lp1k",
    "iCE40LP4K"  -> "--lp8k",
    "iCE40LP8K"  -> "--lp8k",
    "iCE40HX1K"  -> "--hx1k",
    "iCE40HX4K"  -> "--hx8k",
    "iCE40HX8K"  -> "--hx8k",
    "iCE40UP3K"  -> "--up5k",
    "iCE40UP5K"  -> "--up5k",
    "iCE5LP1K"   -> "--u4k",
    "iCE5LP2K"   -> "--u4k",
    "iCE5LP4K"   -> "--u4k"
  )

}

object YosysNextpnrFlow {
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

  def apply(toplevelName: String, device_options: String, buildDir: String): Report = {
    doCmd(s"mkdir -p ${buildDir}")
    val yosys = Seq(
      "yosys",
      "-l", s"${toplevelName}.rpt",
      "-p", s"synth_ice40 -top ${toplevelName} -json ${toplevelName}.json",
      s"../${toplevelName}.v")
    doCmd(yosys, buildDir)
    val nextpnr = Seq(
      "nextpnr-ice40",
      "-l", s"${toplevelName}.tim",
      device_options,
      "--package", "ct256",
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
          if(all_freqs.nonEmpty) {
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
        val report = Source.fromFile(s"${buildDir + "/" + toplevelName}.tim").getLines().mkString
        val max_freq = try {
          val all_freqs = "[0-9]+.[0-9]+ MHz ".r.findAllIn(report)
          if(all_freqs.nonEmpty) {
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
        //return max_freq
        ""
      }
    }
  }
}
