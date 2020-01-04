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

object ALU8Func extends SpinalEnum {
  val NONE, LD, ADD, ADC, SUB, SBC = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding") (
    NONE -> 0,
    LD   -> 1,
    ADD  -> 2,
    ADC  -> 3,
    SUB  -> 4,
    SBC  -> 5
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
  carry4   := 0
  carry7   := 0
  carry8   := 0
  overflow := 0

  switch(io.func) {
    is(ALU8Func.LD) {
      io.output := io.input1
      _ccs(Flags._Z) := (io.input1 === 0)
      _ccs(Flags._N) := (io.input1(7))
      _ccs(Flags._V) := False
    }
    is(ALU8Func.ADD, ALU8Func.ADC) {
      /* Version 1.3.6 of SpinalHDL does not store the carry-out,
       * so we have to extend by 1 bit by hand, or use the development
       * version. Here we extend by hand. */
      val in1_ext0_4 = io.input1(3 downto 0).resize(5).asUInt
      val in2_ext0_4 = io.input2(3 downto 0).resize(5).asUInt
      val in1_ext4_7 = io.input1(6 downto 4).resize(4).asUInt
      val in2_ext4_7 = io.input2(6 downto 4).resize(4).asUInt
      val in1_ext7_8 = io.input1(7).asBits.resize(2).asUInt
      val in2_ext7_8 = io.input2(7).asBits.resize(2).asUInt

      val carry_in = Mux(io.func === ALU8Func.ADD, U"0", ccs(Flags._C).asUInt)

      val sum0_3 = in1_ext0_4 + in2_ext0_4 + carry_in
      carry4 := sum0_3.msb.asUInt

      val sum4_6 = in1_ext4_7 + in2_ext4_7 + carry4
      carry7 := sum4_6.msb.asUInt

      val sum7 = in1_ext7_8 + in2_ext7_8 + carry7
      carry8 := sum7.msb.asUInt

      io.output(3 downto 0) := sum0_3(3 downto 0).asBits
      io.output(6 downto 4) := sum4_6(2 downto 0).asBits
      io.output(7)          := sum7(0)

      overflow := carry7 ^ carry8
      _ccs(Flags._H) := carry4.asBool
      _ccs(Flags._N) := io.output(7)
      _ccs(Flags._Z) := io.output === 0
      _ccs(Flags._V) := overflow.asBool
      _ccs(Flags._C) := carry8.asBool
    }
    is(ALU8Func.SUB, ALU8Func.SBC) {
      /* Version 1.3.6 of SpinalHDL does not store the carry-out,
       * so we have to extend by 1 bit by hand, or use the development
       * version. Here we extend by hand. */
      val in1_ext0_7 = io.input1(6 downto 0).resize(8).asUInt
      val in1_ext7_8 = io.input1(7).asBits.resize(2).asUInt

      val carry_in = Mux(io.func === ALU8Func.SUB, U"0", ccs(Flags._C).asUInt)

      val sum0_6 = in1_ext0_7 + (~io.input2(6 downto 0)).resize(8).asUInt + ~carry_in
      carry7 := sum0_6.msb.asUInt

      val sum7 = in1_ext7_8 + (~io.input2(7)).asBits.resize(2).asUInt + carry7
      carry8 := sum7.msb.asUInt

      io.output(6 downto 0) := sum0_6(6 downto 0).asBits
      io.output(7)          := sum7(0)

      overflow := carry7 ^ carry8
      _ccs(Flags._N) := io.output(7)
      _ccs(Flags._Z) := io.output === 0
      _ccs(Flags._V) := overflow.asBool
      _ccs(Flags._C) := ~carry8.asBool
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
              // sumN = input1(N-1 downto 0) + input2(N-1 downto 0) (so sumN(N-1) is the carry bit)
              when(io.func === ALU8Func.ADD) {
                carry_in := 0
              } otherwise {
                carry_in := ccs(Flags._C).asUInt
              }

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
              assert(_ccs(Flags._H) === h)
              assert(_ccs(Flags._N) === n)
              assert(_ccs(Flags._Z) === z)
              assert(_ccs(Flags._V) === v)
              assert(_ccs(Flags._C) === c)
              assert(_ccs(Flags._I) === ccs(Flags._I))
            }
            is(ALU8Func.SUB, ALU8Func.SBC) {
              when(io.func === ALU8Func.SUB) {
                carry_in := 0
              } otherwise {
                carry_in := ccs(Flags._C).asUInt
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
              assert(_ccs(Flags._N) === n)
              assert(_ccs(Flags._Z) === z)
              assert(_ccs(Flags._V) === v)
              assert(_ccs(Flags._C) === c)
              assert(_ccs(Flags._H) === ccs(Flags._H))
              assert(_ccs(Flags._I) === ccs(Flags._I))
            }
          }
        }
      }

      alu.setDefinitionName("ALU8")
      alu
    }.printPruned()
  }
}