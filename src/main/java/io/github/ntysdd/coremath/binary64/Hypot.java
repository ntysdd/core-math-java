/*
 * Correctly rounded hypot(x,y) for binary64 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary64/hypot/hypot.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_hypot -> Hypot.compute
 *
 * Copyright (c) 2022-2026 Alexei Sibidanov <sibid@uvic.ca>.
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
 * Correctly rounded {@code sqrt(x*x + y*y)} of two binary64 values, the
 * translation of the CORE-MATH {@code cr_hypot}.
 *
 * <p>The entry point settles the infinite and NaN arguments first, then moves
 * both operands above zero and scales the larger one into {@code [1,2)}: when
 * the two exponents differ by more than 27 the smaller operand cannot reach the
 * rounding and one fused multiply-add suffices. Otherwise {@code x*x + y*y} is
 * evaluated with its exact residuals, the square root is taken and refined
 * through the two-sided bound {@code aidr}; when that bound cannot decide, the
 * file falls back to {@code asHypotHard}, which squares the root in 128-bit
 * integer arithmetic and corrects it against the exact value of
 * {@code x^2 + y^2}. The subnormal case is {@code asHypotDenorm}, an integer
 * square root with the same correction loop on 64-bit wraparound arithmetic,
 * and {@code asHypotOverflow} produces the overflow result.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code get_flags} and {@code set_flags} are dropped, together with the
 *       {@code fexcept_t flag} parameter of {@code asHypotDenorm} and
 *       {@code asHypotHard} and the {@code volatile fexcept_t flag} of the
 *       entry point: they read and restore the exception flags through
 *       {@code _mm_getcsr} / {@code _mm_setcsr} on x86-64 and through
 *       {@code fegetexceptflag} / {@code fesetexceptflag} elsewhere, and the
 *       JVM exposes no such state. The {@code if (D == 0)} arm that only called
 *       {@code set_flags} keeps its place with a comment.
 *   <li>The directed-rounding arms are dropped: the C code probes the rounding
 *       mode with {@code op = 1.0 + 0x1p-54} and {@code om = 1.0 - 0x1p-54},
 *       which both round to {@code 1.0} in round-to-nearest, so
 *       {@code op == om} holds there and the {@code op > 1.0} and
 *       {@code (op == 1)} arms are dead. Java has round-to-nearest-even only.
 *       The {@code volatile double trig_uf} that the C code squares to raise the
 *       underflow flag, and the {@code errno} assignments, go with them.
 *   <li>{@code u128} and its {@code unsigned _BitInt(128)} variant become
 *       {@code internal.U128}: {@code (u128)lm*lm} is {@link U128#mul},
 *       {@code >>} is {@link U128#shr}, {@code <<} is {@link U128#shl} and the
 *       {@code !!(u128)} test is {@link U128#signum}. The reference build takes
 *       the {@code unsigned __int128} branch of the typedef.
 *   <li>The {@code b64u64_u} unions become {@link Double#doubleToRawLongBits}
 *       and {@link Double#longBitsToDouble}; {@code xi}, {@code yi},
 *       {@code xd}, {@code yd}, {@code ri}, {@code thd} and {@code tld} are
 *       carried as {@code long} bit patterns. The union round trip
 *       {@code t.u = ey; yd.f = t.f} of the subnormal rescaling is the identity
 *       on the bits and becomes the assignment {@code yd = ey}.
 *   <li>{@code __builtin_sqrt} is {@link Math#sqrt}, {@code __builtin_fma} is
 *       {@link Math#fma}, {@code __builtin_fabs} is {@link Math#abs},
 *       {@code __builtin_clzll} is {@link Long#numberOfLeadingZeros} and
 *       {@code __builtin_expect} is nothing. The {@code u64 rm =
 *       __builtin_sqrt(...)} of {@code asHypotDenorm} converts a {@code double}
 *       to an integer, so the Java spell it {@code (long) Math.sqrt(...)}.
 *   <li>{@code uint64_t} comparisons go through {@link Long#compareUnsigned}
 *       and {@code uint64_t} shifts are {@code >>>}, except where the C code
 *       shifts a signed {@code i64} ({@code tm >> 1}, {@code D >> 63}) or knows
 *       the value is positive.
 *   <li>The C comparisons that are stored or added as 0/1 integers become
 *       {@code boolean} where they are only tested ({@code rb2}, {@code sb},
 *       {@code ninf}, {@code nqnn}) and the ternary {@code cond ? 1 : 0} where
 *       their value is used in arithmetic ({@code rm += 1 + ...},
 *       {@code 1 << (k - ...)}).
 *   <li>The output parameters {@code e} of {@code fasttwosum} and the
 *       address-taken local {@code tl} of the entry point become one-element
 *       arrays.
 *   <li>{@code volatile double z} and {@code volatile double trig_uf} become
 *       plain locals, since the port has no reason to keep the C compiler from
 *       folding them.
 *   <li>{@code __attribute__((noinline))} is dropped and so are the
 *       {@code typedef}s of {@code u64} and {@code i64}.
 * </ul>
 */
