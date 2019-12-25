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

      val mem = Map(0xFFFE -> 0x12,
                    0xFFFF -> 0x34,
                    0x1234 -> 0x7E,
                    0x1235 -> 0xA0,
                    0x1236 -> 0x10,
                    0xA010 -> 0x01 )

      for(i <- 0 to 15) {
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
