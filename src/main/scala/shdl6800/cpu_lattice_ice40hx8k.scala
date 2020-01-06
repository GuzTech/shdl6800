package shdl6800

import lattice._
import spinal.core._

// This is the software ROM
object mem {
  val mem = Map(
    0xFFFE -> 0x12,
    0xFFFF -> 0x34,
    0x1234 -> 0x7E, // JMP 0x1234
    0x1235 -> 0x12,
    0x1236 -> 0x34
  )
}

class SHDL6800 extends Component {
  val io = new Bundle {
    val clk    = in Bool
    val resetn = in Bool
  }

  val cpu = new Core()
  val ph1 = ClockDomain.internal(
    name   = "ph1",
    config = ClockDomainConfig(
      clockEdge        = RISING,
      resetKind        = SYNC,
      resetActiveLevel = LOW))
  val ph2 = ClockDomain.internal(
    name = "ph2",
    config = ClockDomainConfig(
      clockEdge        = FALLING,
      resetKind        = SYNC,
      resetActiveLevel = LOW))

}

object cpu_lattice_ice40hx8k {
  def main(args: Array[String]): Unit = {
    val toplevelName = SpinalVerilog(new SHDL6800()).toplevelName

    val report = YosysNextpnrFlow(
      toplevelName = toplevelName,
      deviceName   = DeviceName.iCE40HX8K,
      packageType  = PackageType.CT256,
      buildDir     = "./build")

    println(s"Resource usage:\n${report.getArea()}")
    println(s"\nMax clock frequency: ${report.getFMax()} MHz")
  }
}