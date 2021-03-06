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

/* Generate Core (Verilog)
 * sby "runMain shdl6800.Core"
 *
 * Generate Core with formal proofs (SystemVerilog)
 * sby "runMain shdl6800.Core <name of instruction>"
 *
 * For example:
 * sby "runMain shdl6800.Core lda
 *
 * Run simulation (by generating Verilator code)
 * sby "runMain shdl6800.Core sim"
 */

package shdl6800

import shdl6800.Consts.{Flags, ModeBits}
import shdl6800.formal.{FormalData, Verification}
import spinal.core._

import scala.sys.process.Process

/* Values for specifying an 8-bit register for things
 * like sources and destinations. Can also specify the
 * (H)igh or (L)ow 8 bits of a 16-bit signal. */
object Reg8 extends SpinalEnum {
  val NONE, A, B, XH, XL, SPH, SPL, PCH, PCL, TMP8, TMP16H, TMP16L, DIN, DOUT = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding")(
    NONE   ->  0,
    A      ->  1,
    B      ->  2,
    XH     ->  3,
    XL     ->  4,
    SPH    ->  5,
    SPL    ->  6,
    PCH    ->  7,
    PCL    ->  8,
    TMP8   ->  9,
    TMP16H -> 10,
    TMP16L -> 11,
    DIN    -> 12,
    DOUT   -> 13
  )
}

/* Values for specifying a 16-bit register for thing
 * like sources and destinations. */
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

/* The core of the CPU. There is another layer which
 * handles I/O for the actual pins. */
