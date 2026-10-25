/*
 * Correctly-rounded cosine of binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/cos/cosf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_cosf -> CosF.compute
 *
 * Copyright (c) 2022-2023 Alexei Sibidanov.
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

import io.github.ntysdd.coremath.internal.U128;

/**
 * Correctly rounded cosine of a binary32 value, the translation of the CORE-MATH
 * {@code cr_cosf}.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code roundeven_finite} expands to {@code __builtin_roundeven} in the
 *       reference build (GCC 13, x86-64), so it becomes {@link Math#rint}; the
 *       {@code #if} fallback behind it is dropped.
 *   <li>{@code int *q} output parameters become {@code int[]} of length one.
 *   <li>The {@code st[]} table keeps its {@code float} initializers and reads
 *       the {@code uarg} member of the union through
 *       {@link Float#floatToRawIntBits}.
 *   <li>The {@code 0.0f/0.0f} that upstream writes to raise {@code FE_INVALID}
 *       becomes {@link Float#NaN}.
 *   <li>{@code unsigned __int128} becomes {@link U128}, and {@code uint32_t}
 *       comparisons are unsigned through {@link Integer#compareUnsigned}.
 * </ul>
 */
public final strictfp class CosF {
  private CosF() {
  }

  // static const u64 ipi[] inside rbig() in the C source
  private static final long[] IPI = {0xfe5163abdebbc562L, 0xdb6295993c439041L, 0xfc2757d1f534ddc0L,
    0xa2f9836e4e441529L};

  private static double rbig(int u, int[] q) {
    int e = (u>>>23)&0xff, i;
    long m = (u&(~0>>>9))|1<<23;
    U128 p0 = U128.mul(m, IPI[0]);
    U128 p1 = U128.mul(m, IPI[1]); p1 = p1.addLow(p0.hi);
    U128 p2 = U128.mul(m, IPI[2]); p2 = p2.addLow(p1.hi);
    U128 p3 = U128.mul(m, IPI[3]); p3 = p3.addLow(p2.hi);
    long p3h = p3.hi, p3l = p3.lo, p2l = p2.lo, p1l = p1.lo;
    long a;
    int k = e-124, s = k-23;
    // in cr_cosf(), rbig() is called in the case 127+28 <= e < 0xff
    // thus 155 <= e <= 254, which yields 28 <= k <= 127 and 5 <= s <= 104
    if (s<64) {
      i = (int)(p3h<<s|p3l>>>(64-s));
      a = p3l<<s|p2l>>>(64-s);
    } else if(s==64) {
      i = (int)p3l;
      a = p2l;
    } else { // s > 64
      i = (int)(p3l<<(s-64)|p2l>>>(128-s));
      a = p2l<<(s-64)|p1l>>>(128-s);
    }
    int sgn = u; sgn >>= 31;
    long sm = a>>63;
    i -= sm;
    double z = (a^sgn)*0x1p-64;
    i = (i^sgn) - sgn;
    q[0] = i;
    return z;
  }

  private static double rltl(float z, int[] q) {
    double x = z;
    double idl = -0x1.b1bbead603d8bp-29*x, idh = 0x1.45f306ep+2*x, id = Math.rint(idh);
    long Q = Double.doubleToRawLongBits(0x1.8p52 + id); q[0] = (int)Q;
    return (idh - id) + idl;
  }

  private static double rltl0(double x, int[] q) {
    double idh = 0x1.45f306dc9c883p+2*x, id = Math.rint(idh);
    long Q = Double.doubleToRawLongBits(0x1.8p52 + id); q[0] = (int)Q;
    return idh - id;
  }

  // struct {union{float arg; uint32_t uarg;}; float rh, rl;} st[] inside
  // as_cosf_database() in the C source
  private static final class St {
    final float arg;
    final float rh;
    final float rl;

    St(float arg, float rh, float rl) {
      this.arg = arg;
      this.rh = rh;
      this.rl = rl;
    }
  }

  private static final St[] ST = {
    new St(0x1.2d97c8p+2f, 0x1.99bc5cp-27f, -0x1p-52f),
    new St(0x1.4555p+51f, 0x1.115d7ep-1f, -0x1p-26f),
    new St(0x1.48a858p+54f, 0x1.f48148p-2f, 0x1p-27f),
    new St(0x1.3170fp+63f, 0x1.fe2976p-1f, 0x1p-26f),
    new St(0x1.2b9622p+67f, 0x1.f0285ep-1f, -0x1p-26f),
  };

  private static float asCosfDatabase(float x, double r) {
    int u = Float.floatToRawIntBits(x);
    int ax = u&(~0>>>1);
    for(int i=0; i<ST.length; i++)
      if(Float.floatToRawIntBits(ST[i].arg) == ax) return ST[i].rh + ST[i].rl;
    return (float)r;
  }

  private static final double[] b =
    {0x1.3bd3cc9be45dcp-6, -0x1.03c1f081b0833p-14, 0x1.55d3c6fc9ac1fp-24, -0x1.e1d3ff281b40dp-35};
  private static final double[] a =
    {0x1.921fb54442d17p-3, -0x1.4abbce6256a39p-10, 0x1.466bc5a518c16p-19, -0x1.32bdc61074ff6p-29};
  private static final double[] tb =
    {0x1p+0, 0x1.f6297cff75cbp-1, 0x1.d906bcf328d46p-1, 0x1.a9b66290ea1a3p-1,
     0x1.6a09e667f3bcdp-1, 0x1.1c73b39ae68c8p-1, 0x1.87de2a6aea963p-2, 0x1.8f8b83c69a60bp-3,
     0x0p+0, -0x1.8f8b83c69a60bp-3, -0x1.87de2a6aea963p-2, -0x1.1c73b39ae68c8p-1,
     -0x1.6a09e667f3bcdp-1, -0x1.a9b66290ea1a3p-1, -0x1.d906bcf328d46p-1, -0x1.f6297cff75cbp-1,
     -0x1p+0, -0x1.f6297cff75cbp-1, -0x1.d906bcf328d46p-1, -0x1.a9b66290ea1a3p-1,
     -0x1.6a09e667f3bcdp-1, -0x1.1c73b39ae68c8p-1, -0x1.87de2a6aea963p-2, -0x1.8f8b83c69a60bp-3,
     0x0p+0, 0x1.8f8b83c69a60bp-3, 0x1.87de2a6aea963p-2, 0x1.1c73b39ae68c8p-1,
     0x1.6a09e667f3bcdp-1, 0x1.a9b66290ea1a3p-1, 0x1.d906bcf328d46p-1, 0x1.f6297cff75cbp-1};

  private static float asCosfBig(float x) {
    int u = Float.floatToRawIntBits(x);
    int ax = u<<1;
    if(Integer.compareUnsigned(ax, 0xff<<24)>=0) { // nan or +-inf
      if((ax<<8)!=0) return x + x; // nan
      return Float.NaN; // 0.0f/0.0f in the C source, to raise FE_INVALID
    }
    int[] ia = new int[1];
    double z = rbig(u, ia);
    double z2 = z*z, z4 = z2*z2;
    double aa = (a[0] + z2*a[1]) + z4*(a[2] + z2*a[3]);
    double bb = (b[0] + z2*b[1]) + z4*(b[2] + z2*b[3]);
    double s0 = tb[(ia[0]+8)&31], c0 = tb[ia[0]&31];
    double r = c0 + z*(aa*s0 - bb*(z*c0));
    long tr = Double.doubleToRawLongBits(r); long tail = (tr + 6)&(~0L>>>36);
    if(tail<=12) return asCosfDatabase(x, r);
    return (float)r;
  }

  public static float compute(float x) {
    int u = Float.floatToRawIntBits(x);
    int ax = u<<1;
    int[] ia = new int[1];
    double z0 = x, z;
    if (Integer.compareUnsigned(ax, 0x99000000)>0 || Integer.compareUnsigned(ax, 0x74000000)<0){
      // |x| > 0x1p+26 or |x| < 0x1p-11
      if (Integer.compareUnsigned(ax, 0x74000000)<0) // |x| < 0x1p-11
        /* For |x| < 2^-11, cos(x) rounds to the same value as 1-x^2/2
           for all rounding modes. However, __builtin_fmaf (x/2, -x, 1.0f)
           would raise a spurious underflow for x/2, which the following
           formula avoids. */
        return 0.5f * Math.fma(x, -x, 2.0f);
      return asCosfBig(x); // |x| > 0x1p+26
    }
    if(Integer.compareUnsigned(ax, 0x82a41896)<0){
      if(ax==0x812d97c8) return asCosfDatabase(x, 0.0);
      z = rltl0(z0, ia);
    } else {
      z = rltl((float)z0, ia);
    }
    double z2 = z*z, z4 = z2*z2;
    double aa = (a[0] + z2*a[1]) + z4*(a[2] + z2*a[3]);
    double bb = (b[0] + z2*b[1]) + z4*(b[2] + z2*b[3]);
    double c0 = tb[ia[0]&31], s0 = tb[(ia[0]+8)&31];
    double r = c0 + aa*(z*s0) - bb*(z2*c0);
    return (float)r;
  }
}
