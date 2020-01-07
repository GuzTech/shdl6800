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

class Formal_ORA extends AluVerification {
  override def valid(instr: Bits): Bool = {
    instr === M"1---_1010"
  }

  override def check(instr: Bits, data: FormalData): Unit = {
    // Asserts are not possible with combinatorial signals in SpinalHDL yet...

    val (input1, input2, actual_output) = common_check(instr, data)
    val output                          = input1 | input2

    val z = output === 0
    val n = output(7)
    val v = False

    assert(actual_output === output)
    assertFlags(
      data.post_ccs,
      data.pre_ccs,
      Z = Some(z),
      N = Some(n),
      V = Some(v))
  }
}
