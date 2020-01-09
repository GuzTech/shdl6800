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

import shdl6800.Consts.Flags
import spinal.core._

class Formal_SH_ROT extends Alu2Verification {
  val ROL = M"01--_1001"
  val ROR = M"01--_0110"
  val ASL = M"01--_1000"
  val ASR = M"01--_0111"
  val LSR = M"01--_0100"

  override def valid(instr: Bits): Bool = {
    instr === ROR || instr === ROL || instr === ASL ||
    instr === ASR || instr === LSR
  }

  override def check(instr: Bits, data: FormalData): Unit = {
    // Asserts are not possible with combinatorial signals in SpinalHDL yet...

    val (input, actual_output) = common_check(instr, data)
    val expected_output        = Bits(8 bits)
    val pre_c                  = data.pre_ccs(Flags.C)
    val c                      = Bool

    // Assign default values, or else the compiler detects a latch
    expected_output := 0
    c               := False

    when(instr === ROL) {
      // input[7..0], c ->
      // c, output[7..0]
      c                           := input.msb
      expected_output.lsb         := pre_c
      expected_output(7 downto 1) := input(6 downto 0)
    }.elsewhen(instr === ROR) {
      // c, input[7..0] ->
      // output[7..0], c
      c                           := input.lsb
      expected_output.msb         := pre_c
      expected_output(6 downto 0) := input(7 downto 1)
    }.elsewhen(instr === ASL) {
      // input[7..0], 0 ->
      // c, output[7..0]
      c                           := input.msb
      expected_output.lsb         := False
      expected_output(7 downto 1) := input(6 downto 0)
    }.elsewhen(instr === ASR) {
      // input[7], input[7..0] ->
      // output[7..0], c
      c                           := input.lsb
      expected_output.msb         := input.msb
      expected_output(6 downto 0) := input(7 downto 1)
    }.elsewhen(instr === LSR) {
      // 0, input[7..0] ->
      // output[7..0], c
      c                           := input.lsb
      expected_output.msb         := False
      expected_output(6 downto 0) := input(7 downto 1)
    }

    assert(expected_output === actual_output)

    val n = expected_output(7)
    val z = expected_output === 0
    val v = n ^ c

    assertFlags(
      data.post_ccs,
      data.pre_ccs,
      Z = Some(z),
      N = Some(n),
      V = Some(v),
      C = Some(c))
  }
}