case class Core(verification: Option[Verification] = None) extends Component {
  val io = new Bundle {
    val Addr = out Bits(16 bits)
    val Din  = in  Bits(8 bits)
    val Dout = out Bits(8 bits)
    val RW   = out Bits(1 bit)  // 1 = read, 0 = write
    val VMA  = out Bits(1 bit)  // 1 = address is valid
  }

  // Registers
  val RW    = Reg(Bits(1 bit))   init(1)
  val Addr  = Reg(Bits(16 bits)) init(0)
  val Dout  = Reg(Bits(8 bits))  init(0)
  val VMA   = Reg(Bits(1 bit))   init(0)

  val a     = Reg(Bits(8 bits))
  val b     = Reg(Bits(8 bits))
  val x     = Reg(Bits(16 bits))
  val sp    = Reg(Bits(16 bits))
  val pc    = Reg(Bits(16 bits))
  val instr = Reg(Bits(8 bits))
  val tmp8  = Reg(Bits(8 bits))
  val tmp16 = Reg(Bits(16 bits))

  // Buses
  val src8_1   = Bits(8 bits)   // Input 1 of the ALU
  val src8_2   = Bits(8 bits)   // Input 2 of the ALU
  val alu8     = Bits(8 bits)   // Output from the ALU
  val ccs      = Bits(8 bits)   // Flags from the ALU

  // Selectors for buses
  val src8_1_select  = Reg8()
  val src8_2_select  = Reg8()

  // Function control
  val alu8_func = ALU8Func()

  /* Mappings of selectors to signals. The second tuple element is
   * whether the register is read/write. */
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
                    , Reg8.DOUT   -> (this.Dout, true))
  val reg16_map = Map( Reg16.X     -> (this.x, true)
                     , Reg16.SP    -> (this.sp, true)
                     , Reg16.PC    -> (this.pc, true)
                     , Reg16.TMP16 -> (this.tmp16, true)
                     , Reg16.ADDR  -> (this.Addr, true))

  // Internal state
  val reset_state    = Reg(Bits(2 bits)) init(0) // Where we are during reset
  val cycle          = Reg(UInt(4 bits)) init(0) // Where we are during instr processing
  val mode           = Bits(2 bits)                      // Mode bits, decoded by ModeBits

  val end_instr_flag = Bits(1 bit)   // Performs end-of-instruction actions
  val end_instr_addr = Bits(16 bits) // where the next instruction is

  // Formal verification
  val formalData = FormalData(verification)

  val alu = new ALU8

  /* Default values. Some are necessary to avoid getting compiler errors
   * about signals not having drivers, or latches being inferred. */
  end_instr_flag := 0
  src8_1_select  := Reg8.NONE
  src8_2_select  := Reg8.NONE
  alu8_func      := ALU8Func.NONE
  VMA            := 1
  cycle          := cycle + 1
  end_instr_addr := 0
  sp             := 0

  // Some common instruction decoding
  mode := instr(5 downto 4)

  src_bus_setup(reg8_map, src8_1, src8_1_select)
  src_bus_setup(reg8_map, src8_2, src8_2_select)

  alu.io.input1 := src8_1
  alu.io.input2 := src8_2
  alu8          := alu.io.output
  alu.io.func   := alu8_func
  ccs           := alu.io.ccs

  reset_handler()
  when(reset_state === 3) {
    when(cycle === 0) {
      fetch()
    } otherwise {
      execute()
    }
  }
  maybe_do_formal_verification()
  end_instr_flag_handler()

  def src_bus_setup[T <: SpinalEnum](reg_map: Map[SpinalEnumElement[T], (Bits, Boolean)], bus: Bits, selector: SpinalEnumCraft[T]): Unit = {
    switch(selector) {
      reg_map.foreach {case (e, reg) => is(e) { bus := reg._1 }}
      default {
        bus := 0
      }
    }
  }

  def dst_bus_setup[T <: SpinalEnum](reg_map: Map[SpinalEnumElement[T], (Bits, Boolean)], bus: Bits, bitmap: Bits): Unit = {
    reg_map.foreach {case (e, reg) => if(reg._2) { when(bitmap(e.position - 1)) {reg._1 := bus} }}
  }

  /* Generates logic for reading the reset vector at 0xFFFE
   * and jumping there. */
  def reset_handler(): Unit = {
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

  // Generates logic for handling the end of an instruction.
  def end_instr_flag_handler(): Unit = {
    when(end_instr_flag === 1) {
      pc    := end_instr_addr
      Addr  := end_instr_addr
      RW    := 1
      cycle := 0
    }
  }

  /* Fetch the opcode at PC, which should already be on the address line.
   * The opcode is on the data lines by the end of the cycle.
   * We always increment PC and Addr and go to instruction cycle 1. */
  def fetch(): Unit = {
    instr := io.Din
    RW    := 1

    val new_pc = (pc.asSInt + 1).asBits
    pc   := new_pc
    Addr := new_pc
  }

  /* If formal verification is enabled, take pre- and post-snapshots, and do asserts.
   *
   * A pre-snapshot is taken of the registers when io.Din is the instruction we're
   * looking for, and we're on cycle 0. We use io.Din because io.Din -> instr only at the
   * *end* of cycle 0.
   *
   * A post-snapshot is taken of the registers during cycle 0 of the *next* instruction.
   * It's not really a "snapshot", in that the CPU state aren't stored. All verification
   * takes place using combinatorial statements.
   */
  def maybe_do_formal_verification(): Unit = {
    if(verification.isDefined) {
      val v = verification.get

      when((cycle === 0) && (reset_state === 3)) {
        when(v.valid(io.Din)) {
          formalData.preSnapshot(io.Din, ccs, a, b, x, sp, pc)
        } otherwise {
          formalData.noSnapshot
        }

        when(formalData.snapshot_taken) {
          formalData.postSnapshot(ccs, a, b, x, sp, pc)
          v.check(instr, formalData)
        }
      }
    }
  }

  // Execute the instruction in the instr register.
  def execute(): Unit = {
    switch(instr) {
      is(B"0000_0001") { NOP() }                                           // NOP
      is(B"0000_0110") { TAP() }                                           // TAP
      is(B"0000_0111") { TPA() }                                           // TPA
      is(M"0000_100-") { IN_DE_X() }                                       // INX/DEX
      is(M"0000_101-") { CL_SE_V() }                                       // CLV, SEV
      is(M"0000_110-") { CL_SE_C() }                                       // CLC, SEC
      is(M"0000_111-") { CL_SE_I() }                                       // CLI, SEI
      is(M"0010_----") { BR() }                                            // Branch instructions
      is(M"01--_0000") { ALU2(ALU8Func.SUB, False, True) }                 // NEG
      is(M"01--_0011") { ALU2(ALU8Func.COM, False, True) }                 // COM
      is(M"01--_0100") { ALU2(ALU8Func.LSR, False, True) }                 // LSR
      is(M"01--_0110") { ALU2(ALU8Func.ROR, False, True) }                 // ROR
      is(M"01--_0111") { ALU2(ALU8Func.ASR, False, True) }                 // ASR
      is(M"01--_1000") { ALU2(ALU8Func.ASL, False, True) }                 // ASL
      is(M"01--_1001") { ALU2(ALU8Func.ROL, False, True) }                 // ROL
      is(M"01--_1010") { ALU2(ALU8Func.DEC, False, True) }                 // DEC
      is(M"01--_1100") { ALU2(ALU8Func.INC, False, True) }                 // INC
      is(M"01--_1101") { ALU2(ALU8Func.SUB, True, False, store = false) }  // TST
      is(M"011-_1110") { JMP() }                                           // JMP
      is(M"01--_1111") { ALU2(ALU8Func.SUB, True, True) }                  // CLR
      is(M"1---_0110") { ALU(ALU8Func.LD) }                                // LDA
      is(M"1---_0000") { ALU(ALU8Func.SUB) }                               // SUB
      is(M"1---_0001") { ALU(ALU8Func.SUB, store = false) }                // CMP
      is(M"1---_0010") { ALU(ALU8Func.SBC) }                               // SBC
      is(M"1---_0100") { ALU(ALU8Func.AND) }                               // AND
      is(M"1---_0101") { ALU(ALU8Func.AND, store = false) }                // BIT
      is(M"1--1_0111",
         M"1-10_0111") { STA() }                                           // STA
      is(M"1---_1000") { ALU(ALU8Func.EOR) }                               // EOR
      is(M"1---_1001") { ALU(ALU8Func.ADC) }                               // ADC
      is(M"1---_1010") { ALU(ALU8Func.ORA) }                               // ORA
      is(M"1---_1011") { ALU(ALU8Func.ADD) }                               // ADD
      default  { end_instr(pc) }                                           // Illegal
    }
  }

  /* Reads a byte starting from the given cycle.
   *
   * The byte read is combinatorially placed in comb_dest.
   */
  def read_byte(cycle: Int, addr: Bits, comb_dest: Option[Bits]): Unit = {
    when(this.cycle === cycle) {
      Addr := addr
      RW   := 1
    }

    when(this.cycle === (cycle + 1)) {
      if(comb_dest.isDefined) {
        comb_dest.get := io.Din
      }

      if(verification.isDefined) {
        formalData.read(Addr, io.Din)
      }
    }
  }

  def ALU(func: SpinalEnumElement[ALU8Func.type], store: Boolean = true): Unit = {
    val b_bit = instr(6)

    when(mode === ModeBits.DIRECT) {
      val operand = mode_direct()
      read_byte(cycle = 1, addr = operand, comb_dest = Some(src8_2))

      when(cycle === 2) {
        src8_1    := Mux(b_bit, b, a)
        alu8_func := func

        if (store) {
          when(b_bit) {
            b := alu8
          } otherwise {
            a := alu8
          }
        }
        end_instr(pc)
      }
    }.elsewhen(mode === ModeBits.EXTENDED) {
      val operand = mode_ext()
      read_byte(cycle = 2, addr = operand, comb_dest = Some(src8_2))

      when(cycle === 3) {
        src8_1    := Mux(b_bit, b, a)
        alu8_func := func

        if(store) {
          when(b_bit) {
            b := alu8
          } otherwise {
            a := alu8
          }
        }

        end_instr(pc)
      }
    }.elsewhen(mode === ModeBits.IMMEDIATE) {
      val operand = mode_immediate8()

      when(cycle === 2) {
        src8_1    := Mux(b_bit, b, a)
        src8_2    := operand
        alu8_func := func

        if(store) {
          when(b_bit) {
            b := alu8
          } otherwise {
            a := alu8
          }
        }

        end_instr(pc)
      }
    }.elsewhen(mode === ModeBits.INDEXED) {
      val operand = mode_indexed()
      read_byte(cycle = 3, addr = operand, comb_dest = Some(src8_2))

      when(cycle === 4) {
        src8_1    := Mux(b_bit, b, a)
        alu8_func := func

        if(store) {
          when(b_bit) {
            b := alu8
          } otherwise {
            a := alu8
          }
        }

        end_instr(pc)
      }
    }
  }

  def ALU2(func: SpinalEnumElement[ALU8Func.type], operand1: Bool, operand2: Bool, store: Boolean = true): Unit = {
    when(mode === ModeBits.A) {
      src8_1    := Mux(operand1, a, B(0))
      src8_2    := Mux(operand2, a, B(0))
      alu8_func := func

      if(store) {
        a := alu8
      }

      end_instr(pc)
    }.elsewhen(mode === ModeBits.B) {
      src8_1    := Mux(operand1, b, B(0))
      src8_2    := Mux(operand2, b, B(0))
      alu8_func := func

      if(store) {
        b := alu8
      }

      end_instr(pc)
    }.elsewhen(mode === ModeBits.EXTENDED) {
      val operand = mode_ext()
      read_byte(cycle = 2, addr = operand, comb_dest = None)

      when(cycle === 3) {
        src8_1    := Mux(operand1, io.Din, B(0))
        src8_2    := Mux(operand2, io.Din, B(0))
        alu8_func := func
        // Output during cycle 4:
        tmp8      := alu8
        VMA       := 0
        Addr      := operand
        RW        := 1
      }

      when(cycle === 4) {
        Addr := operand
        Dout := tmp8
        RW   := 0

        if(!store) {
          VMA := 0
        }
      }

      when(cycle === 5) {
        if(store) {
          if(verification.isDefined) {
            formalData.write(Addr, Dout)
          }
        }
        end_instr(pc)
      }
    }.elsewhen(mode === ModeBits.INDEXED) {
      val operand = mode_indexed()
      read_byte(cycle = 3, addr = operand, comb_dest = None)

      when(cycle === 4) {
        src8_1    := Mux(operand1, io.Din, B(0))
        src8_2    := Mux(operand2, io.Din, B(0))
        alu8_func := func
        // Output during cycle 4:
        tmp8      := alu8
        VMA       := 0
        Addr      := operand
        RW        := 1
      }

      when(cycle === 5) {
        Addr := operand
        Dout := tmp8
        RW   := 0

        if(!store) {
          VMA := 0
        }
      }

      when(cycle === 6) {
        if(store) {
          if(verification.isDefined) {
            formalData.write(Addr, Dout)
          }
        }
        end_instr(pc)
      }
    }
  }

  def BR(): Unit = {
    val operand = mode_immediate8()

    val relative = operand

    /* At this point, pc is the instruction start + 2, so we just
       add the signed relative offset to get the target. */
    when(cycle === 2) {
      tmp16 := (pc.asSInt + (relative.asSInt.resize(16))).asBits
    }

    when(cycle === 3) {
      val take_branch = branch_check()
      end_instr(Mux(take_branch, tmp16, pc))
    }
  }

  // Clears of sets Carry.
  def CL_SE_C(): Unit = {
    when(cycle === 1) {
      alu8_func := Mux(instr(0), ALU8Func.SEC, ALU8Func.CLC)
      end_instr(pc)
    }
  }

  // Clears of sets Overflow.
  def CL_SE_V(): Unit = {
    when(cycle === 1) {
      alu8_func := Mux(instr(0), ALU8Func.SEV, ALU8Func.CLV)
      end_instr(pc)
    }
  }

  // Clears of sets Interrupt.
  def CL_SE_I(): Unit = {
    when(cycle === 1) {
      alu8_func := Mux(instr(0), ALU8Func.SEI, ALU8Func.CLI)
      end_instr(pc)
    }
  }

  // Increments or decrements X.
  def IN_DE_X(): Unit = {
    val dec = instr(0)

    when(cycle === 1) {
      VMA  := 0
      Addr := x
      x    := Mux(dec, x.asSInt - 1, x.asSInt + 1).asBits
    }

    when(cycle === 2) {
      VMA  := 0
      Addr := x
    }

    when(cycle === 3) {
      alu8_func := Mux(x === 0, ALU8Func.SEZ, ALU8Func.CLZ)
      end_instr(pc)
    }
  }

  def JMP(): Unit = {
    when(mode === ModeBits.EXTENDED) {
      val operand = mode_ext()

      when(cycle === 2) {
        end_instr(operand)
      }
    }.elsewhen(mode === ModeBits.INDEXED) {
      val operand = mode_indexed()

      when(cycle === 3) {
        end_instr(operand)
      }
    }
  }

  def NOP(): Unit = {
    end_instr(pc)
  }

  def STA(): Unit = {
    val b_bit = instr(6)

    when(mode === ModeBits.DIRECT) {
      val operand = mode_direct()

      when(cycle === 1) {
        VMA  := 0
        Addr := operand
        RW   := 1
      }

      when(cycle === 2) {
        Addr := operand
        Dout := Mux(b_bit, b, a)
        RW   := 0
      }

      when(cycle === 3) {
        if (verification.isDefined) {
          formalData.write(Addr, Dout)
        }

        src8_2    := Mux(b_bit, b, a)
        alu8_func := ALU8Func.LD
        end_instr(pc)
      }
    }.elsewhen(mode === ModeBits.EXTENDED) {
      val operand = mode_ext()

      when(cycle === 2) {
        VMA  := 0
        Addr := operand
        RW   := 1
      }

      when(cycle === 3) {
        Addr := operand
        Dout := Mux(b_bit, b, a)
        RW   := 0
      }

      when(cycle === 4) {
        if (verification.isDefined) {
          formalData.write(Addr, Dout)
        }

        src8_2    := Mux(b_bit, b, a)
        alu8_func := ALU8Func.LD
        end_instr(pc)
      }
    }.elsewhen(mode === ModeBits.INDEXED) {
      val operand = mode_indexed()

      when(cycle === 3) {
        VMA  := 0
        Addr := operand
        RW   := 1
      }

      when(cycle === 4) {
        Addr := operand
        Dout := Mux(b_bit, b, a)
        RW   := 0
      }

      when(cycle === 5) {
        if (verification.isDefined) {
          formalData.write(Addr, Dout)
        }

        src8_2    := Mux(b_bit, b, a)
        alu8_func := ALU8Func.LD
        end_instr(pc)
      }
    }
  }

  // Transfer A to CCS.
  def TAP(): Unit = {
    when(cycle === 1) {
      alu8_func := ALU8Func.TAP
      src8_1    := a
      end_instr(pc)
    }
  }

  // Transfer CCS to A.
  def TPA(): Unit = {
    when(cycle === 1) {
      alu8_func := ALU8Func.TPA
      a         := alu8
      end_instr(pc)
    }
  }

  /* Generates logic for a 1-bit value for branching.
   *
   * Returns a Bool which is set if the branch should be
   * take. The branch logic is determined by the instruction.
   */
  def branch_check(): Bool = {
    val invert      = instr(0)
    val cond        = Bool

    switch(instr(3 downto 1)) {
      is(B"000") { // BRA, BRN
        cond := True
      }
      is(B"001") { // BHI, BLS
        cond := !(ccs(Flags.C) | ccs(Flags.Z))
      }
      is(B"010") { // BCC, BCS
        cond := !ccs(Flags.C)
      }
      is(B"011") { // BNE, BEQ
        cond := !ccs(Flags.Z)
      }
      is(B"100") { // BVC, BVS
        cond := !ccs(Flags.V)
      }
      is(B"101") { // BPL, BMI
        cond := !ccs(Flags.N)
      }
      is(B"110") { // BGE, BLT
        cond := !(ccs(Flags.N) ^ ccs(Flags.V))
      }
      is(B"111") { // BGT, BLE
        cond := !(ccs(Flags.Z) | (ccs(Flags.N) ^ ccs(Flags.V)))
      }
    }

    val take_branch = (cond ^ invert)
    take_branch
  }

  /* Generates logic to get the 8-bit operand for immediate mode instructions.
   *
   * Returns the Bits containing an 8-bit operand.
   * After cycle 1, tmp8 contains the operand.
   */
  def mode_immediate8(): Bits = {
    val operand = Mux(cycle === 1, io.Din, tmp8)

    when(cycle === 1) {
      tmp8 := io.Din
      val new_pc = (pc.asSInt + 1).asBits
      pc   := new_pc
      Addr := new_pc
      RW   := 1

      if (verification.isDefined) {
        formalData.read(Addr, io.Din)
      }
    }

    operand
  }

  /* Generates logic to get the 8-bit zero-page address for direct mode instructions.
   *
   * Returns the Bits containing a 16-bit address where the upper byte is zero.
   * After cycle 1, tmp16 contains the address.
   */
  def mode_direct(): Bits = {
    val operand = Mux(cycle === 1, io.Din.resize(16), tmp16)

    when(cycle === 1) {
      tmp16(15 downto 8) := 0
      tmp16( 7 downto 0) := io.Din
      val new_pc = (pc.asSInt + 1).asBits
      pc   := new_pc
      Addr := new_pc
      RW   := 1

      if (verification.isDefined) {
        formalData.read(Addr, io.Din)
      }
    }

    operand
  }

  /* Generate logic to get the 16-bits address for indexed mode instructions.
   *
   * Returns the Bits containing a 16-bit address.
   * After cycle 2, tmp16 contains the address. The address is not valid until after
   * cycle 2.
   */
  def mode_indexed(): Bits = {
    val operand = tmp16

    when(cycle === 1) {
      tmp16(15 downto 8) := 0
      tmp16( 7 downto 0) := io.Din
      val new_pc         = (pc.asSInt + 1).asBits
      pc                 := new_pc
      Addr               := new_pc
      RW                 := 1

      if (verification.isDefined) {
        formalData.read(Addr, io.Din)
      }
    }

    when(cycle === 2) {
      tmp16 := (tmp16.asSInt + x.asSInt).asBits
    }

    operand
  }

  /* Generates logic to get the 16-bit operand for extended mode instructions.
   *
   * Returns the Bits containing the 16-bit operand. After cycle 2, tmp16
   * contains the operand.
   */
  def mode_ext(): Bits = {
    val operand = Mux(cycle === 2,
      Cat(tmp16(15 downto 8), io.Din), tmp16)

    when(cycle === 1) {
      tmp16(15 downto 8) := io.Din
      val new_addr       = (pc.asSInt + 1).asBits
      pc                 := new_addr
      Addr               := new_addr
      RW                 := 1

      if(verification.isDefined) {
        formalData.read(Addr, io.Din)
      }
    }

    when(cycle === 2) {
      tmp16(7 downto 0) := io.Din
      pc                := (pc.asSInt + 1).asBits

      if(verification.isDefined) {
        formalData.read(Addr, io.Din)
      }
    }

    operand
  }

  /* Ends the instruction.
   *
   * Loads the PC and Addr register with the given addr, sets R/W mode
   * to read, and sets the cycle to 0 at the end of the current cycle.
   */
  def end_instr(addr: Bits): Unit = {
    end_instr_addr := addr
    end_instr_flag := 1
  }

  // IO wiring
  io.RW   := RW
  io.Addr := Addr
  io.Dout := Dout
  io.VMA  := VMA
}

