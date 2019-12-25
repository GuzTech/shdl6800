// shdl6800: A 6800 processor written in SpinalHDL
//
// Copyright (C) 2019 Oguz Meteer <info@guztech.nl>
//
// Permission to use, copy, modify, and/or distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
// ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
// ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
// OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

package shdl6800

import spinal.core.{SpinalEnumCraft, _}

class Core extends Component {
  val io = new Bundle {
    val Addr = out Bits(16 bits)
    val Din  = in  Bits(8 bits)
    val Dout = out Bits(8 bits)
    val RW   = out Bits(1 bit)
  }

  // Registers
  val RW    = Reg(Bits(1 bit)) init(1)
  val Addr  = Reg(Bits(16 bits)) init(0)

  val a     = Reg(Bits(8 bits))
  val b     = Reg(Bits(8 bits))
  val x     = Reg(Bits(16 bits))
  val sp    = Reg(Bits(16 bits))
  val pc    = Reg(Bits(16 bits))
  val instr = Reg(Bits(8 bits))
  val tmp8  = Reg(Bits(8 bits))
  val tmp16 = Reg(Bits(16 bits))

  // Buses
  val src8_1   = Bits(8 bits)
  val src8_2   = Bits(8 bits)
  val alu8     = Bits(8 bits)
  val src16    = Bits(16 bits)
  val incdec16 = Bits(16 bits)

  // Selectors for buses
  object Reg8 extends SpinalEnum {
    val NONE, A, B, XH, XL, SPH, SPL, PCH, PCL, TMP8, TMP16H, TMP16L, DIN, DOUT = newElement()
    defaultEncoding = SpinalEnumEncoding("staticEncoding")(
      NONE   -> 0,
      A      -> 1,
      B      -> 2,
      XH     -> 3,
      XL     -> 4,
      SPH    -> 5,
      SPL    -> 6,
      PCH    -> 7,
      PCL    -> 8,
      TMP8   -> 9,
      TMP16H -> 10,
      TMP16L -> 11,
      DIN    -> 12,
      DOUT   -> 13
    )
  }

  object Reg16 extends SpinalEnum {
    val NONE, X, SP, PC, TMP16, ADDR = newElement()
    defaultEncoding = SpinalEnumEncoding("staticEncoding") (
      NONE  -> 0,
      X     -> 1,
      SP    -> 2,
      PC    -> 3,
      TMP16 -> 4,
      ADDR  -> 5
    )
  }

  val src8_1_select  = Reg8()
  val src8_2_select  = Reg8()
  val alu8_write     = Bits(Reg8.elements.length bits)
  val src16_select   = Reg16()
  val src16_write    = Bits(Reg16.elements.length bits)
  val incdec16_write = Bits(Reg16.elements.length bits)

  val reg8_map = Map( Reg8.A      -> (this.a, true)
                    , Reg8.B      -> (this.b, true)
                    , Reg8.XH     -> (this.x(15 downto 8), true)
                    , Reg8.XL     -> (this.x( 7 downto 0), true)
                    , Reg8.SPH    -> (this.sp(15 downto 8), true)
                    , Reg8.SPL    -> (this.sp( 7 downto 0), true)
                    , Reg8.PCH    -> (this.pc(15 downto 8), true)
                    , Reg8.PCL    -> (this.pc( 7 downto 0), true)
                    , Reg8.TMP8   -> (this.tmp8, true)
                    , Reg8.TMP16H -> (this.tmp16(15 downto 8), true)
                    , Reg8.TMP16L -> (this.tmp16( 7 downto 0), true)
                    , Reg8.DIN    -> (this.io.Din, false)
                    , Reg8.DOUT   -> (this.io.Dout, true))

  val reg16_map = Map( Reg16.X     -> (this.x, true)
                     , Reg16.SP    -> (this.sp, true)
                     , Reg16.PC    -> (this.pc, true)
                     , Reg16.TMP16 -> (this.tmp16, true)
                     , Reg16.ADDR  -> (this.Addr, true))

  def src_bus_setup[T <: SpinalEnum](reg_map: Map[SpinalEnumElement[T], (Bits, Boolean)], bus: Bits, selector: SpinalEnumCraft[T]): Unit = {
    switch(selector) {
      reg_map.foreach {case (e, reg) => is(e) { bus := reg._1 }}
      default {
        bus := 0
      }
    }
  }

  def dst_bus_setup[T <: SpinalEnum](reg_map: Map[SpinalEnumElement[T], (Bits, Boolean)], bus: Bits, bitmap: Bits): Unit = {
    reg_map.foreach {case (e, reg) => if(reg._2) { if(bitmap(e.position) == True) {reg._1 := bus} }}
}

  src_bus_setup(reg8_map, src8_1, src8_1_select)
  src_bus_setup(reg8_map, src8_2, src8_2_select)
  dst_bus_setup(reg8_map, alu8, alu8_write)
  src_bus_setup(reg16_map, src16, src16_select)
  dst_bus_setup(reg16_map, src16, src16_write)
  dst_bus_setup(reg16_map, incdec16, incdec16_write)

  // Internal state
  val reset_state    = Reg(Bits(2 bits)) init(0)
  val cycle          = Reg(Bits(4 bits)) init(0)
  val end_instr_addr = Bits(16 bits)
  val end_instr_flag = Bits(1 bit)

  end_instr_addr := 0
  end_instr_flag := 0
  tmp16(7 downto 0) := 0

  reset_handler
  end_instr_flag_handler

  when(reset_state === 3) {
    when(cycle === 0) {
      fetch()
    } otherwise {
      execute()
    }
  }

  def reset_handler: Unit = {
    switch(reset_state) {
      is(0) {
        Addr        := B"16'hFFFE"
        RW          := 1
        reset_state := 1
      }
      is(1) {
        Addr        := B"16'hFFFF"
        RW          := 1
        tmp8        := io.Din
        reset_state := 2
      }
      is(2) {
        val reset_vector = Bits(16 bits)
        reset_vector := Cat(tmp8, io.Din)
        end_instr(reset_vector)
        reset_state := 3
      }
      default {
        reset_state := 3
      }
    }
  }

  def end_instr_flag_handler: Unit = {
    when(end_instr_flag === 1) {
      pc    := end_instr_addr
      Addr  := end_instr_addr
      RW    := 1
      cycle := 0
    }
  }

  def end_instr(addr: Bits): Unit = {
    end_instr_addr := addr
    end_instr_flag := 1
  }

  def fetch(): Unit = {
    instr := io.Din
    cycle := 1
    RW    := 1
    pc    := (pc.asUInt + 1).asBits
    Addr  := (pc.asUInt + 1).asBits
  }

  def execute(): Unit = {
    switch(instr) {
      is(B"00000001") {
        NOP()
      }
      is(B"01111110") {
        JMPext()
      }
      default {
        end_instr(pc)
      }
    }
  }

  def NOP(): Unit = {
    end_instr(pc)
  }

  def JMPext(): Unit = {
    when(cycle === 1) {
      tmp16(15 downto 8) := io.Din
      pc                 := (pc.asUInt + 1).asBits
      Addr               := (pc.asUInt + 1).asBits
      RW                 := 1
      cycle              := 2
    }
    when(cycle === 2) {
      val new_pc = Bits(16 bits)
      new_pc := Cat(tmp16(15 downto 8), io.Din)
      end_instr(new_pc)
    }
  }

  // IO wiring
  io.RW   := RW
  io.Addr := Addr
  io.Dout := 0
}

object CoreVerilog {
  def main(args: Array[String]): Unit = {
    SpinalVerilog(new Core).printPruned()
  }
}