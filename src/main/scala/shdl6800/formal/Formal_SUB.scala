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

import shdl6800.Flags
import spinal.core._

class Formal_SUB extends Verification {
  override def valid(instr: Bits): Bool = {
    instr === M"1-1100-0"
  }

  override def check(instr: Bits, data: FormalData): Unit = {
    // Asserts are not possible with combinatorial signals in SpinalHDL yet...

    val b         = instr(6)
    val pre_input = Mux(b, data.pre_b, data.pre_a)
    val output    = Mux(b, data.post_b, data.post_a)

    val carry_in = UInt(1 bit)
    val sum9     = UInt(9 bits)
    val sum8     = UInt(8 bits)
    val with_carry = (data.instr(1) === True)

    when(b) {
      assert(data.post_a === data.pre_a)
    } otherwise {
      assert(data.post_b === data.pre_b)
    }

    assert(data.post_x === data.pre_x)
    assert(data.post_sp === data.pre_sp)
    assert(data.addresses_written === 0)

    assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 3).asBits)
    assert(data.addresses_read === 3)
    assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)
    assert(data.read_addr(1) === data.plus16(data.pre_pc.asSInt, 2).asBits)
    assert(data.read_addr(2) === Cat(data.read_data(0), data.read_data(1)))

    val n = sum9(7)
    val c = ~sum9(8)
    val z = sum9(7 downto 0) === 0
    val v = (sum8(7) ^ sum9(8))

    when(with_carry) {
      carry_in := data.pre_ccs(Flags._C).asUInt
    } otherwise {
      carry_in := 0
    }

    /* We have to extend the bits we're interested in with a zero bit,
     * because in version 1.3.6 of SpinalHDL, the carry-out is not generated.
     * The development version does have addition with carry-out, but we're
     * sticking to the latest stable release. */
    val input1 = pre_input.resize(9).asUInt
    val input2 = data.read_data(2).resize(9).asUInt

    sum9 := (input1 + (~data.read_data(2)).resize(9).asUInt + ~carry_in)
    sum8 := (input1(6 downto 0).resize(8) + (~input2(6 downto 0)).resize(8) + ~carry_in)
    assert(output === sum9(7 downto 0).asBits)

    assertFlags(
      data.post_ccs,
      data.pre_ccs,
      Z = Some(z),
      N = Some(n),
      V = Some(v),
      C = Some(c))
  }
}
