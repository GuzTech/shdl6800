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
- [Part 5](https://www.youtube.com/watch?v=9MMb9dSnNvo) is [completed](https://github.com/GuzTech/shdl6800/tree/part_5).
- [Part 6](https://www.youtube.com/watch?v=C6sUaElP9hA) is [completed](https://github.com/GuzTech/shdl6800/tree/part_6).

The following instructions have been implemented:

- NOP
- ADD (with formal *add*)
- ADC (with formal *add*)
- AND (with formal *and*)
- BIT (with formal *bit*)
- CMP (with formal *cmp*)
- EOR (with formal *eor*)
- JMP (with formal *jmp*)
- LDA (with formal *lda*)
- ORA (with formal *ora*)
- STA (with formal *sta*)
- SUB (with formal *sub*)
- SBC (with formal *sub*)
- BR  (with formal *br*)
- CLI (with formal *flag*)
- SEI (with formal *flag*)
- CLC (with formal *flag*)
- SEC (with formal *flag*)
- CLV (with formal *flag*)
- SEV (with formal *flag*)
- TAP (with formal *tap*)
- TPA (with formal *tpa*)

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

Formal verification requires the free and open-source [SymbiYosys](https://symbiyosys.readthedocs.io/en/latest/quickstart.html) and [Boolector](https://boolector.github.io/) tools. Once installed, you can either formally verify all instructions, one by one:

```
make -s formal
```

Or verify them in parallel by specifying how many you want with the `-j<number>` flag. For example, to use all the cores of your CPU:

```
make -s formal -j$(nproc)
```

Or you can verify a single instruction by specifying the filename of the formal verification file of that instruction. For example, verifying the `LDA` instruction is done like this:

```
make -s Formal_LDA
```

## License

shdl6800 is free and open hardware and is licensed under the [ISC licence](http://en.wikipedia.org/wiki/ISC_license).
