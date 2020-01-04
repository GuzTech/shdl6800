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

import spinal.core._

class Formal_CMP extends Verification {
  override def valid(instr: Bits): Bool = {
    instr === M"1-11_0001"
  }

  override def check(instr: Bits, data: FormalData): Unit = {
    // Asserts are not possible with combinatorial signals in SpinalHDL yet...

    val b         = instr(6)
    val pre_input = Mux(b, data.pre_b, data.pre_a)

    assert(data.post_a === data.pre_a)
    assert(data.post_b === data.pre_b)
    assert(data.post_x === data.pre_x)
    assert(data.post_sp === data.pre_sp)
    assert(data.addresses_written === 0)

    assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 3).asBits)
    assert(data.addresses_read === 3)
    assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)
    assert(data.read_addr(1) === data.plus16(data.pre_pc.asSInt, 2).asBits)
    assert(data.read_addr(2) === Cat(data.read_data(0), data.read_data(1)))

    val input1  = pre_input.asUInt
    val input2  = data.read_data(2).asUInt
    val sinput1 = pre_input.asSInt
    val sinput2 = data.read_data(2).asSInt

    val z = input1 === input2
    val n = (input1 - input2)(7)

    // GE is true if and only if N ^ V == 0 (i.e. N == V)
    val ge = sinput1 >= sinput2
    val v  = Mux(ge, n, ~n)
    val c  = (input1 < input2)

    assertFlags(
      data.post_ccs,
      data.pre_ccs,
      Z = Some(z),
      N = Some(n),
      V = Some(v),
      C = Some(c))
  }
}