object Core {
  def main(args: Array[String]): Unit = {
    if(args.length > 0) {
      if (args(0) == "sim") {
        import spinal.core.sim._

        val spinalConfig = SpinalConfig(defaultClockDomainFrequency = FixedFrequency(1 MHz))

        SimConfig
          .withConfig(spinalConfig)
          .withWave
          .allOptimisation
          .compile(new Core)
          .doSim { dut =>
            // Reset generation
            fork {
              dut.clockDomain.assertReset()
              sleep(10)
              dut.clockDomain.deassertReset()
            }

            // Clock generation
            fork {
              dut.clockDomain.fallingEdge()
              sleep(5)
              while (true) {
                dut.clockDomain.clockToggle()
                sleep(5)
              }
            }

            val mem = Map(
              0xFFFE -> 0x12, // Reset vector high
              0xFFFF -> 0x34, // Reset vector low
              0x1234 -> 0x20, // BRA 0x1234
              0x1235 -> 0xFE,
              0x1236 -> 0x01, // NOP
              0xA010 -> 0x01) // NOP

            for (i <- 0 to 16) {
              dut.clockDomain.waitFallingEdge()
              if (mem.contains(dut.io.Addr.toInt)) {
                dut.io.Din #= mem(dut.io.Addr.toInt)
              } else {
                dut.io.Din #= 0xFF
              }
            }
          }
      } else {
        import spinal.core.Formal._
        import shdl6800.formal._

        // Create the output directory if it doesn't already exits
        Process("mkdir -p src/main/scala/shdl6800/formal/sby/") !

        val config = SpinalConfig(
          defaultConfigForClockDomains = ClockDomainConfig(
            resetKind        = SYNC,
            resetActiveLevel = HIGH),
          targetDirectory = "src/main/scala/shdl6800/formal/sby/"
        )
        config.includeFormal.generateSystemVerilog {
          val verification: Option[Verification] = args(0) match {
            case "jmp"       | "JMP"       => Some(new Formal_JMP)
            case "lda"       | "LDA"       => Some(new Formal_LDA)
            case "add"       | "ADD"       => Some(new Formal_ADD)
            case "sub"       | "SUB"       => Some(new Formal_SUB)
            case "and"       | "AND"       => Some(new Formal_AND)
            case "bit"       | "BIT"       => Some(new Formal_BIT)
            case "cmp"       | "CMP"       => Some(new Formal_CMP)
            case "eor"       | "EOR"       => Some(new Formal_EOR)
            case "ora"       | "ORA"       => Some(new Formal_ORA)
            case "sta"       | "STA"       => Some(new Formal_STA)
            case "br"        | "BR"        => Some(new Formal_BR)
            case "flag"      | "FLAG"      => Some(new Formal_FLAG)
            case "tap"       | "TAP"       => Some(new Formal_TAP)
            case "tpa"       | "TPA"       => Some(new Formal_TPA)
            case "inc_dec_x" | "INC_DEC_X" => Some(new Formal_INC_DEC_X)
            case "clr"       | "CLR"       => Some(new Formal_CLR)
            case "com"       | "COM"       => Some(new Formal_COM)
            case "inc_dec"   | "INC_DEC"   => Some(new Formal_INC_DEC)
            case "neg"       | "NEG"       => Some(new Formal_NEG)
            case "sh_rot"    | "SH_ROT"    => Some(new Formal_SH_ROT)
            case "tst"       | "TST"       => Some(new Formal_TST)
            case _                         => None
          }
          val core: Core = new Core(verification) {
            if (verification.isDefined) {
              // Cycle counter
              val cycle2 = Reg(UInt(6 bits))
              cycle2 := (cycle2 + 1)

              // Force a reset
              when(initstate()) {
                assume(clockDomain.isResetActive)

                /* This is needed because the model checker can start
                 * with the state where a snapshot is already taken.*/
                assume(~formalData.snapshot_taken)
              } otherwise {
                when(cycle2 === 20) {
                  cover(formalData.snapshot_taken & end_instr_flag.asBool)
                  assume(formalData.snapshot_taken & end_instr_flag.asBool)
                }

                // Verify that reset does what it's supposed to
                when(past(clockDomain.isResetActive, 4) && ~past(clockDomain.isResetActive, 3) &&
                  ~past(clockDomain.isResetActive, 2) && ~past(clockDomain.isResetActive, 1)) {
                  assert(past(Addr, 2) === 0xFFFE)
                  assert(past(Addr, 1) === 0xFFFF)
                  assert(Addr(15 downto 8) === past(io.Din, 2))
                  assert(Addr(7 downto 0) === past(io.Din, 1))
                  assert(Addr === pc)
                }
              }
            }
          }

          core.setDefinitionName("Formal_" + args(0).toUpperCase())
          core
        }.printPruned()
      }
    } else {
      SpinalVerilog(new Core).printPruned()
    }
  }
}
