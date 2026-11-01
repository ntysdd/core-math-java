/*
 * Correctly rounded sine and cosine of a binary32 value at once.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/sincos/sincosf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_sincosf -> SincosF.compute
 *
 * Copyright (c) 2024-2025 Alexei Sibidanov <sibid@uvic.ca>
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
 * Correctly rounded {@code sin(x)} and {@code cos(x)} of a binary32 value at
 * once, the translation of the CORE-MATH {@code cr_sincosf}.
 *
 * <p>{@code compute} writes the sine to {@code sout[0]} and the cosine to
 * {@code cout[0]}, mirroring the two output pointers of the C entry point. It
 * splits at {@code |x| = 0x1.2d97c8p+3}: below, three Taylor branches cover
 * {@code |x| < 2^-25} and {@code |x| < 2^-12}, and everything else reduces
 * {@code x} modulo pi/2 with the {@code rltl0} (below {@code 0x1p-12}) or
 * {@code rltl} reduction before evaluating the two degree-3 polynomials
 * {@code a} and {@code b} and the 32-entry table {@code tb}; above, the
 * argument is reduced by a 128-bit product with the bits of 1/pi in
 * {@code rbig}, and the same polynomials follow. Both ends consult the 9-row
 * {@code st} table, first for the arguments named by the C source and then,
 * after {@code rbig}, when the last bits of the cosine are within 12 of a
 * rounding boundary.
 *
 * <p>Deviations from the C source, all of them forced by the language or by
 * the mapping table in AGENTS.md:
 * <ul>
 *   <li>{@code roundeven_finite} is {@link Math#rint}, since the reference
 *       build defines it as {@code __builtin_roundeven};
 *       {@code __builtin_copysignf} is {@link Math#copySign} and
 *       {@code __builtin_fmaf} is {@link Math#fma}. The other
 *       {@code __builtin_expect (c, n)} calls are {@code c}.
 *   <li>The {@code errno} assignment of the {@code |x| < 2^-25} underflow case
 *       and of {@code as_sincosf_big} are dropped, together with the
 *       {@code #ifdef CORE_MATH_SUPPORT_ERRNO} blocks that contain nothing
 *       else; {@code #pragma STDC FENV_ACCESS ON} goes with them.
 *   <li>{@code 0.0f/0.0f}, which {@code as_sincosf_big} evaluates for
 *       {@code ±inf} to raise {@code FE_INVALID}, is {@link Float#NaN};
 *       {@code x + x} is kept, since it only propagates a NaN argument.
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become {@code int}
 *       and {@code long} bit patterns through
 *       {@link Float#floatToRawIntBits}, {@link Double#doubleToRawLongBits}
 *       and {@link Double#longBitsToDouble}.
 *   <li>{@code unsigned __int128} is {@link U128}. The {@code u128} products
 *       of {@code rbig} become {@link U128#mul(long, long)} and the
 *       {@code +=} of a shifted 128-bit value becomes
 *       {@link U128#add(U128)} of {@link U128#shr(int)}; the halves are
 *       {@code .hi} and {@code .lo} as in the C code.
 *   <li>{@code int *q} and {@code int *ia} are one-element {@code int}
 *       arrays, allocated per call; {@code b64u64_u Q = {.f = 0x1.8p52 + id};
 *       *q = Q.u} truncates to the low 32 bits of the bit pattern, so the
 *       Java code casts the {@code long} to {@code int} at that point.
 *   <li>The {@code uint32_t} comparisons of {@code cr_sincosf},
 *       {@code as_sincosf_big} and {@code as_sincosf_database} go through
 *       {@link Integer#compareUnsigned}, and the {@code u64 tail <= 12}
 *       test through {@link Long#compareUnsigned}; the bit patterns built by
 *       {@code ax = t.u<<1} are kept as {@code int} and never compared
 *       signed.
 *   <li>The {@code struct {union{float arg; uint32_t uarg;}; float sh, sl, ch,
 *       cl;} st[]} becomes five columns {@code stArg}, {@code stSh},
 *       {@code stSl}, {@code stCh} and {@code stCl}; the {@code arg} members
 *       are the binary32 bit patterns, written as hex {@code int} literals.
 *       {@code sizeof(st)/sizeof(st[0])} is {@code stArg.length}.
 *   <li>The {@code static const u64 ipi[]} becomes {@code long[]} with the
 *       same literals and an {@code L} suffix.
 *   <li>{@code float s = s0 + z*(...)} and the writes to {@code *sout} and
 *       {@code *cout} carry the explicit {@code (float)} cast that the C
 *       conversion does implicitly.
 * </ul>
 */
