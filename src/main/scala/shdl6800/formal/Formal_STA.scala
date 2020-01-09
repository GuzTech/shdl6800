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

package shdl6800.formal

import shdl6800.Consts.ModeBits
import spinal.core._

class Formal_STA extends Verification {
  override def valid(instr: Bits): Bool = {
    (instr === M"1--1_0111") || (instr === M"1-10_0111")
  }

  override def check(instr: Bits, data: FormalData): Unit = {
    // Asserts are not possible with combinatorial signals in SpinalHDL yet...
    val mode  = instr(4 to 5)
    val b     = instr(6)
    val input = Mux(b, data.pre_b, data.pre_a)

    assert(data.post_a === data.pre_a)
    assert(data.post_b === data.pre_b)
    assert(data.post_x === data.pre_x)
    assert(data.post_sp === data.pre_sp)

    when(mode === ModeBits.DIRECT) {
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 2).asBits)

      assert(data.addresses_read === 1)
      assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)

      assert(data.addresses_written === 1)
      assert(data.write_addr(0) === data.read_data(0).resize(16))
      assert(data.write_data(0) === input)
    }.elsewhen(mode === ModeBits.EXTENDED) {
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 3).asBits)

      assert(data.addresses_read === 2)
      assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)
      assert(data.read_addr(1) === data.plus16(data.pre_pc.asSInt, 2).asBits)

      assert(data.addresses_written === 1)
      assert(data.write_addr(0) === Cat(data.read_data(0), data.read_data(1)))
      assert(data.write_data(0) === input)
    }.elsewhen(mode === ModeBits.INDEXED) {
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 2).asBits)

      assert(data.addresses_read === 1)
      assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)

      assert(data.addresses_written === 1)
      assert(data.write_addr(0) === data.plus16(data.pre_x.asSInt, data.read_data(0).resize(16).asSInt).asBits)
      assert(data.write_data(0) === input)
    }

    assertFlags(
      data.post_ccs,
      data.pre_ccs,
      Z = Some(input === 0),
      N = Some(input(7)),
      V = Some(False))
  }
}
