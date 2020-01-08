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

class Formal_CMP extends AluVerification {
  override def valid(instr: Bits): Bool = {
    instr === M"1---_0001"
  }

  override def check(instr: Bits, data: FormalData): Unit = {
    // Asserts are not possible with combinatorial signals in SpinalHDL yet...

    val (input1, input2, actual_output) = common_check(instr, data)
    val sinput1                         = input1.asSInt
    val sinput2                         = input2.asSInt
    val output                          = input1

    val z = input1 === input2
    val n = (input1.asUInt - input2.asUInt)(7)

    // GE is true if and only if N ^ V == 0 (i.e. N == V)
    val ge = sinput1 >= sinput2
    val v  = Mux(ge, n, ~n)
    val c  = (input1.asUInt < input2.asUInt)

    assert(actual_output === output)
    assertFlags(
      data.post_ccs,
      data.pre_ccs,
      Z = Some(z),
      N = Some(n),
      V = Some(v),
      C = Some(c))
  }
}
