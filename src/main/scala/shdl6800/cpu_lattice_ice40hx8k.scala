package shdl6800

import lattice._
import spinal.core._

import scala.collection.mutable

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

class SHDL6800(val defaultClkFreq: HertzNumber,
               val slowClock: Boolean = false,
               val fakeMem: Boolean = false) extends Component
{
  val io = new Bundle {
    val clk1   = in Bool
    val clk2   = in Bool
    val resetn = in Bool
    val addr   = out Bits(16 bits)
    val rw     = out Bits(1 bit)

    val led  =  fakeMem generate(out Bits(8 bits))
    val data = !fakeMem generate(inout(Analog(Bits(8 bits))))
  }

  // Define clock domains
  val ph1 = ClockDomain.internal(
    name   = "ph1",
    config = ClockDomainConfig(
      clockEdge        = RISING,
      resetKind        = SYNC,
      resetActiveLevel = HIGH))

  val ph2 = ClockDomain.internal(
    name = "ph2",
    config = ClockDomainConfig(
      clockEdge        = FALLING,
      resetKind        = SYNC,
      resetActiveLevel = HIGH))

  if(slowClock) {
    val clk_freq = defaultClkFreq
    val timer = Reg(UInt(log2Up((clk_freq / 2).toInt) bits)) init(((clk_freq / 2) - 1).toInt)
    val tick  = Reg(Bool) init(False)

    when(timer === 0) {
      timer := (((clk_freq / 2) - 1).toInt)
      tick := ~tick
    } otherwise {
      timer := (timer - 1)
    }

    // Hook up clocks and reset to pins
    ph1.clock := tick
    ph1.reset := io.resetn
    ph2.clock := ~tick
    ph2.reset := io.resetn
  } else {
    // Hook up clocks and reset to pins
    ph1.clock := io.clk1
    ph1.reset := io.resetn
    ph2.clock := io.clk2
    ph2.reset := io.resetn
  }

  val CoreArea = new ClockingArea(ph1) {
    val cpu = new Core()

    // Hook up address lines to pins
    io.addr := cpu.io.Addr

    if(fakeMem) {
      switch(cpu.io.Addr) {
        mem.mem.foreach{ case (addr, data) => is(addr) {cpu.io.Din := data} }
        default {cpu.io.Din := 0xFF}
      }
      io.led(io.led.getWidth - 1 downto 0) := cpu.io.Addr(io.led.getWidth - 1 downto 0)
    } else {
      // Hook up data in/out + direction to pins
      cpu.io.Din := io.data

      when(cpu.io.RW === 0) {
        io.data := cpu.io.Dout
      }
    }

    io.rw := cpu.io.RW
  }
}

object cpu_lattice_ice40hx8k {
  def main(args: Array[String]): Unit = {
    val design          = SpinalVerilog(new SHDL6800(defaultClkFreq = 12 MHz, slowClock = true, fakeMem = true))
    val toplevelName    = design.toplevelName
    val toplevelSignals = design.toplevel.getAllIo
    toplevelSignals.foreach{ case s => println(s.toString()) }

//    val pinMapping      = toplevelSignals.toMap(
//      "io_clk1"   -> "J3",
//      "io_clk2"   -> "G1",
//      "io_resetn" -> "R9"
//    )

    val report = YosysNextpnrFlow(
      toplevelName = toplevelName,
      deviceName   = DeviceName.iCE40HX8K,
      packageType  = PackageType.CT256,
      buildDir     = "./build")

    println(s"Resource usage:\n${report.getArea()}")
    println(s"\nMax clock frequency: ${report.getFMax()} MHz")
  }
}