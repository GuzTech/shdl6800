shdl6800
============
This is a [SpinalHDL](https://github.com/SpinalHDL/SpinalHDL) reimplementation of [Robert Baruch's 6800 processor implementation](https://github.com/RobertBaruch/n6800) written in [nMigen](https://github.com/m-labs/nmigen).

Robert also posts videos about the implementation, which you can find on his [YouTube channel](https://www.youtube.com/channel/UCBcljXmuXPok9kT_VGA3adg). 

## Why?

I am experimenting with several different hardware description languages, and nMigen (combined with the [LiteX toolbox](https://github.com/enjoy-digital/litex)) looks really good.
I also have some experience with SpinalHDL, and while watching his videos, it looked surprisingly similar to how you would implement it in SpinalHDL. This reimplementation is just to see how similar it can be to his implementation.

## Status

As of this writing, I have somewhat caught up with [Part 3](https://www.youtube.com/watch?v=aLQqOxnVMOQ), and the initial formal verification part is also implemented.

The following instructions have been implemented:

- NOP
- CLC, CLV, CLI, SEC, SEV, and SEI (with formal)
- JMP ext (with formal)
- BRA, BNE, BEQ, BVC, and BVS (with formal)

Formal verification requires the free and open-source [SymbiYosys](https://symbiyosys.readthedocs.io/en/latest/quickstart.html) tools. Once installed, first run the `main` function of the `CoreVerilogWithFormal` object so that it generates a SystemVerilog file with the formal proofs in them:

```
sbt "runMain shdl6800.CoreVerilogWithFormal"
```

Then simply run SymbiYosys:

```
sby -f Core.sby
```

As of this writing, there is a bug in SpinalHDL several `$past()` statements are generated outside clocked `always` blocks. You have to manually substitute the signals inside the `assert` statements to fix this.

## License

shdl6800 is free and open hardware and is licensed under the [ISC licence](http://en.wikipedia.org/wiki/ISC_license).
