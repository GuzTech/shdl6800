# shdl6800: A 6800 processor written in SpinalHDL
#
# Copyright (C) 2020 Oguz Meteer <info@guztech.nl>
#
# Permission to use, copy, modify, and/or distribute this software for any
# purpose with or without fee is hereby granted, provided that the above
# copyright notice and this permission notice appear in all copies.
#
# THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
# WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
# ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
# WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
# ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
# OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

# Run via:
# make -s formal
#
# This will run formal verifications for all instructions that haven't
# yet passed formal verification. That is, if formal verification
# fails on an instruction, you can run make -s formal again, and it will
# skip all instrutions that already verified properly, and pick up where
# the failure happened.
#
# Note that this generally doesn't help, since fixing the problem usually
# involves modifying core.py, which will then cause formal verification
# to begin anew.
#
# You can also run formal verification on one or more specific instructions
# via make -s formal_<insn> formal_<insn> ...

formal_targets := $(patsubst src/main/scala/shdl6800/formal/%.scala, %, $(wildcard src/main/scala/shdl6800/formal/Formal_*.scala))

.PHONY: formal

formal: $(formal_targets)

Formal_%: src/main/scala/shdl6800/formal/sby/%_bmc/status
	$(info $(shell date '+%d %b %Y %H:%M:%S') Verified instruction '$*')

# Don't delete the status file if the user hits ctrl-C.
.PRECIOUS: src/main/scala/shdl6800/formal/sby/%_bmc/status

src/main/scala/shdl6800/formal/sby/%_bmc/status: src/main/scala/shdl6800/formal/sby/Formal_%.sby
	$(info $(shell date '+%d %b %Y %H:%M:%S') Running formal verification on instruction '$*'...)
	sby -f $< 2>&1 >/dev/null; if [ $$? -ne 0 ]; then \
		echo `date '+%d %b %Y %H:%M:%S'` Formal verification FAILED for instruction \'$*\'; \
		rm $@; \
	fi

src/main/scala/shdl6800/formal/sby/%.sby: src/main/scala/shdl6800/formal/sby/%.sv src/main/scala/shdl6800/formal/formal.sby
	cat src/main/scala/shdl6800/formal/formal.sby | sed --expression='s#rel_file#$*#g' | sed --expression='s#abs_file#src/main/scala/shdl6800/formal/sby/$*#g' | sed --expression='s#top_entity#$*#g' > $@

src/main/scala/shdl6800/formal/sby/Formal_%.sv: src/main/scala/shdl6800/formal/Formal_%.scala src/main/scala/shdl6800/Core.scala
	mkdir -p src/main/scala/shdl6800/formal/sby
	sbt "runMain shdl6800.Core $*"
