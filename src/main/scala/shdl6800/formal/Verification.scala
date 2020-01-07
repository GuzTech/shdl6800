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

class Verification {
  def valid(instr: Bits): Bool = {
    True
  }

  def check(instr: Bits, data: FormalData): Unit = {}

  def flags(prev: Bits,
            H: Option[Bool] = None,
            I: Option[Bool] = None,
            N: Option[Bool] = None,
            Z: Option[Bool] = None,
            V: Option[Bool] = None,
            C: Option[Bool] = None): Bits = {
    val result = Bits(8 bits)

    val h = if(H.isDefined) H.get else prev(Flags.H)
    val i = if(I.isDefined) I.get else prev(Flags.I)
    val n = if(N.isDefined) N.get else prev(Flags.N)
    val z = if(Z.isDefined) Z.get else prev(Flags.Z)
    val v = if(V.isDefined) V.get else prev(Flags.V)
    val c = if(C.isDefined) C.get else prev(Flags.C)

    result := (
      7 -> True,
      6 -> True,
      5 -> h,
      4 -> i,
      3 -> n,
      2 -> z,
      1 -> v,
      0 -> c)

    result
  }

  def assertFlags(post_flags: Bits,
                  pre_flags: Bits,
                  H: Option[Bool] = None,
                  I: Option[Bool] = None,
                  N: Option[Bool] = None,
                  Z: Option[Bool] = None,
                  V: Option[Bool] = None,
                  C: Option[Bool] = None): Unit = {
    val expectedFlags = Bits(8 bits)
    expectedFlags := flags(pre_flags, H, I, N, Z, V, C)

    assert(post_flags(7) === expectedFlags(7))
    assert(post_flags(6) === expectedFlags(6))
    assert(post_flags(Flags.H) === expectedFlags(Flags.H))
    assert(post_flags(Flags.I) === expectedFlags(Flags.I))
    assert(post_flags(Flags.N) === expectedFlags(Flags.N))
    assert(post_flags(Flags.Z) === expectedFlags(Flags.Z))
    assert(post_flags(Flags.V) === expectedFlags(Flags.V))
    assert(post_flags(Flags.C) === expectedFlags(Flags.C))
  }
}

case class FormalData(verification: Option[Verification]) {
  val snapshot_taken    = Reg(Bool) init(False)

  val instr             = Reg(Bits(8 bits))  init(0)

  val pre_ccs           = Reg(Bits(8 bits))  init(B"11010000")
  val pre_a             = Reg(Bits(8 bits))  init(0)
  val pre_b             = Reg(Bits(8 bits))  init(0)
  val pre_x             = Reg(Bits(16 bits)) init(0)
  val pre_sp            = Reg(Bits(16 bits)) init(0)
  val pre_pc            = Reg(Bits(16 bits)) init(0)

  val post_ccs          = Bits(8 bits)
  val post_a            = Bits(8 bits)
  val post_b            = Bits(8 bits)
  val post_x            = Bits(16 bits)
  val post_sp           = Bits(16 bits)
  val post_pc           = Bits(16 bits)

  val addresses_written = Reg(UInt(3 bits)) init(0)
  val write_addr        = Vec(Reg(Bits), 8)
  val write_data        = Vec(Reg(Bits), 8)

  val addresses_read    = Reg(UInt(3 bits)) init(0)
  val read_addr         = Vec(Reg(Bits), 8)
  val read_data         = Vec(Reg(Bits), 8)

  // Assign default values to prevent compiler detecting latches.
  post_ccs := 0
  post_a   := 0
  post_b   := 0
  post_x   := 0
  post_sp  := 0
  post_pc  := 0

  def plus16(v1: SInt, v2: SInt): SInt = {
    val tmp = SInt(16 bits)
    tmp := v1 + v2

    tmp(15 downto 0)
  }

  def plus8(v1: SInt, v2: SInt): SInt = {
    val tmp = SInt(8 bits)
    tmp := v1 + v2

    tmp(7 downto 0)
  }

  def read(addr: Bits, data: Bits): Unit = {
    if(verification.isDefined) {
      when(snapshot_taken) {
        when(addresses_read =/= 7) {
          addresses_read := addresses_read + 1
          read_addr(addresses_read) := addr
          read_data(addresses_read) := data
        }
      }
    }
  }

  def write(addr: Bits, data: Bits): Unit = {
    if(verification.isDefined) {
      when(snapshot_taken) {
        when(addresses_written =/= 7) {
          addresses_written := addresses_written + 1
          write_addr(addresses_written) := addr
          write_data(addresses_written) := data
        }
      }
    }
  }

  def preSnapshot(instr: Bits, ccs: Bits, a: Bits, b: Bits, x: Bits, sp: Bits, pc: Bits): Unit = {
    if(verification.isDefined) {
      snapshot_taken    := True
      addresses_read    := 0
      addresses_written := 0
      this.instr        := instr
      pre_ccs           := ccs
      pre_a             := a
      pre_b             := b
      pre_x             := x
      pre_sp            := sp
      pre_pc            := pc
    }
  }

  def noSnapshot: Unit = {
    if(verification.isDefined) {
      snapshot_taken := False
    }
  }

  def postSnapshot(ccs: Bits, a: Bits, b: Bits, x: Bits, sp: Bits, pc: Bits): Unit = {
    if(verification.isDefined) {
      post_ccs := ccs
      post_a   := a
      post_b   := b
      post_x   := x
      post_sp  := sp
      post_pc  := pc
    }
  }
}
