/*
 * Correctly rounded reciprocal square root of a binary64 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary64/rsqrt/rsqrt.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_rsqrt -> Rsqrt.compute
 *
 * Copyright (c) 2025 Paul Zimmermann
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

package io.github.ntysdd.coremath.binary64;

import io.github.ntysdd.coremath.internal.U128;

/**
 * Correctly rounded {@code 1/sqrt(x)} of a binary64 value, the translation of
 * the CORE-MATH {@code cr_rsqrt}.
 *
 * <p>The fast path forms {@code (1/x)*sqrt(x)} (with a scaling that avoids
 * overflow and underflow near the ends of the exponent range), refines it with
 * one Newton step in double-double, and calls {@code asRsqrtRefine} when the
 * two candidates of the double result are less than 24 ulps apart or more than
 * 100 binades apart. That routine works in 128-bit integers: it aligns the
 * significands, runs a bisection-like integer Newton iteration on the 128-bit
 * square, and then performs the final rounding with one more multiplication.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The whole rounding-mode machinery is dropped: {@code get_rounding_mode},
 *       {@code get_arm_rounding_mode}, the {@code fpcr_bitfield} struct, the
 *       two lookup tables {@code lut} they carry, and the
 *       {@code #include <x86intrin.h>} and {@code #include <arm64intr.h>} that
 *       only they need. Java has round-to-nearest-even and nothing else, so
 *       {@code mode == FE_TONEAREST} holds at the single place it is read: the
 *       {@code rrt += am>>2; rrt += rt; inc = rrt>>127; ir.u += inc;} arm is
 *       kept and the {@code else { ir.u += mode == FE_UPWARD; }} arm is removed
 *       with a comment.
 *   <li>The {@code _BitInt(128)} variant of the {@code #if} on clang 14 and
 *       gcc 14 is dropped, the reference build takes the
 *       {@code unsigned __int128} arm; {@code u128} becomes
 *       {@link U128}, {@code <<}/{@code >>} become {@link U128#shl} and
 *       {@link U128#shr}, and the sign-filled {@code ^(-s)} and {@code + s} of
 *       the two's complement trick keep their 64-bit sign extension through
 *       {@link U128#of(long, long)} and {@link U128#addLow}.
 *   <li>{@code b64u64_u} becomes {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}. The two {@code u64} comparisons that
 *       can see the sign bit go through {@link Long#compareUnsigned}.
 *   <li>{@code __attribute__((noinline))} on {@code as_rsqrt_refine} and
 *       {@code __builtin_expect} are dropped, and {@code __builtin_clzll} is
 *       {@link Long#numberOfLeadingZeros}.
 *   <li>Every {@code CORE_MATH_SUPPORT_ERRNO} assignment is dropped. With
 *       {@code feraiseexcept (FE_INVALID)} gone, the two NaNs it accompanies
 *       are written as {@link Double#NaN} instead of {@code __builtin_nan
 *       ("<0")}, and {@code 1.0/0.0} and {@code 1.0/-0.0} become
 *       {@link Double#POSITIVE_INFINITY} and {@link Double#NEGATIVE_INFINITY}.
 *   <li>The {@code #ifdef __INTEL_CLANG_COMPILER} block, which forwards
 *       {@code rsqrt} to the compiler's {@code invsqrt}, is an unbuilt
 *       {@code #if} variant and is dropped.
 *   <li>The empty {@code if (ia.u<<11 == 1ull<<63) { } else { ... }} of
 *       {@code as_rsqrt_refine} is kept as it stands.
 * </ul>
 */
