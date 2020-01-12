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

import shdl6800.Consts.Flags

object ALU8Func extends SpinalEnum {
  val NONE, LD, ADD, ADC, SUB, SBC, AND, EOR, ORA, CLV, SEV,
      CLC, SEC, TAP, TPA, CLI, SEI, CLZ, SEZ, COM, INC, DEC,
      ROL, ROR, ASL, ASR, LSR = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding") (
    NONE -> 0,
    LD   -> 1,
    ADD  -> 2,
    ADC  -> 3,
    SUB  -> 4,
    SBC  -> 5,
    AND  -> 6,
    // BIT is the same as AND, just don't store the output.
    // CMP is the same as SUB, just don't store the output.
    EOR  -> 7,
    ORA  -> 8,
    CLV  -> 9,
    SEV  -> 10,
    CLC  -> 11,
    SEC  -> 12,
    TAP  -> 13,
    TPA  -> 14,
    CLI  -> 15,
    SEI  -> 16,
    CLZ  -> 17,
    SEZ  -> 18,
    COM  -> 19,
    INC  -> 20,
    DEC  -> 21,
    ROL  -> 22,
    ROR  -> 23,
    ASL  -> 24,
    ASR  -> 25,
    LSR  -> 26
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

  // Intermediates
  val carry4   = UInt(1 bit)
  val carry7   = UInt(1 bit)
  val carry8   = UInt(1 bit)
  val overflow = UInt(1 bit)

  // Default values so that the compiler doesn't complain about latches.
  carry4    := 0
  carry7    := 0
  carry8    := 0
  overflow  := 0
  io.output := 0

  switch(io.func) {
    is(ALU8Func.LD) {
      io.output := io.input2
      _ccs(Flags.Z) := (io.output === 0)
      _ccs(Flags.N) := (io.output(7))
      _ccs(Flags.V) := False
    }
    is(ALU8Func.ADD, ALU8Func.ADC) {
      val in1 = io.input1.asUInt
      val in2 = io.input2.asUInt

      val carry_in = Mux(io.func === ALU8Func.ADD, U"0", ccs(Flags.C).asUInt)

      val sum0_3 = in1(3 downto 0) +^ in2(3 downto 0) + carry_in
      carry4 := sum0_3.msb.asUInt

      val sum4_6 = in1(6 downto 4) +^ in2(6 downto 4) + carry4
      carry7 := sum4_6.msb.asUInt

      val sum7 = in1(7).asUInt +^ in2(7).asUInt + carry7
      carry8 := sum7.msb.asUInt

      io.output(3 downto 0) := sum0_3(3 downto 0).asBits
      io.output(6 downto 4) := sum4_6(2 downto 0).asBits
      io.output(7)          := sum7(0)

      overflow      := carry7 ^ carry8
      _ccs(Flags.H) := carry4.asBool
      _ccs(Flags.N) := io.output(7)
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.V) := overflow.asBool
      _ccs(Flags.C) := carry8.asBool
    }
    is(ALU8Func.SUB, ALU8Func.SBC) {
      val in1 = io.input1.asUInt
      val in2 = io.input2.asUInt

      val carry_in = Mux(io.func === ALU8Func.SUB, U"0", ccs(Flags.C).asUInt)

      val sum0_6 = in1(6 downto 0) +^ (~in2(6 downto 0)) + ~carry_in
      carry7 := sum0_6.msb.asUInt

      val sum7 = in1(7).asUInt +^ (~in2(7)).asUInt + carry7
      carry8 := sum7.msb.asUInt

      io.output(6 downto 0) := sum0_6(6 downto 0).asBits
      io.output(7)          := sum7(0)

      overflow := carry7 ^ carry8
      _ccs(Flags.N) := io.output(7)
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.V) := overflow.asBool
      _ccs(Flags.C) := ~carry8.asBool
    }
    is(ALU8Func.AND) {
      io.output     := io.input1 & io.input2
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := io.output(7)
      _ccs(Flags.V) := False
    }
    is(ALU8Func.EOR) {
      io.output     := io.input1 ^ io.input2
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := io.output(7)
      _ccs(Flags.V) := False
    }
    is(ALU8Func.ORA) {
      io.output     := io.input1 | io.input2
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := io.output(7)
      _ccs(Flags.V) := False
    }
    is(ALU8Func.INC) {
      io.output     := (io.input2.asSInt + 1).asBits
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := io.output(7)
      _ccs(Flags.V) := io.output === 0x80
    }
    is(ALU8Func.DEC) {
      io.output     := (io.input2.asSInt - 1).asBits
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := io.output(7)
      _ccs(Flags.V) := io.output === 0x7F
    }
    is(ALU8Func.COM) {
      io.output     := (0xFF ^ io.input2)
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := io.output(7)
      _ccs(Flags.V) := False
      _ccs(Flags.C) := True
    }
    is(ALU8Func.ROL) {
      // IIIIIIIIC ->
      // C00000000

      val tmp   = Cat(io.input2, ccs(Flags.C))
      val new_C = tmp.msb
      val new_N = io.output(7)

      io.output     := tmp(7 downto 0)
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := new_N
      _ccs(Flags.V) := new_N ^ new_C
      _ccs(Flags.C) := new_C
    }
    is(ALU8Func.ROR) {
      // CIIIIIIII ->
      // 00000000C

      val tmp   = Cat(ccs(Flags.C), io.input2)
      val new_C = tmp.lsb
      val new_N = io.output(7)

      io.output     := tmp(8 downto 1)
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := new_N
      _ccs(Flags.V) := new_N ^ new_C
      _ccs(Flags.C) := new_C
    }
    is(ALU8Func.ASL) {
      // IIIIIIII0 ->
      // C00000000

      val tmp   = Cat(io.input2, B"0")
      val new_C = tmp.msb
      val new_N = io.output(7)

      io.output     := tmp(7 downto 0)
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := new_N
      _ccs(Flags.V) := new_N ^ new_C
      _ccs(Flags.C) := new_C
    }
    is(ALU8Func.ASR) {
      // 7IIIIIIII ->  ("7" is the repeat of input[7])
      // 00000000C

      val tmp   = Cat(io.input2(7), io.input2)
      val new_C = tmp.lsb
      val new_N = io.output(7)

      io.output     := tmp(8 downto 1)
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := new_N
      _ccs(Flags.V) := new_N ^ new_C
      _ccs(Flags.C) := new_C
    }
    is(ALU8Func.LSR) {
      // 0IIIIIIII ->
      // 00000000C

      val tmp   = Cat(B"0", io.input2)
      val new_C = tmp.lsb
      val new_N = io.output(7)

      io.output     := tmp(8 downto 1)
      _ccs(Flags.Z) := io.output === 0
      _ccs(Flags.N) := new_N
      _ccs(Flags.V) := new_N ^ new_C
      _ccs(Flags.C) := new_C
    }
    is(ALU8Func.CLC) {
      _ccs(Flags.C) := False
    }
    is(ALU8Func.SEC) {
      _ccs(Flags.C) := True
    }
    is(ALU8Func.CLV) {
      _ccs(Flags.V) := False
    }
    is(ALU8Func.SEV) {
      _ccs(Flags.V) := True
    }
    is(ALU8Func.CLI) {
      _ccs(Flags.I) := False
    }
    is(ALU8Func.SEI) {
      _ccs(Flags.I) := True
    }
    is(ALU8Func.CLZ) {
      _ccs(Flags.Z) := False
    }
    is(ALU8Func.SEZ) {
      _ccs(Flags.Z) := True
    }
    is(ALU8Func.TAP) {
      _ccs := io.input1 | B"1100_0000"
    }
    is(ALU8Func.TPA) {
      io.output := ccs | B"1100_0000"
    }
    default {
      io.output := 0
    }
  }
}

