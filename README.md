shdl6800
============
This is a [SpinalHDL](https://github.com/SpinalHDL/SpinalHDL) reimplementation of [Robert Baruch's 6800 processor implementation](https://github.com/RobertBaruch/n6800) written in [nMigen](https://github.com/m-labs/nmigen).

Robert also posts videos about the implementation, which you can find on his [YouTube channel](https://www.youtube.com/channel/UCBcljXmuXPok9kT_VGA3adg). 

## Why?

I am experimenting with several different hardware description languages, and nMigen (combined with the [LiteX toolbox](https://github.com/enjoy-digital/litex)) looks really good.
I also have some experience with SpinalHDL, and while watching his videos, it looked surprisingly similar to how you would implement it in SpinalHDL. This reimplementation is just to see how similar it can be to his implementation.

## Status

As of this writing, I have caught up with [Part 3](https://www.youtube.com/watch?v=aLQqOxnVMOQ), and the formal verification part is also implemented.

The following instructions have been implemented:

- NOP
- JMP ext (with formal)

## Generating Verilog

Run the `main` function of the `CoreVerilog` object:

```
sbt "runMain shdl6800.CoreVerilog"
```

## Formal Verification

*As of this commit, SpinalHDL generates an incorrect SystemVerilog file containing formal proofs, so this is not yet possible.*

Formal verification requires the free and open-source [SymbiYosys](https://symbiyosys.readthedocs.io/en/latest/quickstart.html) tools. Once installed, first run the `main` function of the `CoreVerilog` object with the instruction you want to formally verify. For example:

```
sbt "runMain shdl6800.CoreVerilog jmp"
```

This will generate a SystemVerilog file with the formal proofs for the `jmp` instruction.

Then simply run SymbiYosys:

```
sby -f Core.sby
```

## License

shdl6800 is free and open hardware and is licensed under the [ISC licence](http://en.wikipedia.org/wiki/ISC_license).
