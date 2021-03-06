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

import shdl6800.Consts.{Flags, ModeBits}
import spinal.core._

class Formal_TPA extends Verification {
  override def valid(instr: Bits): Bool = {
    instr === B"0000_0111"
  }

  override def check(instr: Bits, data: FormalData): Unit = {
    // Asserts are not possible with combinatorial signals in SpinalHDL yet...

    assert(data.post_ccs === data.pre_ccs)
    assert(data.post_b === data.pre_b)
    assert(data.post_x === data.pre_x)
    assert(data.post_sp === data.pre_sp)
    assert(data.addresses_written === 0)
    assert(data.addresses_read === 0)

    assert(data.post_pc === data.plus16(data.pre_pc.asSInt, 1).asBits)
    assert(data.post_a === (B"1100_0000" | data.pre_ccs))
  }
}