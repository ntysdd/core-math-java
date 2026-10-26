/*
 * Correctly-rounded cube root for binary32 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/cbrt/cbrtf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_cbrtf -> CbrtF.compute
 *
 * Copyright (c) 2023-2025 Alexei Sibidanov and Paul Zimmermann.
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
 * Correctly rounded cube root of a binary32 value, the translation of the
 * CORE-MATH {@code cr_cbrtf}.
 *
 * <p>Reduce the exponent modulo 3, evaluate the degree-7 polynomial of
 * {@code [1,2)} plus its {@code r0} correction term, test the two bounds and,
 * when they disagree, take one Newton step in double and round the last bits to
 * the 2^-31 grid.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>Everything behind {@code CORE_MATH_CHECK_INEXACT}
 *       ({@code get_rounding_mode}, {@code set_flags}, the
 *       {@code x86intrin.h} include and the three calls in the body) is dropped:
 *       the reference build does not define that macro, so it is not compiled
 *       upstream either.
 *   <li>{@code ((const uint64_t *)escale)[it]}, the bit view of the
 *       {@code double} table, is {@link Double#doubleToRawLongBits} applied to
 *       the entry; the table keeps the spelling of the C source.
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become raw-bit
 *       conversions, with the bits carried in {@code int} and {@code long}.
 *   <li>{@code __builtin_clz} is {@link Integer#numberOfLeadingZeros} and
 *       {@code __builtin_expect} is dropped.
 *   <li>{@code uint32_t} shifts are {@code >>>} and {@code uint32_t}
 *       comparisons go through {@link Integer#compareUnsigned}, since
 *       {@code au} keeps the sign bit; the {@code (m0^m1)<((int64_t)1<<31)}
 *       test of the C source is signed and stays signed.
 *   <li>{@code static const} tables declared inside the function become fields:
 *       {@code escale} and {@code c}.
 *   <li>Where the C code narrows a {@code double} to {@code float}
 *       ({@code ub} and {@code lb}) the cast is explicit.
 * </ul>
 */
public final strictfp class CbrtF {
  private CbrtF() {
  }

  private static final double[] escale = {1.0, 0x1.428a2f98d728bp+0/* 2^(1/3) */, 0x1.965fea53d6e3dp+0/* 2^(2/3) */};

  private static final double[] c =
      {0x1.2319d352ea5d5p-1, 0x1.67ad8ee258d1ap-1, -0x1.9342edf9cbad9p-2, 0x1.b6388fc510a75p-3,
       -0x1.6002455599e2fp-4, 0x1.7b096936192c4p-6, -0x1.e5577187e8bf8p-9, 0x1.169ef81d6c34ep-12};

  public static float compute(float x) {
    // the C source calls get_rounding_mode (&flag) here under
    // CORE_MATH_CHECK_INEXACT, which the reference build does not define
    int u = Float.floatToRawIntBits(x), au = u<<1, sgn = u>>>31, e = au>>>24;
    if(Integer.compareUnsigned(au, 1<<24)<0 || Integer.compareUnsigned(au, 0xff<<24)>=0){
      if(Integer.compareUnsigned(au, 0xff<<24)>=0) return x + x; /* inf, nan */
      if(au==0) return x; /* +-0 */
      int nz = Integer.numberOfLeadingZeros(au) - 7;  /* subnormal */
      au <<= nz;
      e -= nz-1;
    }
    int mant = au&0xffffff;
    long cvt1 = (long)mant<<28|(0x3ffL<<52);
    e += 899;
    int et = e/3, it = e%3;
    long isc = Double.doubleToRawLongBits(escale[it]);
    isc += (long)(et - 342)<<52;
    isc |= (long)sgn<<63;
    long cvt2 = isc;
    double z = Double.longBitsToDouble(cvt1), r0 = -0x1.9931c6c2d19d1p-6/z, z2 = z*z, z4 = z2*z2;
    double f = ((c[0] + z*c[1]) + z2*(c[2] + z*c[3])) + z4*((c[4] + z*c[5]) + z2*(c[6] + z*c[7])) + r0;
    double r = f * Double.longBitsToDouble(cvt2);
    float ub = (float)r, lb = (float)(r - Double.longBitsToDouble(cvt2)*1.4182e-9);
    if(ub==lb){
      // cvt2.f = r in the C source, where only the flag check below reads it
      // the C source restores the saved flags here, under CORE_MATH_CHECK_INEXACT
      return ub;
    }
    double u0 = -0x1.ab16ec65d138fp+3;
    double h = f*f*f - z;
    f -= (f*r0*u0)*h;
    r = f * Double.longBitsToDouble(cvt2);
    cvt1 = Double.doubleToRawLongBits(r);
    ub = (float)r;
    long m0 = cvt1<<19, m1 = m0>>63;
    if((m0^m1) < ((long)1<<31)){
      cvt1 = (cvt1 + (1L<<31))&0xffffffff00000000L;
      ub = (float)Double.longBitsToDouble(cvt1);
      // the C source restores the saved flags here, under CORE_MATH_CHECK_INEXACT
    }
    return ub;
  }
}