object ALU8 {
  def main(args: Array[String]): Unit = {
    import spinal.core.Formal._

    val config = SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = HIGH))
    config.includeFormal.generateSystemVerilog {
      val alu: ALU8 = new ALU8 {
        val carry_in = UInt(1 bit)
        val sum9     = UInt(9 bits)
        val sum8     = UInt(8 bits)
        val sum5     = UInt(5 bits)

        // Set default values so the compiler doesn't complain about latches.
        carry_in := 0
        sum9     := 0
        sum8     := 0
        sum5     := 0

        // Force a reset
        when(initstate()) {
          assume(clockDomain.isResetActive)
        } otherwise {
          assert(_ccs(7 downto 6) === B"11")

          switch(io.func) {
            is(ALU8Func.ADD, ALU8Func.ADC) {
              carry_in := Mux(io.func === ALU8Func.ADD, U"0", ccs(Flags.C).asUInt)

              val h = sum5(4)
              val n = sum9(7)
              val c = sum9(8)
              val z = (sum9(7 downto 0) === 0)
              val v = (sum8(7) ^ c)

              /* We have to extend the bits we're interested in with a zero bit,
               * because in version 1.3.6 of SpinalHDL, the carry-out is not generated.
               * The development version does have addition with carry-out, but we're
               * sticking to the latest stable release. */
              val input1 = io.input1.resize(9).asUInt
              val input2 = io.input2.resize(9).asUInt

              sum9 := (input1 + input2 + carry_in)
              sum8 := (input1(6 downto 0).resize(8) + input2(6 downto 0).resize(8) + carry_in)
              sum5 := (input1(3 downto 0).resize(5) + input2(3 downto 0).resize(5) + carry_in)
              assert(io.output === sum9(7 downto 0).asBits)
              assert(_ccs(Flags.H) === h)
              assert(_ccs(Flags.N) === n)
              assert(_ccs(Flags.Z) === z)
              assert(_ccs(Flags.V) === v)
              assert(_ccs(Flags.C) === c)
              assert(_ccs(Flags.I) === ccs(Flags.I))
            }
            is(ALU8Func.SUB, ALU8Func.SBC) {
              when(io.func === ALU8Func.SUB) {
                carry_in := 0
              } otherwise {
                carry_in := ccs(Flags.C).asUInt
              }

              val n = sum9(7)
              val c = ~sum9(8)
              val z = (sum9(7 downto 0) === 0)
              val v = (sum8(7) ^ sum9(8))

              /* We have to extend the bits we're interested in with a zero bit,
               * because in version 1.3.6 of SpinalHDL, the carry-out is not generated.
               * The development version does have addition with carry-out, but we're
               * sticking to the latest stable release. */
              val input1 = io.input1.resize(9).asUInt
              val input2 = io.input2.resize(9).asUInt

              sum9 := (input1 + (~io.input2).resize(9).asUInt + ~carry_in)
              sum8 := (input1(6 downto 0).resize(8) + (~input2(6 downto 0)).resize(8) + ~carry_in)
              assert(sum9(7 downto 0).asBits === (io.input1.asUInt - io.input2.asUInt - carry_in).asBits)
              assert(io.output === sum9(7 downto 0).asBits)
              assert(_ccs(Flags.N) === n)
              assert(_ccs(Flags.Z) === z)
              assert(_ccs(Flags.V) === v)
              assert(_ccs(Flags.C) === c)
              assert(_ccs(Flags.H) === ccs(Flags.H))
              assert(_ccs(Flags.I) === ccs(Flags.I))
            }
          }
        }
      }

      alu.setDefinitionName("ALU8")
      alu
    }.printPruned()
  }
}