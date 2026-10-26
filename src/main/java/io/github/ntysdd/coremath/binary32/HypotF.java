/*
 * Correctly rounded hypot(x,y) for binary32 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/hypot/hypotf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_hypotf -> HypotF.compute
 *
 * Copyright (c) 2022-2025 Alexei Sibidanov.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Translated mechanically; see the class Javadoc for the deviations.
 */

package io.github.ntysdd.coremath.binary32;

/**
 * Correctly rounded {@code sqrt(x*x + y*y)} of two binary32 values, the
 * translation of the CORE-MATH {@code cr_hypotf}.
 *
 * <p>The entry point settles the infinite and signaling-NaN arguments first,
 * takes the larger operand, and returns {@code (float) fma(2^-13, ay, at)} when
 * the smaller one is below {@code 2^-13} of it. Otherwise the sum of squares is
 * computed in binary64, where it is exact up to the residual the two
 * {@code __builtin_fma} terms recover, the square root is taken in binary64 and
 * adjusted by one ulp of the binary64 result when the binary32 value is on a
 * rounding boundary.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The {@code #ifdef CORE_MATH_SUPPORT_ERRNO} blocks are dropped, and with
 *       them the only {@code fegetround} call of the file and the
 *       rounding-mode-dependent underflow threshold it computes: Java has no
 *       {@code errno} and round-to-nearest-even only.
 *   <li>{@code __builtin_fmaxf} and {@code __builtin_fminf} are
 *       {@link Math#max} and {@link Math#min}, which is exact here because the
 *       NaN and infinite arguments were returned before.
 *   <li>{@code __builtin_sqrt} is {@link Math#sqrt}, {@code __builtin_fmaf} is
 *       {@link Math#fma} and {@code __builtin_fabsf} is {@link Math#abs}.
 *       {@code __builtin_expect} is dropped.
 *   <li>The {@code b32u32_u} unions become {@link Float#floatToRawIntBits};
 *       {@code tx} and {@code ty} are carried as {@code int} bit patterns and
 *       the {@code b64u64_u} {@code t} of the binary64 square root as a
 *       {@code long}.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned}, {@code uint32_t} shifts are
 *       {@code >>>} and {@code uint64_t} comparisons go through
 *       {@link Long#compareUnsigned}. The signaling-NaN test
 *       {@code tx.u > 0xff<<23 && !((tx.u>>22)&1)} is stored in a
 *       {@code boolean}.
 *   <li>Where the C code narrows a {@code double} to {@code float}
 *       ({@code c = r} and the final {@code return t.f}) the cast is explicit.
 * </ul>
 */
public final strictfp class HypotF {
  private HypotF() {
  }

  public static float compute(float x, float y) {
    float ax = Math.abs(x), ay = Math.abs(y);
    int tx = Float.floatToRawIntBits(ax), ty = Float.floatToRawIntBits(ay);
    if(Integer.compareUnsigned(tx, 0xff<<23) >= 0 || Integer.compareUnsigned(ty, 0xff<<23) >= 0){
      // either x or y is Inf or NaN
      boolean snanX = Integer.compareUnsigned(tx, 0xff<<23) > 0 && ((tx >>> 22) & 1) == 0;
      boolean snanY = Integer.compareUnsigned(ty, 0xff<<23) > 0 && ((ty >>> 22) & 1) == 0;
      if (snanX || snanY)
        return x + y; // will return qNaN
      if(tx == (0xff<<23)) return ax;
      if(ty == (0xff<<23)) return ay;
      return ax + ay;
    }
    float at = Math.max(ax, ay), c;
    ay = Math.min(ax, ay);
    double xd = at, yd = ay, x2 = xd*xd, y2 = yd*yd, r2 = x2 + y2;
    if(yd < xd*0x1.fffffep-13)
    {
      /* Since xd<=0x1.fffffep127, we have
         yd < 0x1.fffffep127*0x1.fffffep-13=0x1.fffffc000002p+115,
         thus sqrt(xd^2+yd^2) < 0x1.fffffefffffcdp+127 < 2^128,
         thus for rounding towards zero or downwards, overflow cannot happen. */
      c = Math.fma(0x1p-13f, ay, at);
      return c;
    }
    double r = Math.sqrt(r2);
    long t = Double.doubleToRawLongBits(r);
    c = (float) r;
    if(Long.compareUnsigned(t, 0x47efffffe0000000L) > 0){ // t > 0x1.fffffep+127
      return c;
    }
    if((((t + 1)&0xfffffffL) > 2)) {
      return c;
    }
    double cd = c;
    if((cd*cd - x2) - y2 == 0.0) return c;
    double ir2 = 0.5/r2, dr2 = (x2 - r2) + y2;
    double rs = r*ir2, dz = dr2 - Math.fma(r, r, -r2), dr = rs*dz;
    double rh = r + dr, rl = dr + (r - rh);
    t = Double.doubleToRawLongBits(rh);
    if((t&0xfffffffL) == 0){
      if(rl>0.0) t += 1;
      if(rl<0.0) t -= 1;
    }
    return (float) Double.longBitsToDouble(t);
  }
}
