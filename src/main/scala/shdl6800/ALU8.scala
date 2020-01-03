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

package shdl6800

import spinal.core._

// Flags
object Flags {
  val _H = 5
  val _I = 4
  val _N = 3
  val _Z = 2
  val _V = 1
  val _C = 0
}

//object ALU8Func extends SpinalEnum {
//  val NONE, LD, ADD, ADC, SUB, SBC = newElement()
//  defaultEncoding = SpinalEnumEncoding("staticEncoding") (
//    NONE -> 0x00,
//    LD   -> 0x01,
//    ADD  -> 0x02,
//    ADC  -> 0x04,
//    SUB  -> 0x08,
//    SBC  -> 0x10
//  )
//}

object ALU8Func extends SpinalEnum {
  val NONE, LD = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding") (
    NONE -> 0x00,
    LD   -> 0x01
  )
}

class ALU8 extends Component {
  val io = new Bundle {
    val input1 = in  Bits(8 bits)
    val input2 = in  Bits(8 bits)
    val output = out Bits(8 bits)
    val func   = in  (ALU8Func())
    val ccs    = out Bits(8 bits)
  }

  val ccs  = Reg(Bits(8 bits)) init(B"11010000")
  val _ccs = Bits(8 bits)

  io.ccs := ccs

  _ccs := ccs
  ccs  := _ccs

  val carry4   = Bool
  val carry7   = Bool
  val carry8   = Bool
  val overflow = Bool

  switch(io.func) {
    is(ALU8Func.LD) {
      io.output := io.input1
      _ccs(Flags._Z) := (io.input1 === 0)
      _ccs(Flags._N) := (io.input1(7))
      _ccs(Flags._V) := False
    }
//    is(ALU8Func.ADD, ALU8Func.ADC) {
//      val in1_ext = io.input1.asUInt.resize(9)
//      val in2_ext = io.input2.asUInt.resize(9)
//
//      val carry_in = Mux(io.func === ALU8Func.ADD, U"0", ccs(Flags._C).asUInt)
//
////      val sum0_3 = Cat(io.output(3 downto 0), carry4)
//      val sum0_3 = io.input1(3 downto 0).asUInt + io.input2(3 downto 0).asUInt + carry_in
//
//      val sum7 = io.input1(7).asUInt + io.input2(7).asUInt + carry7.asUInt
//      overflow := carry7 ^ carry8
//      _ccs(Flags._H) := carry4
//      _ccs(Flags._N) := io.output(7)
//      _ccs(Flags._Z) := io.output === 0
//      _ccs(Flags._V) := overflow
//      _ccs(Flags._C) := carry8
//    }
//    is(ALU8Func.SUB, ALU8Func.SBC) {
//
//    }
    default {
      io.output := 0
    }
  }
}

//object ALU8 {
//  def main(args: Array[String]): Unit = {
//    import spinal.core.Formal._
//
//    val config = SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = HIGH))
//    config.includeFormal.generateSystemVerilog {
//      val alu: ALU8 = new ALU8 {
//        val carry_in = UInt(1 bit)
//        val sum9     = UInt(9 bits)
//        val sum8     = UInt(8 bits)
//        val sum5     = UInt(5 bits)
//
//        assert(_ccs(7 downto 6) === B"11")
//
////        switch(io.func) {
////          is(ALU8Func.ADD, ALU8Func.ADC) {
////            // sumN = input1(N-1 downto 0) + input2(N-1 downto 0) (so sumN(N-1) is the carry bit)
////            when(io.func === ALU8Func.ADD) {
////              carry_in := U"0"
////            } otherwise {
////              carry_in := ccs(Flags._C).asUInt
////            }
////
////            val h = sum5(4)
////            val n = sum9(7)
////            val c = sum9(8)
////            val z = (sum9(7 downto 0) === 0)
////            val v = (sum8(7) ^ c)
////
////            sum9 := (io.input1.asUInt + io.input2.asUInt + carry_in)
////            sum8 := (io.input1(6 downto 0).asUInt + io.input2(6 downto 0).asUInt + carry_in)
////            sum5 := (io.input1(3 downto 0).asUInt + io.input2(3 downto 0).asUInt + carry_in)
////            assert(io.output === sum9(7 downto 0).asBits)
////            assert(_ccs(Flags._H) === h)
////            assert(_ccs(Flags._N) === n)
////            assert(_ccs(Flags._Z) === z)
////            assert(_ccs(Flags._V) === v)
////            assert(_ccs(Flags._C) === c)
////            assert(_ccs(Flags._I) === ccs(Flags._I))
////          }
////          is(ALU8Func.SUB, ALU8Func.SBC) {
////            when(io.func === ALU8Func.SUB) {
////              carry_in := U"0"
////            } otherwise {
////              carry_in := ccs(Flags._C).asUInt
////            }
////
////            val n = sum9(7)
////            val c = ~sum9(8)
////            val z = (sum9(7 downto 0) === 0)
////            val v = (sum8(7) ^ sum9(8))
////
////            sum9 := (io.input1.asUInt + ~io.input2.asUInt + ~carry_in)
////            sum8 := (io.input1(6 downto 0).asUInt + ~io.input2(6 downto 0).asUInt + ~carry_in)
////            assert(sum9(7 downto 0).asBits === (io.input1.asSInt - io.input2.asSInt - carry_in.asSInt).asBits)
////            assert(io.output === sum9(7 downto 0).asBits)
////            assert(_ccs(Flags._N) === n)
////            assert(_ccs(Flags._Z) === z)
////            assert(_ccs(Flags._V) === v)
////            assert(_ccs(Flags._C) === c)
////            assert(_ccs(Flags._H) === ccs(Flags._H))
////            assert(_ccs(Flags._I) === ccs(Flags._I))
////          }
////        }
//      }
//
//      alu.setDefinitionName("ALU8")
//      alu
//    }.printPruned()
//  }
//}