public strictfp class SincosF {

  private SincosF() {
  }

  // static const u64 ipi[] of rbig()
    private static final long[] ipi = {0xfe5163abdebbc562L, 0xdb6295993c439041L, 0xfc2757d1f534ddc0L, 0xa2f9836e4e441529L};

  // the arg member of st[] in the C source
  private static final int[] stArg = {
      0x46199998, 0x3f3adc51, 0x3fa7832a, 0x4116cbe4,
      0x4096cbe4, 0x5922aa80, 0x5aa4542c, 0x5f18b878,
      0x6115cb11};

  // the sh member of st[] in the C source
  private static final float[] stSh = {
      -0x1.63f4bap-2f, 0x1.55688ap-1f, 0x1.ee836cp-1f, -0x1.99bc5ap-26f,
      -0x1p+0f, -0x1.b0ea44p-1f, 0x1.beac8cp-1f, 0x1.5ac1eep-4f,
      -0x1.f983c2p-3f};

  // the sl member of st[] in the C source
  private static final float[] stSl = {
      -0x1p-27f, -0x1p-26f, -0x1p-26f, -0x1p-51f,
      0x1p-25f, 0x1p-26f, 0x1p-26f, -0x1p-30f,
      0x1p-28f};

  // the ch member of st[] in the C source
  private static final float[] stCh = {
      -0x1.e01216p-1f, 0x1.7d8e1ep-1f, 0x1.09558p-2f, -0x1p+0f,
      0x1.99bc5cp-27f, 0x1.115d7ep-1f, 0x1.f48148p-2f, 0x1.fe2976p-1f,
      0x1.f0285ep-1f};

  // the cl member of st[] in the C source
  private static final float[] stCl = {
      -0x1p-26f, 0x1p-26f, -0x1p-27f, 0x1p-25f,
      -0x1p-52f, -0x1p-26f, 0x1p-27f, 0x1p-26f,
      -0x1p-26f};

    private static final double[] b =
    {0x1.3bd3cc9be45dcp-6, -0x1.03c1f081b0833p-14, 0x1.55d3c6fc9ac1fp-24, -0x1.e1d3ff281b40dp-35};

    private static final double[] a =
    {0x1.921fb54442d17p-3, -0x1.4abbce6256a39p-10, 0x1.466bc5a518c16p-19, -0x1.32bdc61074ff6p-29};

    private static final double[] tb =
    {0x0p+0, 0x1.8f8b83c69a60bp-3, 0x1.87de2a6aea963p-2, 0x1.1c73b39ae68c8p-1,
     0x1.6a09e667f3bcdp-1, 0x1.a9b66290ea1a3p-1, 0x1.d906bcf328d46p-1, 0x1.f6297cff75cbp-1,
     0x1p+0, 0x1.f6297cff75cbp-1, 0x1.d906bcf328d46p-1, 0x1.a9b66290ea1a3p-1,
     0x1.6a09e667f3bcdp-1, 0x1.1c73b39ae68c8p-1, 0x1.87de2a6aea963p-2, 0x1.8f8b83c69a60bp-3,
     0x0p+0, -0x1.8f8b83c69a60bp-3, -0x1.87de2a6aea963p-2, -0x1.1c73b39ae68c8p-1,
     -0x1.6a09e667f3bcdp-1, -0x1.a9b66290ea1a3p-1, -0x1.d906bcf328d46p-1, -0x1.f6297cff75cbp-1,
     -0x1p+0, -0x1.f6297cff75cbp-1, -0x1.d906bcf328d46p-1, -0x1.a9b66290ea1a3p-1,
     -0x1.6a09e667f3bcdp-1, -0x1.1c73b39ae68c8p-1, -0x1.87de2a6aea963p-2, -0x1.8f8b83c69a60bp-3};

  private static double rbig(int u, int[] q) {
    int e = (u>>23)&0xff, i;
    long m = (u&(~0>>>9))|1<<23;
    U128 p0 = U128.mul(m, ipi[0]);
    U128 p1 = U128.mul(m, ipi[1]); p1 = p1.add(p0.shr(64));
    U128 p2 = U128.mul(m, ipi[2]); p2 = p2.add(p1.shr(64));
    U128 p3 = U128.mul(m, ipi[3]); p3 = p3.add(p2.shr(64));
    long p3h = p3.hi, p3l = p3.lo, p2l = p2.lo, p1l = p1.lo;
    long a;
    int k = e-124, s = k-23;
    /* in cr_sinf(), rbig() is called in the case 127+28 <= e < 0xff
       thus 155 <= e <= 254, which yields 28 <= k <= 127 and 5 <= s <= 104 */
    if (s<64) {
      i = (int) (p3h<<s|p3l>>>(64-s));
      a = p3l<<s|p2l>>>(64-s);
    } else if(s==64) {
      i = (int) p3l;
      a = p2l;
    } else { /* s > 64 */
      i = (int) (p3l<<(s-64)|p2l>>>(128-s));
      a = p2l<<(s-64)|p1l>>>(128-s);
    }
    int sgn = u; sgn >>= 31;
    long sm = a>>63;
    i -= (int) sm;
    double z = (a^sgn)*0x1p-64;
    i = (i^sgn) - sgn;
    q[0] = i;
    return z;
  }

  private static double rltl(float z, int[] q) {
    double x = z;
    double idl = -0x1.b1bbead603d8bp-29*x, idh = 0x1.45f306ep+2*x, id = Math.rint(idh);
    q[0] = (int) Double.doubleToRawLongBits(0x1.8p52 + id);
    return (idh - id) + idl;
  }

  private static double rltl0(double x, int[] q) {
    double idh = 0x1.45f306dc9c883p+2*x, id = Math.rint(idh);
    q[0] = (int) Double.doubleToRawLongBits(0x1.8p52 + id);
    return idh - id;
  }

  private static float addSign(float x, float rh, float rl) {
    float sgn = Math.copySign(1.0f, x);
    return sgn*rh + sgn*rl;
  }

  private static void asSincosfDatabase(float x, float[] sout, float[] cout) {
    int u = Float.floatToRawIntBits(x);
    int ax = u&(~0>>>1);
    for(int i=0;i<stArg.length;i++)
      if(stArg[i] == ax){
        sout[0] = addSign(x, stSh[i], stSl[i]);
        cout[0] = stCh[i] + stCl[i];
      }
  }

  private static void asSincosfBig(float x, float[] sout, float[] cout) {
    int u = Float.floatToRawIntBits(x);
    int ax = u<<1;
    if(Integer.compareUnsigned(ax, 0xff<<24) >= 0){ // nan or +-inf
      if((ax<<8) != 0){
        sout[0] = x + x;
        cout[0] = x + x;
        return; // nan
      }
      // the C source sets errno to EDOM here
      sout[0] = Float.NaN; // the C source computes 0.0f/0.0f, to raise FE_INVALID
      cout[0] = Float.NaN; // the C source computes 0.0f/0.0f, to raise FE_INVALID
      return;
    }
    int[] ia = new int[1];
    double z = rbig(u, ia);
    double z2 = z*z, z4 = z2*z2;
    double aa = (a[0] + z2*a[1]) + z4*(a[2] + z2*a[3]);
    double bb = (b[0] + z2*b[1]) + z4*(b[2] + z2*b[3]);
    bb *= z;
    double s0 = tb[ia[0]&31], c0 = tb[(ia[0]+8)&31];
    double s = s0 + z*(aa*c0 - bb*s0);
    double c = c0 - z*(aa*s0 + bb*c0);
    sout[0] = (float) s;
    cout[0] = (float) c;
    long tr = Double.doubleToRawLongBits(c);
    long tail = (tr + 6)&(~0L>>>36);
    if(Long.compareUnsigned(tail, 12) <= 0) asSincosfDatabase(x, sout, cout);
  }

  public static void compute(float x, float[] sout, float[] cout) {
    int u = Float.floatToRawIntBits(x);
    int ax = u<<1;
    int[] ia = new int[1];
    double z0 = x, z;
    if(Integer.compareUnsigned(ax, 0x822d97c8) < 0){ // |x| < 0x1.2d97c8p+3
      if (Integer.compareUnsigned(ax, 0x73000000) < 0){ // |x| < 0x1p-12
        if (Integer.compareUnsigned(ax, 0x66000000) < 0){ // |x| < 0x1p-25
          if(ax==0){
            sout[0] = x;
            cout[0] = 1.0f;
          } else {
            sout[0] = Math.fma(-x, Math.abs(x), x);
            /* We have underflow when |x| <= 0x1p-126 for rounding towards zero,
               and when |x| < 0x1p-126 for rounding to nearest or away from zero.
               In all cases this is when |sout| < 0x1p-126. */
            cout[0] = 1.0f - 0x1p-25f;
          }
        } else {
          sout[0] = (-0x1.555556p-3f*x)*(x*x) + x;
          cout[0] = (-0x1p-1f*x)*x + 1.0f;
        }
        return;
      }
      if(ax==0x812d97c8) {
        asSincosfDatabase(x, sout, cout);
        return;
      }
      z = rltl0(z0, ia);
    } else {
      if (Integer.compareUnsigned(ax, 0x99000000) > 0) {
        asSincosfBig(x, sout, cout);
        return;
      }
      if (ax==0x8c333330) {
        asSincosfDatabase(x, sout, cout);
        return;
      }
      z = rltl((float) z0, ia);
    }
    double z2 = z*z, z4 = z2*z2;
    double aa = (a[0] + z2*a[1]) + z4*(a[2] + z2*a[3]);
    double bb = (b[0] + z2*b[1]) + z4*(b[2] + z2*b[3]);
    aa *= z;
    bb *= z2;
    double s0 = tb[ia[0]&31], c0 = tb[(ia[0]+8)&31];
    double rs = s0 + (aa*c0 - bb*s0);
    double rc = c0 - (aa*s0 + bb*c0);
    sout[0] = (float) rs;
    cout[0] = (float) rc;
  }
}
