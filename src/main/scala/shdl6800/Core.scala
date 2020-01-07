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

import shdl6800.formal.{FormalData, Verification}
import spinal.core._

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

  val end_instr_flag = Bits(1 bit)   // Performs end-of-instruction actions
  val end_instr_addr = Bits(16 bits) // where the next instruction is

  val alu = new ALU8

  // Formal verification
  val formalData = FormalData(verification)

  /* Default values. Some are necessary to avoid getting compiler errors
   * about signals not having drivers, or latches being inferred. */
  end_instr_flag := 0
  src8_1_select  := Reg8.NONE
  src8_2_select  := Reg8.NONE
  alu8_func      := ALU8Func.NONE
  end_instr_addr := 0
  x              := 0
  sp             := 0

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
    cycle := 1
    RW    := 1

    val new_pc = (pc.asSInt + 1).asBits
    pc     := new_pc
    Addr   := new_pc
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
      is(B"0000_0001") { NOP() }                                // NOP
      is(B"0111_1110") { JMPext() }                             // JMP ext
      is(M"1-11_0110") { ALUext(ALU8Func.LD) }                  // LDA ext
      is(M"1-11_0000") { ALUext(ALU8Func.SUB) }                 // SUB ext
      is(M"1-11_0001") { ALUext(ALU8Func.SUB, store = false) }  // CMP ext
      is(M"1-11_0010") { ALUext(ALU8Func.SBC) }                 // SBC ext
      is(M"1-11_0100") { ALUext(ALU8Func.AND) }                 // AND ext
      is(M"1-11_0101") { ALUext(ALU8Func.AND, store = false) }  // BIT ext
      is(M"1-11_0111") { STAext() }                             // STA ext
      is(M"1-11_1000") { ALUext(ALU8Func.EOR) }                 // EOR ext
      is(M"1-11_1001") { ALUext(ALU8Func.ADC) }                 // ADC ext
      is(M"1-11_1010") { ALUext(ALU8Func.ORA) }                 // ORA ext
      is(M"1-11_1011") { ALUext(ALU8Func.ADD) }                 // ADD ext
      default  { end_instr(pc) }                                // Illegal
    }
  }

  /* Reads a byte starting from the given cycle.
   *
   * The byte read is combinatorially placed in comb_dest.
   */
  def read_byte(cycle: UInt, addr: Bits, comb_dest: Bits): Unit = {
    when(this.cycle === cycle) {
      Addr := addr
      RW   := 1
    }

    when(this.cycle === (cycle + 1)) {
      comb_dest := io.Din

      if(verification.isDefined) {
        formalData.read(Addr, io.Din)
      }
    }
  }

  def ALUext(func: SpinalEnumElement[ALU8Func.type], store: Boolean = true): Unit = {
    val operand = mode_ext()
    read_byte(cycle = 2, addr = operand, comb_dest = src8_2)

    val b_bit = instr(6)

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
  }

  def JMPext(): Unit = {
    val operand = mode_ext()

    when(cycle === 2) {
      end_instr(operand)
    }
  }

  def NOP(): Unit = {
    end_instr(pc)
  }

  def STAext(): Unit = {
    val operand = mode_ext()

    val b_bit = instr(6)

    when(cycle === 2) {
      VMA  := 0
      Addr := operand
      RW   := 1
    }

    when(cycle === 3) {
      Addr  := operand
      Dout  := Mux(b_bit, b, a)
      RW    := 0
      cycle := 4
    }

    when(cycle === 4) {
      if(verification.isDefined) {
        formalData.write(Addr, Dout)
      }

      src8_2    := Mux(b_bit, b, a)
      alu8_func := ALU8Func.LD
      end_instr(pc)
    }
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
      cycle              := 2

      if(verification.isDefined) {
        formalData.read(Addr, io.Din)
      }
    }

    when(cycle === 2) {
      tmp16(7 downto 0) := io.Din
      pc                := (pc.asSInt + 1).asBits
      cycle             := 3

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
              0x1234 -> 0x7E, // JMP ext
              0x1235 -> 0x12, // Address high
              0x1236 -> 0x34, // Address low
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

        val config = SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = HIGH))
        config.includeFormal.generateSystemVerilog {
          val verification: Option[Verification] = args(0) match {
            case "jmp" => Some(new Formal_JMP)
            case "lda" => Some(new Formal_LDA)
            case "add" => Some(new Formal_ADD)
            case "sub" => Some(new Formal_SUB)
            case "and" => Some(new Formal_AND)
            case "bit" => Some(new Formal_BIT)
            case "cmp" => Some(new Formal_CMP)
            case "eor" => Some(new Formal_EOR)
            case "ora" => Some(new Formal_ORA)
            case "sta" => Some(new Formal_STA)
            case _     => None
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

          core.setDefinitionName("Core")
          core
        }.printPruned()
      }
    } else {
      SpinalVerilog(new Core).printPruned()
    }
  }
}
