shdl6800
============
This is a [SpinalHDL](https://github.com/SpinalHDL/SpinalHDL) reimplementation of [Robert Baruch's 6800 processor implementation](https://github.com/RobertBaruch/n6800) written in [nMigen](https://github.com/m-labs/nmigen).

Robert also posts videos about the implementation, which you can find on his [YouTube channel](https://www.youtube.com/channel/UCBcljXmuXPok9kT_VGA3adg). 

## Why?

I am experimenting with several different hardware description languages, and nMigen (combined with the [LiteX toolbox](https://github.com/enjoy-digital/litex)) looks really good.
I also have some experience with SpinalHDL, and while watching his videos, it looked surprisingly similar to how you would implement it in SpinalHDL. This reimplementation is just to see how similar it can be to his implementation.

## Status

- [Part 3](https://www.youtube.com/watch?v=aLQqOxnVMOQ) is [completed](https://github.com/GuzTech/shdl6800/tree/part_3).
- [Part 4](https://www.youtube.com/watch?v=xqMtyCu4lME) is [completed](https://github.com/GuzTech/shdl6800/tree/part_4).
- [Part 5](https://www.youtube.com/watch?v=9MMb9dSnNvo) is completed as far as the HDL and the FPGA specific code. Code for device and package specification, and pin constraints have also been implemented but needs a bit more testing. The implementation is [working](https://twitter.com/BitlogIT/status/1214352859376029696) though.

The following instructions have been implemented:

- NOP
- ADDA/ADDB ext (with formal *add*)
- ADCA/ADCB ext (with formal *add*)
- ANDA/ANDB ext (with formal *and*)
- BITA/BITB ext (with formal *bit*)
- CMPA/CMPB ext (with formal *cmp*)
- EORA/EORB ext (with formal *eor*)
- JMP ext (with formal *jmp*)
- LDAA/LDAB ext (with formal *lda*)
- ORAA/ORAB ext (with formal *ora*)
- STAA/STAB ext (with formal *sta*)
- SUBA/SUBC ext (with formal *sub*)
- SBCA/SBCB ext (with formal *sub*)

## Generating Verilog

Run the `main` function of the `Core` object:

```
sbt "runMain shdl6800.Core"
```

## Running Simulation

To run simulation, you need [Verilator](https://www.veripool.org/wiki/verilator). Once installed, run the `main` function of the `Core` object with `sim` as a parameter:

```
sbt "runMain shdl6800.Core sim"
```

This will generate Verilator code of the core and simulate the design as described in `Core.scala`. It will also generate a `test.vcd` trace file in the `simWorkspace/Core` folder, which you can view with [GTKWave](http://gtkwave.sourceforge.net/).

## Formal Verification

Formal verification requires the free and open-source [SymbiYosys](https://symbiyosys.readthedocs.io/en/latest/quickstart.html) tools. Once installed, first run the `main` function of the `Core` object with the instruction you want to formally verify. For example:

```
sbt "runMain shdl6800.Core jmp"
```

This will generate a SystemVerilog file with the formal proofs for the `jmp` instruction.

Then simply run SymbiYosys:

```
sby -f Core.sby
```

## License

shdl6800 is free and open hardware and is licensed under the [ISC licence](http://en.wikipedia.org/wiki/ISC_license).
