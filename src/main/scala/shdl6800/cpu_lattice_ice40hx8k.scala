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
    val clk1   = !slowClock generate(in Bool)
    val clk2   = !slowClock generate(in Bool)
    val resetn = !slowClock generate(in Bool)
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
      resetKind        = ASYNC,
      resetActiveLevel = HIGH))

  val ph2 = ClockDomain.internal(
    name = "ph2",
    config = ClockDomainConfig(
      clockEdge        = FALLING,
      resetKind        = ASYNC,
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
    ph1.reset := clockDomain.isResetActive
    ph2.clock := ~tick
    ph2.reset := clockDomain.isResetActive
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
    val clockConfig     = ClockDomainConfig(
      clockEdge        = RISING,
      resetKind        = ASYNC,
      resetActiveLevel = HIGH
    )
    val design: SpinalReport[SHDL6800] = SpinalConfig(defaultConfigForClockDomains = clockConfig).generateVerilog(
      new SHDL6800(defaultClkFreq = 12 MHz, slowClock = true, fakeMem = true)
    )
//    val design          = SpinalVerilog(new SHDL6800(defaultClkFreq = 12 MHz, slowClock = true, fakeMem = true))
    val toplevelName    = design.toplevelName
    val toplevelSignals = design.toplevel.getAllIo
    toplevelSignals.foreach{ case s => println(s.toString()) }

    val pinMapping: Map[String, String] = Map(
      "clk"     -> "J3",
      "reset"   -> "R9",
      "io_addr" -> "B1 B2 C1 C2 D1 D2 E2 F1 F2 G2 H1 H2 J2 J1 K3 K1",
      "io_led"  -> "C3 B3 C4 C5 A1 A2 B4 B5",
      "io_rw"   -> "E4"
    )

    val report = YosysNextpnrFlow(
      toplevel    = design,
      deviceName  = DeviceName.iCE40HX8K,
      packageType = PackageType.CT256,
      pinMappings = pinMapping,
      buildDir    = "./build"
    )

    println(s"Resource usage:\n${report.getArea()}")
    println(s"\nMax clock frequency: ${report.getFMax()} MHz")
  }
}