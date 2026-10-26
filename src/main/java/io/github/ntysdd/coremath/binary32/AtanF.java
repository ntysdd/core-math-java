/*
 * Correctly rounded arc-tangent of a binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/atan/atanf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_atanf -> AtanF.compute
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
 * Correctly rounded arc-tangent of a binary32 value, the translation of the
 * CORE-MATH {@code cr_atanf}.
 *
 * <p>{@code atanf.c} is self-contained: a rational approximation of
 * {@code atan} on {@code [2^-13, 1]}, a reflection for {@code |x| > 1}, and two
 * short branches for the tiny arguments. It has no helper functions, so the
 * class is one field block and one method.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code b32u32_u} and {@code b64u64_u} become
 *       {@link Float#floatToRawIntBits} and
 *       {@link Double#doubleToRawLongBits}.
 *   <li>{@code __builtin_fmaf} becomes {@link Math#fma(float, float, float)},
 *       {@code __builtin_copysign} becomes {@link Math#copySign}.
 *   <li>Where the C file returns a {@code double} expression from the
 *       {@code float} function, the {@code (float)} cast is written out: the
 *       results of {@code r} and of the {@code pi/2} reflection.
 *   <li>The {@code CORE_MATH_SUPPORT_ERRNO} block of the {@code |x| < 2^-25}
 *       branch is dropped, there is no {@code errno} in Java.
 *   <li>{@code pi2} stays a local, {@code #define PI_OVER2_H} and
 *       {@code PI_OVER2_L} become fields.
 * </ul>
 */
public final strictfp class AtanF {
  private AtanF() {
  }

  // #define PI_OVER2_H inside cr_atanf()
  private static final double PI_OVER2_H = 0x1.9p0;

  // #define PI_OVER2_L inside cr_atanf()
  private static final double PI_OVER2_L = 0x1.0fdaa22168c23p-7;

  private static final double[] cn =
    {0x1.51eccde075d67p-2, 0x1.a76bb5637f2f2p-1, 0x1.81e0eed20de88p-1,
     0x1.376c8ca67d11dp-2, 0x1.aec7b69202ac6p-5, 0x1.9561899acc73ep-9,
     0x1.bf9fa5b67e6p-16};
  private static final double[] cd =
    {0x1.51eccde075d66p-2, 0x1.dfbdd7b392d28p-1, 0x1p+0,
     0x1.fd22bf0e89b54p-2, 0x1.d91ff8b576282p-4, 0x1.653ea99fc9bbp-7,
     0x1.1e7fcc202340ap-12};

  public static float compute(float x) {
    double pi2 = 0x1.921fb54442d18p+0;
    int t = Float.floatToRawIntBits(x);
    int e = (t >>> 23) & 0xff, gt = e >= 127 ? 1 : 0;
    int ta = t & 0x7fffffff;
    if (ta >= 0x4c700518) { // |x| >= 0x1.e00a3p+25
      if (ta > 0x7f800000) return x + x; // nan
      return (float) Math.copySign(pi2, (double) x); // inf or |x| >= 0x1.e00a3p+25
    }
    if (e < 127 - 13) { // |x| < 2^-13
      if (e < 127 - 25) { // |x| < 2^-25
        if ((t << 1) == 0) return x;
        float res = Math.fma(-x, Math.abs(x), x);
        // the C source sets errno to ERANGE here when the result underflows
        return res;
      }
      return Math.fma(-0x1.5555555555555p-2f * x, x * x, x);
    }
    /* now |x| >= 0x1p-13 */
    double z = x;
    if (gt != 0) z = 1 / z; /* gt is non-zero for |x| >= 1 */
    double z2 = z * z, z4 = z2 * z2, z8 = z4 * z4;
    /* polynomials generated using rminimax
       (https://gitlab.inria.fr/sfilip/rminimax) with the following command:
       ./ratapprox --function="atan(x)" --dom=[0.000122070,1] --num=[x,x^3,x^5,x^7,x^9,x^11,x^13] --den=[1,x^2,x^4,x^6,x^8,x^10,x^12] --output=atanf.sollya --log
       (see output atanf.sollya)
       The coefficient cd[0] was slightly reduced from the original value
       0x1.51eccde075d67p-2 to avoid an exceptional case for |x| = 0x1.1ad646p-4
       and rounding to nearest.
    */
    double cn0 = cn[0] + z2 * cn[1];
    double cn2 = cn[2] + z2 * cn[3];
    double cn4 = cn[4] + z2 * cn[5];
    double cn6 = cn[6];
    cn0 += z4 * cn2;
    cn4 += z4 * cn6;
    cn0 += z8 * cn4;
    cn0 *= z;
    double cd0 = cd[0] + z2 * cd[1];
    double cd2 = cd[2] + z2 * cd[3];
    double cd4 = cd[4] + z2 * cd[5];
    double cd6 = cd[6];
    cd0 += z4 * cd2;
    cd4 += z4 * cd6;
    cd0 += z8 * cd4;
    double r = cn0 / cd0;
    if (gt == 0) return (float) r; /* for |x| < 1, (float) r is correctly rounded */

    /* now r approximates atan(1/x), we use atan(x) + atan(1/x) = sign(x)*pi/2,
       where PI_OVER2_H + PI_OVER2_L approximates pi/2.
       With sign(z)*L + (-r + sign(z)*H), it fails for x=0x1.98c252p+12 and
       rounding upward.
       With sign(z)*PI - r, where PI is a double approximation of pi to nearest,
       it fails for x=0x1.ddf9f6p+0 and rounding upward. */
    r = (Math.copySign(PI_OVER2_L, z) - r) + Math.copySign(PI_OVER2_H, z);
    return (float) r;
  }

}
