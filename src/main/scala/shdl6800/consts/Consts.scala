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

package shdl6800.Consts

import spinal.core.{SpinalEnum, SpinalEnumEncoding}

object ModeBits extends SpinalEnum {
  val IMMEDIATE, DIRECT, INDEXED, EXTENDED = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding")(
    IMMEDIATE -> 0,
    DIRECT    -> 1,
    INDEXED   -> 2,
    EXTENDED  -> 3
  )
}

object Flags {
  val H = 5
  val I = 4
  val N = 3
  val Z = 2
  val V = 1
  val C = 0
}