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

class AluVerification extends Verification {
  /* Does common checks for ALU instructions.
   *
   * Returns a tuple of Bits: (input1, input2, actual_output). The caller should use those
   * values to verify flags and expected output.
   */
  def common_check(instr: Bits, data: FormalData): (Bits, Bits, Bits) = {
    val mode          = instr(5 downto 4)
    val b             = instr(6)
    val input1        = Mux(b, data.pre_b, data.pre_a)
    val input2        = Bits(8 bits)
    val actual_output = Mux(b, data.post_b, data.post_a)

    // Give this a default value, or else the compiler detects a latch
    input2 := 0

    when(b) {
      assert(data.post_a === data.pre_a)
    } otherwise {
      assert(data.post_b === data.pre_b)
    }

    assert(data.post_x === data.pre_x)
    assert(data.post_sp === data.pre_sp)
    assert(data.addresses_written === 0)

    when(mode === ModeBits.DIRECT) {
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 2).asBits)
      assert(data.addresses_read === 2)
      assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)
      assert(data.read_addr(1) === data.read_data(0).resize(16))

      input2 := data.read_data(1)
    }.elsewhen(mode === ModeBits.EXTENDED) {
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 3).asBits)
      assert(data.addresses_read === 3)
      assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)
      assert(data.read_addr(1) === data.plus16(data.pre_pc.asSInt, 2).asBits)
      assert(data.read_addr(2) === Cat(data.read_data(0), data.read_data(1)))

      input2 := data.read_data(2)
    }.elsewhen(mode === ModeBits.IMMEDIATE) {
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 2).asBits)
      assert(data.addresses_read === 1)
      assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)

      input2 := data.read_data(0)
    }.elsewhen(mode === ModeBits.INDEXED) {
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 2).asBits)
      assert(data.addresses_read === 2)
      assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)
      assert(data.read_addr(1) === data.plus16(data.pre_x.asSInt, data.read_data(0).resize(16).asSInt).asBits)

      input2 := data.read_data(1)
    }

    (input1, input2, actual_output)
  }
}

class Alu2Verification extends Verification {
  /* Does common checks for ALU instructions from 0x40 to 0x7F.
   *
   * Returns a tuple of Bits: (input1, input2, actual_output). The caller should use those
   * values to verify flags and expected output.
   */
  def common_check(instr: Bits, data: FormalData, store: Boolean = true): (Bits, Bits) = {
    val mode          = instr(5 downto 4)
    val input         = Bits(8 bits)
    val actual_output = Bits(8 bits)

    // Assign default values, or else the compiler detects a latch
    input         := 0
    actual_output := 0

    assert(data.post_x === data.pre_x)
    assert(data.post_sp === data.pre_sp)

    when(mode === ModeBits.A) {
      assert(data.post_b === data.pre_b)
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 1).asBits)
      assert(data.addresses_read === 0)
      assert(data.addresses_written === 0)

      input := data.pre_a

      if(store) {
        actual_output := data.post_a
      } else {
        assert(data.post_a === data.pre_a)
      }
    }.elsewhen(mode === ModeBits.B) {
      assert(data.post_a === data.pre_a)
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 1).asBits)
      assert(data.addresses_read === 0)
      assert(data.addresses_written === 0)

      input := data.pre_b

      if(store) {
        actual_output := data.post_b
      } else {
        assert(data.post_b === data.pre_b)
      }
    }.elsewhen(mode === ModeBits.EXTENDED) {
      assert(data.post_a === data.pre_a)
      assert(data.post_b === data.pre_b)
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 3).asBits)
      assert(data.addresses_read === 3)
      assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)
      assert(data.read_addr(1) === data.plus16(data.pre_pc.asSInt, 2).asBits)
      assert(data.read_addr(2) === Cat(data.read_data(0), data.read_data(1)))

      input := data.read_data(2)

      if(store) {
        assert(data.addresses_written === 1)
        assert(data.write_addr(0) === data.read_addr(2))
        actual_output := data.write_data(0)
      } else {
        assert(data.addresses_written === 0)
      }
    }.elsewhen(mode === ModeBits.INDEXED) {
      assert(data.post_a === data.pre_a)
      assert(data.post_b === data.pre_b)
      assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 2).asBits)
      assert(data.addresses_read === 2)
      assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)
      assert(data.read_addr(1) === data.plus16(data.pre_x.asSInt, data.read_data(0).resize(16).asSInt).asBits)

      input := data.read_data(1)

      if(store) {
        assert(data.addresses_written === 1)
        assert(data.write_addr(0) === data.read_addr(1))
        actual_output := data.write_data(0)
      } else {
        assert(data.addresses_written === 0)
      }
    }

    (input, actual_output)
  }
}