/*
 * Correctly rounded arctangent of a binary32 value, in half-revolution units.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/atanpi/atanpif.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_atanpif -> AtanPiF.compute
 *
 * Copyright (c) 2023-2025 Alexei Sibidanov and Paul Zimmermann
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
 * Correctly rounded {@code atan(x)/pi} of a binary32 value, the translation of
 * the CORE-MATH {@code cr_atanpif}.
 *
 * <p>{@code atanpif.c} is self-contained: three branches for the arguments
 * with {@code |x| >= 2^25}, {@code |x| < 2^-13} and {@code |x| < 2^-25}, three
 * exceptional binary32 arguments, and a rational approximation of
 * {@code atan(x)/pi} on {@code [2^-13, 1]} in double precision, reflected to
 * {@code |x| > 1} by {@code atan(x) = sign(x)*pi/2 - atan(1/x)}. It has no
 * helper functions, so the class is one field block and one method.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code b32u32_u} becomes {@link Float#floatToRawIntBits}; the
 *       {@code ~0u>>1} of {@code ax} is {@code ~0 >>> 1}.
 *   <li>{@code int32_t gt = e>=127} becomes a {@code boolean}, Java's
 *       relational operators yield one and the value is only tested.
 *   <li>{@code __builtin_expect} is dropped, the JIT profiles the branches
 *       itself, and {@code __builtin_copysignf} is {@link Math#copySign}.
 *   <li>The {@code CORE_MATH_SUPPORT_ERRNO} block of the {@code |x| < 2^-25}
 *       branch is dropped, there is no {@code errno} in Java; the
 *       {@code THRESHOLD} and {@code C0} macros it defines go with it.
 *   <li>The three results the C code returns as {@code double} expressions
 *       ({@code sx}, {@code sx - (0x1.5555555555555p-2*sx)*(z*z)} and
 *       {@code r}) are converted with the explicit {@code (float)} cast.
 *   <li>{@code cn} and {@code cd} are {@code static const} tables of the
 *       function and become fields.
 * </ul>
 */
public final strictfp class AtanPiF {
  private AtanPiF() {
  }

  private static final double[] cn = {0x1.45f306dc9c882p-2, 0x1.733b561bc23d5p-1,
    0x1.28d9805bdfbf2p-1, 0x1.8c3ba966ae287p-3, 0x1.94a7f81ee634bp-6, 0x1.a6bbf6127a6dfp-11};

  private static final double[] cd = {0x1p+0, 0x1.4e3b3ecc2518fp+1, 0x1.3ef4a360ff063p+1,
    0x1.0f1dc55bad551p+0, 0x1.8da0fecc018a4p-3, 0x1.8fa87803776bfp-7, 0x1.dadf2ca0acb43p-14};

  public static float compute(float x) {
    int t = Float.floatToRawIntBits(x);
    int e = (t >>> 23) & 0xff;
    boolean gt = e >= 127;
    if (e > 127 + 24) { // |x| >= 2^25
      float f = Math.copySign(0.5f, x);
      if (e == 0xff) {
        if ((t << 9) != 0) return x + x; // nan
        return f; // inf
      }
      // Warning: 0x1.45f306p-2f / x underflows for |x| >= 0x1.45f306p+124
      if (Math.abs(x) >= 0x1.45f306p+124f)
        return f - Math.copySign(0x1p-26f, x);
      else
        return f - 0x1.45f306p-2f / x;
    }
    double z = x;
    if (e < 127 - 13) { // |x| < 2^-13
      double sx = z*0x1.45f306dc9c883p-2;
      if (e < 127 - 25) { // |x| < 2^-25
        // the C source sets errno to ERANGE here (underflow) for a range of
        // arguments that depends on the rounding mode, Java has no errno
        return (float) sx;
      }
      return (float) (sx - (0x1.5555555555555p-2*sx)*(z*z));
    }
    int ax = t & (~0 >>> 1);
    if (ax == 0x3fa267dd) return Math.copySign(0x1.267004p-2f, x) - Math.copySign(0x1p-55f, x);
    if (ax == 0x3f693531) return Math.copySign(0x1.e1a662p-3f, x) + Math.copySign(0x1p-28f, x);
    if (ax == 0x3f800000) return Math.copySign(0x1p-2f, x);
    if (gt) z = 1/z;
    double z2 = z*z, z4 = z2*z2, z8 = z4*z4;
    double cn0 = cn[0] + z2*cn[1];
    double cn2 = cn[2] + z2*cn[3];
    double cn4 = cn[4] + z2*cn[5];
    cn0 += z4*cn2;
    cn0 += z8*cn4;
    cn0 *= z;
    double cd0 = cd[0] + z2*cd[1];
    double cd2 = cd[2] + z2*cd[3];
    double cd4 = cd[4] + z2*cd[5];
    double cd6 = cd[6];
    cd0 += z4*cd2;
    cd4 += z4*cd6;
    cd0 += z8*cd4;
    double r = cn0/cd0;
    if (gt) r = Math.copySign(0.5, z) - r;
    return (float) r;
  }
}
