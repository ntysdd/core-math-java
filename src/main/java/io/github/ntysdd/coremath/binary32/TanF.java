/*
 * Correctly-rounded tangent of binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/tan/tanf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_tanf -> TanF.compute
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

import io.github.ntysdd.coremath.internal.U128;

/**
 * Correctly rounded tangent of a binary32 value, the translation of the CORE-MATH
 * {@code cr_tanf}.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code roundeven_finite} expands to {@code __builtin_roundeven} in the
 *       reference build (GCC 13, x86-64), so it becomes {@link Math#rint}; the
 *       {@code #if} fallback behind it is dropped.
 *   <li>{@code int *q} output parameters become {@code int[]} of length one.
 *   <li>The {@code cn}, {@code cd}, {@code s} and {@code st} tables are declared
 *       inside {@code cr_tanf} upstream; they become fields. The {@code st}
 *       rows keep their {@code float} initializers and read the {@code uarg}
 *       member of the union through {@link Float#floatToRawIntBits}.
 *   <li>{@code feraiseexcept (FE_INVALID)} and the {@code __builtin_nanf} that
 *       follows it become {@link Float#NaN}.
 *   <li>{@code unsigned __int128} becomes {@link U128}.
 * </ul>
 */
public final strictfp class TanF {
  private TanF() {
  }

  // static const u64 ipi[] inside rbig() in the C source
  private static final long[] IPI = {0xfe5163abdebbc562L, 0xdb6295993c439041L, 0xfc2757d1f534ddc0L,
    0xa2f9836e4e441529L};

  // argument reduction
  // for |z| < 2^28, return r such that 2/pi*x = q + r
  private static double rltl(float z, int[] q) {
    double x = z;
    /* The constants in idh, idl approximate 2/pi. Since idh is representable
       on 28 bits, and x on 24 bits, idh is exact */
    double idl = -0x1.b1bbead603d8bp-32*x, idh = 0x1.45f306ep-1*x, id = Math.rint(idh);
    q[0] = (int)(long)id;
    return (idh - id) + idl;
  }

  // argument reduction
  // same as rltl, but for |x| >= 2^28
  private static double rbig(int u, int[] q) {
    int e = (u>>>23)&0xff, i;
    long m = (u&(~0>>>9))|1<<23;
    U128 p0 = U128.mul(m, IPI[0]);
    U128 p1 = U128.mul(m, IPI[1]); p1 = p1.addLow(p0.hi);
    U128 p2 = U128.mul(m, IPI[2]); p2 = p2.addLow(p1.hi);
    U128 p3 = U128.mul(m, IPI[3]); p3 = p3.addLow(p2.hi);
    long p3h = p3.hi, p3l = p3.lo, p2l = p2.lo, p1l = p1.lo;
    long a;
    int k = e-127, s = k-23;
    // in cr_tanf(), rbig() is called in the case 127+28 <= e < 0xff
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

  // static const double cn[], cd[] and s[] inside cr_tanf() in the C source
  private static final double[] cn =
    {0x1.921fb54442d18p+0, -0x1.fd226e573289fp-2, 0x1.b7a60c8dac9f6p-6, -0x1.725beb40f33e5p-13};
  private static final double[] cd =
    {0x1p+0, -0x1.2395347fb829dp+0, 0x1.2313660f29c36p-3, -0x1.9a707ab98d1c1p-9};
  private static final double[] s = {0, 1};

  // struct {union{float arg; uint32_t uarg;}; float rh, rl;} st[] inside
  // cr_tanf() in the C source
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
    new St(0x1.143ec4p+0f, 0x1.ddf9f6p+0f, -0x1.891d24p-52f),
    new St(0x1.ada6aap+27f, 0x1.e80304p-3f, 0x1.419f46p-58f),
    new St(0x1.af61dap+48f, 0x1.60d1c8p-2f, -0x1.2d6c3ap-55f),
    new St(0x1.0088bcp+52f, 0x1.ca1edp+0f, 0x1.f6053p-53f),
    new St(0x1.f90dfcp+72f, 0x1.597f9cp-1f, 0x1.925978p-53f),
    new St(0x1.cc4e22p+85f, -0x1.f33584p+1f, 0x1.d7254ap-51f),
    new St(0x1.a6ce12p+86f, -0x1.c5612ep-1f, -0x1.26c33ep-53f),
    new St(0x1.6a0b76p+102f, -0x1.e42a1ep+0f, -0x1.1dc906p-52f),
  };

  public static float compute(float x) {
    int u = Float.floatToRawIntBits(x);
    int e = (u>>>23)&0xff;
    int[] ia = new int[1];
    double z;
    if (e<127+28) { // |x| < 2^28
      if (e<115) { // |x| < 2^-13
        if (e<102) { // |x| < 2^-26
          return Math.fma(x, Math.abs(x), x);
        }
        float x2 = x*x;
        return Math.fma(x, 0x1.555556p-2f*x2, x);
      }
      z = rltl(x, ia);
    } else if (e<0xff) {
      z = rbig(u, ia);
    } else {
      if((u<<9)!=0) return x + x; // nan
      return Float.NaN; // feraiseexcept(FE_INVALID) then __builtin_nanf in the C source
    }
    double z2 = z*z, z4 = z2*z2;
    double n = cn[0] + z2*cn[1], n2 = cn[2] + z2*cn[3]; n += z4*n2;
    double d = cd[0] + z2*cd[1], d2 = cd[2] + z2*cd[3]; d += z4*d2;
    n *= z;
    double s0 = s[ia[0]&1], s1 = s[1-(ia[0]&1)];
    double r1 = (n*s1 - d*s0)/(n*s0 + d*s1);
    long tr = Double.doubleToRawLongBits(r1);
    long tail = (tr + 7)&(~0L>>>35);
    if(tail<=14) {
      int ax = u&(~0>>>1), sgn = u>>>31;
      for(int j=0;j<8;j++) {
        if(Float.floatToRawIntBits(ST[j].arg) == ax) {
          if(sgn!=0)
            return -ST[j].rh - ST[j].rl;
          else
            return  ST[j].rh + ST[j].rl;
        }
      }
    }
    return (float)r1;
  }
}
