// shdl6800: A 6800 processor written in SpinalHDL
//
// Copyright (C) 2019 Oguz Meteer <info@guztech.nl>
//
// Permission to use, copy, modify, and/or distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
// ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
// ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
// OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

package shdl6800

import spinal.core._
import spinal.core.sim._

//Core's testbench
object CoreSim {
  def main(args: Array[String]) {
    val spinalConfig = SpinalConfig(defaultClockDomainFrequency = FixedFrequency(1 MHz))

    SimConfig
      .withConfig(spinalConfig)
      .withWave
      .allOptimisation
      .compile(new Core)
      .doSim
    { dut =>
      // Reset generation
      fork {
        dut.clockDomain.assertReset()
        sleep(10)
        dut.clockDomain.deassertReset()
      }

      // Clock generation
      fork {
        //sleep(15)
        dut.clockDomain.fallingEdge()
        sleep(5)
        while(true) {
          dut.clockDomain.clockToggle()
          sleep(5)
        }
      }

      val mem = Map(0xFFFE -> 0x12, // Reset vector high
                    0xFFFF -> 0x34, // Reset vector low
                    0x1234 -> 0x7E, // JMP ext
                    0x1235 -> 0xA0, // Address high
                    0x1236 -> 0x10, // Address low
                    0xA010 -> 0x20, // BRA
                    0xA011 -> 0x01, // rel = 1
                    0xA012 -> 0x01, // NOP
                    0xA013 -> 0x0A, // Clear overflow flag
                    0xA014 -> 0x29, // BVS
                    0xA015 -> 0x10, // rel = 16
                    0xA016 -> 0x28, // BVC
                    0xA017 -> 0x01, // rel = 1
                    0xA018 -> 0x01, // NOP
                    0xA019 -> 0x7E, // JMP ext
                    0xA01A -> 0x12, // Address high
                    0xA01B -> 0x34) // Address low

      for(i <- 0 to 30) {
        dut.clockDomain.waitFallingEdge()
        if(mem.contains(dut.io.Addr.toInt)) {
          dut.io.Din #= mem(dut.io.Addr.toInt)
        } else {
          dut.io.Din #= 0xFF
        }
      }
    }
  }
}