public final strictfp class Rsqrt {
  private Rsqrt() {
  }

  // __attribute__((noinline)) is dropped, the JIT decides
  private static double asRsqrtRefine(double rf, double a) {
    long ir = Double.doubleToRawLongBits(rf), ia = Double.doubleToRawLongBits(a);
    if (Long.compareUnsigned(ia, 1L << 52) < 0) {
      long nz = Long.numberOfLeadingZeros(ia);
      ia <<= nz - 11;
      ia &= ~0L >>> 12;
      long e = nz - 12;
      ia |= e << 52;
    }
    if ((ia << 11) == (1L << 63)) {
    } else {
      int e = (int) (ia >>> 52) & 1;
      long rm, am;
      rm = (ir << 11 | 1L << 63) >>> 11;
      am = ((ia & (~0L >>> 12)) | 1L << 52) << (5 - e);
      U128 rt = U128.mul(rm, am);
      long rth = rt.hi, rtl = rt.lo;
      U128 rrt = U128.mul(rtl, rm);
      long t0 = rrt.lo, t1 = rrt.hi + rth*rm;
      rrt = U128.of(t0, t1);
      long s = rrt.hi >>> 63, dd = 1 - 2*s;
      U128 rts = rt.shl(1).xor(U128.of(-s, -s)).addLow(s);
      U128 prrt;
      long am2 = am << 1, am20 = -am;
      do {
        ir -= dd;
        prrt = rrt;
        am20 += am2;
        U128 tt = rts.sub(U128.of(am20, 0));
        rrt = rrt.sub(tt);
      } while (((prrt.hi ^ rrt.hi) >>> 63) == 0);
      ir += ((rrt.hi >>> 63) != 0) ? 0 : dd;
      rrt = ((rrt.hi >>> 63) != 0) ? rrt : prrt;
      // the C code branches on the rounding mode here; Java has only
      // round-to-nearest-even, so its arm is the only one left
      rm = (ir << 11 | 1L << 63) >>> 11;
      rt = U128.mul(rm, am);
      rrt = rrt.add(U128.of(am >>> 2, 0));
      rrt = rrt.add(rt);
      long inc = rrt.hi >>> 63;
      ir += inc;
      rf = Double.longBitsToDouble(ir);
    }
    return rf;
  }

  public static double compute(double x) {
    long ix = Double.doubleToRawLongBits(x);
    double r;
    int e = (int) (ix >>> 52);
    if (e == 0) {
      if (ix != 0) // x <> +0
        r = Math.sqrt(x)/x;
      else {
        // the C source sets errno to ERANGE here (pole error)
        return Double.POSITIVE_INFINITY; // 1.0/0.0 in the C source, case x = +0
      }
    } else if (e < 0x7fd) {
      // common case: 2^-1022 <= x < 2^1022
      r = (1.0 / x) * Math.sqrt(x);
    } else if (e < 0x7ff) {
        r = (4.0 / x) * (0.25 * Math.sqrt(x));
    } else {
      long ax = ix << 1;
      if (ax == 0) {
        // the C source sets errno to ERANGE here (pole error)
        return Double.NEGATIVE_INFINITY; // 1.0/-0.0 in the C source, x=-0
      }
      if (Long.compareUnsigned(ax, 0x7ffL << 53) > 0) return x + x; // NaN
      if ((e >> 11) != 0) { // x < 0
        // the C source sets errno to EDOM here and raises FE_INVALID
        return Double.NaN; // __builtin_nan("<0") in the C source
      }
      return 0.0; // +Inf
    }
    // use Newton iteration r' = r - r/2*(r^2*x-1)
    double rx = r*x, drx = Math.fma(r, x, -rx);
    // rx + drx approximates r*x with about 106 bits
    double h = Math.fma(r, rx, -1.0) + r * drx, dr = (r * 0.5) * h;
    // h approximates r^2*x-1
    // dr approximates the correction term r/2*(r^2*x-1)
    double rf = r - dr;
    long idr = Double.doubleToRawLongBits(dr), ir = Double.doubleToRawLongBits(r);
    int de = (int) (ir >>> 52) - ((int) (idr >>> 52) & 0x7ff);
    long d = (idr + 18) & ~0L >>> 13;
    if (d <= 23 || de > 100)
      rf = asRsqrtRefine(rf, x);
    return rf;
  }
}
