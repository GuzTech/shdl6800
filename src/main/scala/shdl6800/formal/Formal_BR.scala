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

object Branch extends SpinalEnum {
  val A, N, HI, LS, CC, CS, NE, EQ, VC, VS, PL, MI, GE, LT, GT, LE = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding") (
    A  -> 0x0,
    N  -> 0x1,
    HI -> 0x2,
    LS -> 0x3,
    CC -> 0x4,
    CS -> 0x5,
    NE -> 0x6,
    EQ -> 0x7,
    VC -> 0x8,
    VS -> 0x9,
    PL -> 0xA,
    MI -> 0xB,
    GE -> 0xC,
    LT -> 0xD,
    GT -> 0xE,
    LE -> 0xF
  )
}

class Formal_BR extends Verification {
  override def valid(instr: Bits): Bool = {
    instr === M"0010_----"
  }

  override def check(instr: Bits, data: FormalData): Unit = {
    // Asserts are not possible with combinatorial signals in SpinalHDL yet...

    assert(data.post_ccs === data.pre_ccs)
    assert(data.post_a === data.pre_a)
    assert(data.post_b === data.pre_b)
    assert(data.post_x === data.pre_x)
    assert(data.post_sp === data.pre_sp)
    assert(data.addresses_written === 0)

    assert(data.addresses_read === 1)
    assert(data.read_addr(0) === data.plus16(data.pre_pc.asSInt, 1).asBits)

    val n  = data.pre_ccs(Flags.N)
    val z  = data.pre_ccs(Flags.Z)
    val v  = data.pre_ccs(Flags.V)
    val c  = data.pre_ccs(Flags.C)
    val br = instr(3 downto 0).asUInt

    val take_branch = Vec(
      True,
      False,
      (c | z) === False,
      (c | z) === True,
      c === False,
      c === True,
      z === False,
      z === True,
      v === False,
      v === True,
      n === False,
      n === True,
      (n ^ v) === False,
      (n ^ v) === True,
      (z | (n ^ v)) === False,
      (z | (n ^ v)) === True
    )
    val offset = data.read_data(0)
    assert(data.post_pc === Mux(
      take_branch(br),
      (data.pre_pc.asSInt + 2 + offset.asSInt)(15 downto 0),
      (data.pre_pc.asSInt + 2)(15 downto 0)
    ).asBits)
  }
}