public final strictfp class Hypot {
  private Hypot() {
  }

  private static double fasttwosum(double x, double y, double[] e) {
    double s = x + y;
    double z = s - x;
    e[0] = y - z;
    return s;
  }

  /* This routine deals with the case where both x and y are subnormal.
     a is the encoding of x, and b is the encoding of y.
     We assume x >= y > 0 thus 2^52 > a >= b > 0. */
  private static double asHypotDenorm(long a, long b) {
    double af = a, bf = b;
    boolean underflow = false;
    // af and bf are x and y multiplied by 2^1074, thus integers
    a <<= 1;
    b <<= 1;
    long rm = (long)Math.sqrt(af*af + bf*bf);
    long tm = rm << 1;
    long D = a*a + b*b - tm*tm;
    // D = a^2+b^2 - tm^2
    while (D > 2 * tm) { // tm too small
      D -= 2 * tm + 1;   // (tm+1)^2 = tm^2 + 2*tm + 1
      tm ++;
    }
    while (D < 0) {      // tm too large
      D += 2 * tm - 1;   // (tm-1)^2 = tm^2 - 2*tm + 1
      tm --;
    }
    if(D==0){
      // C restores here the floating-point flags it saved on entry
    }
    // tm = floor(sqrt(a^2+b^2)) and 0 <= D = a^2+b^2 - tm^2 < 2*tm+1
    // if D=0 and tm is even, the result is exact
    // if D=0 and tm is odd, the result is a midpoint
    int rb = (int)(tm & 1); // round bit for rm
    boolean rb2 = D >= tm; // round bit for tm
    boolean sb = D != 0; // sticky bit for rm
    /* We cannot have rb=1 and sb=0 (midpoint case) in the subnormal range.
       Indeed, this would mean x=a*2^-1074, y=b*2^-1074, z=(k+1/2)*2^-1074
       with a, b, k integers and x^2+y^2=z^2. This would imply
       a^2+b^2 = (k+1/2)^2 which is impossible: the left-hand side is an
       integer, while the right-hand side is 1/4 mod 1.
       Thus the only possible cases are:
       rb=0, sb=0: exact case
       rb=0, sb=1
       rb=1, sb=1
    */
    rm = tm >> 1; // truncate the low bit
    underflow = Long.compareUnsigned(rm, 0x10000000000000L) < 0;
    if(sb){ // non-exact case
      // The C code tests the rounding mode with op = 1.0 + 0x1p-54 and
      // om = 1.0 - 0x1p-54, which both round to 1.0 in round-to-nearest, so
      // that op == om holds and the rounding-upwards arm is dead; it is
      // dropped, Java has round-to-nearest-even only.
      rm += rb;
      // we have no underflow when rm is now 2^52 and rb2 != 0
      if ((rm>>>52) != 0 && rb2) underflow = false;
      if(underflow){ // trigger underflow exception _after_ rounding for inexact results
        // The C code multiplies 0x1p-1022 by itself here to raise the
        // underflow flag, and sets errno; the JVM has neither.
      }
    }
    // else the result is exact, and we have no underflow
    return Double.longBitsToDouble(rm);
  }

  /* Here the square root is refined by Newton iterations: x^2+y^2 is exact
     and fits in a 128-bit integer, so the approximation is squared (which
     also fits in a 128-bit integer), compared and adjusted if necessary using
     the exact value of x^2+y^2. */
  private static double asHypotHard(double x, double y) {
    // The C code tests the rounding mode with op = 1.0 + 0x1p-54 and
    // om = 1.0 - 0x1p-54, which both round to 1.0 in round-to-nearest, so that
    // op == om holds and the two arms below that do not assume it are dead;
    // they are dropped, Java has round-to-nearest-even only.
    long xi = Double.doubleToRawLongBits(x), yi = Double.doubleToRawLongBits(y);
    long bm = (xi&(~0L>>>12))|1L<<52;
    long lm = (yi&(~0L>>>12))|1L<<52;
    int be = (int)(xi>>52);
    int le = (int)(yi>>52);
    long ri = Double.doubleToRawLongBits(Math.sqrt(x*x + y*y));
    int bs = 2;
    long rm = (ri&(~0L>>>12)); int re = (int)(ri>>52)-0x3ff;
    rm |= 1L<<52;
    for(int i=0;i<3;i++){
      if(rm == 1L<<52){
        rm = ~0L>>>11;
        re--;
      } else
        rm--;
    }
    bm <<= bs;
    long m2 = bm*bm;
    int de = be-le;
    int ls = bs-de;
    if(ls>=0){
      lm <<= ls;
      m2 += lm*lm;
    } else {
      U128 lm2 = U128.mul(lm, lm);
      ls *= 2;
      m2 += lm2.shr(-ls).lo; // since ls < 0, the shift by -ls is legitimate
      m2 |= lm2.shl(128 + ls).signum();
    }
    int k = bs+re;
    long D;
    do {
      rm += 1 + (rm>=(1L<<53) ? 1 : 0);
      long tm = rm << k, rm2 = tm*tm;
      D = m2 - rm2;
    } while(D>0);
    if(D==0){
      // C restores here the floating-point flags it saved on entry
    } else {
      long tm = (rm << k) - (1L<<(k-(rm<=(1L<<53) ? 1 : 0)));
      D = m2 - tm*tm;
      if(D != 0)
        rm += D>>63;
      else
        rm -= rm&(1L<<(1-(rm<=(1L<<53) ? 1 : 0)));
    }
    if(rm>=(1L<<53)){
      rm >>= 1;
      re ++;
    }

    long e = be - 1 + re;
    xi = (e<<52) + rm;
    return Double.longBitsToDouble(xi);
  }

  // case hypot(x,y) >= 2^1024
  private static double asHypotOverflow() {
    double z = 0x1.fffffffffffffp1023;
    double f = z + z;
    return f;
  }

  public static double compute(double x, double y) {
    long xi = Double.doubleToRawLongBits(x), yi = Double.doubleToRawLongBits(y);
    long emsk = 0x7ffL<<52, ex = xi&emsk, ey = yi&emsk;
    /* emsk corresponds to the upper bits of NaN and Inf (apart the sign bit) */
    x = Math.abs(x); y = Math.abs(y);
    if(ex==emsk||ey==emsk){
      /* Either x or y is NaN or Inf */
      long wx = xi<<1, wy = yi<<1, wm = emsk<<1;
      boolean ninf = (wx==wm) ^ (wy==wm);
      boolean nqnn = ((wx>>>52)==0xfff) ^ ((wy>>>52)==0xfff);
      /* ninf is 1 when only one of x and y is +/-Inf
         nqnn is 1 when only one of x and y is qNaN
         IEEE 754 says that hypot(+/-Inf,qNaN)=hypot(qNaN,+/-Inf)=+Inf. */
      if (ninf && nqnn) return (wx==wm) ? x * x : y * y;
      return x + y; /* inf, nan */
    }
    // now both x and y are finite (neither NaN nor Inf)
    double u = x > y ? x : y, v = x > y ? y : x;
    long xd = Double.doubleToRawLongBits(u), yd = Double.doubleToRawLongBits(v);
    ey = yd;
    if((ey>>>52) == 0){ // y is subnormal
      if(ey == 0) return Double.longBitsToDouble(xd);
      ex = xd;
      if((ex>>>52) == 0) // x is subnormal too
        /* we can't have x=0 here since then y=0 because 0<=y<=x,
           and the case y=0 was tested above */
        return asHypotDenorm(ex,ey);
      int nz = Long.numberOfLeadingZeros(ey);
      ey <<= nz-11;
      ey &= ~0L>>>12;
      ey -= (long)(nz-12)<<52;
      yd = ey; // C: t.u = ey; yd.f = t.f
    }
    long de = xd - yd;
    if(Long.compareUnsigned(de, 27L<<52) > 0) {
      double r = Math.fma(0x1p-27, v, u);
      return r;
    }
    long off = (0x3ffL<<52) - (xd & emsk);
    xd += off;
    yd += off;
    x = Double.longBitsToDouble(xd);
    y = Double.longBitsToDouble(yd);
    double x2 = x*x, dx2 = Math.fma(x,x,-x2);
    double y2 = y*y, dy2 = Math.fma(y,y,-y2);
    double r2 = x2 + y2, ir2 = 0.5/r2, dr2 = ((x2 - r2) + y2) + (dx2 + dy2);
    double th = Math.sqrt(r2), rsqrt = th*ir2;
    double[] tl = new double[1];
    double dz = dr2 - Math.fma(th,th,-r2);
    tl[0] = rsqrt*dz;
    th = fasttwosum(th, tl[0], tl);
    long thd = Double.doubleToRawLongBits(th);
    long tld = Double.doubleToRawLongBits(Math.abs(tl[0]));
    ex = thd;
    ey = tld;
    ex &= 0x7ffL<<52;
    long aidr = ey + (0x3feL<<52) - ex;
    long mid = (aidr - 0x3c90000000000000L + 16)>>>5;
    long midm = (aidr - 0x3c80000000000000L + 16)>>>5;
    if( mid==0 || midm==0
        || Long.compareUnsigned(aidr, 0x39b0000000000000L) < 0
        || Long.compareUnsigned(aidr, 0x3c9fffffffffff80L) > 0)
      thd = Double.doubleToRawLongBits(asHypotHard(x,y));
    thd -= off;
    if(Long.compareUnsigned(thd, 0x7ffL<<52) >= 0) return asHypotOverflow();
    return Double.longBitsToDouble(thd);
  }
}
