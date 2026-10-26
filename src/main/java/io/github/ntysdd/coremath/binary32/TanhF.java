/*
 * Correctly-rounded hyperbolic tangent function for binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/tanh/tanhf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_tanhf -> TanhF.compute
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
 * Correctly rounded hyperbolic tangent of a binary32 value, the translation of
 * the CORE-MATH {@code cr_tanhf}.
 *
 * <p>Three regimes: {@code x - x^3/3} with an FMA for {@code |x| < 2^-13}, a
 * degree-3 polynomial in {@code x^2} up to that bound, and the ratio of two
 * degree-7 polynomials in {@code x^2} above it, with {@code ±1 - 2^-25} as soon
 * as the result cannot be anything else.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The {@code b32u32_u} union becomes {@link Float#floatToRawIntBits} and
 *       {@link Float#intBitsToFloat}; the bits of the argument are carried in
 *       an {@code int}.
 *   <li>{@code static const} tables declared inside {@code cr_tanhf} become
 *       fields: {@code ir}, {@code cn} and {@code cd}.
 *   <li>{@code __builtin_fmaf} is {@link Math#fma} with {@code float}
 *       arguments, {@code __builtin_fabsf} is {@link Math#abs} and
 *       {@code __builtin_copysignf} is {@link Math#copySign}.
 *   <li>{@code uint32_t} shifts are {@code >>>} and {@code uint32_t}
 *       comparisons go through {@link Integer#compareUnsigned}, since
 *       {@code ux} keeps the sign bit of the argument.
 *   <li>The {@code errno} assignments are dropped, since no caller can observe
 *       {@code errno}.
 *   <li>Where the C code narrows a {@code double} to {@code float} (the
 *       {@code r} of the last branch) the cast is explicit.
 * </ul>
 */
public final strictfp class TanhF {
  private TanhF() {
  }

  private static final float[] ir = {1.0f,-1.0f};

  private static final double[] cn =
      {0x1p+0, 0x1.30877b8b72d33p-3, 0x1.694aa09ae9e5ep-8, 0x1.4101377abb729p-14,
       0x1.e0392b1db0018p-22, 0x1.2533756e546f7p-30, 0x1.d62e5abe6ae8ap-41, 0x1.b06be534182dep-54};

  private static final double[] cd =
      {0x1p+0, 0x1.ed99131b0ebeap-2, 0x1.0d27ed6c95a69p-5, 0x1.7cbdaca0e9fccp-11,
       0x1.b4e60b892578ep-18, 0x1.a6f707c5c71abp-26, 0x1.35a8b6e2cd94cp-35, 0x1.ca8230677aa01p-47};

  public static float compute(float x) {
    double z = x;
    int ux = Float.floatToRawIntBits(x);
    int e = (ux>>>23)&0xff;
    if (e==0xff){
      if((ux<<9) != 0) return x + x; // x = nan
      return ir[ux>>>31]; // x = +-inf
    }
    if (e<115){ // |x| < 2^-13
      if (e<102){ // |x| < 2^-26
        if((ux<<1)==0) return x;
        float res = Math.fma(-x, Math.abs(x), x);
        /* The Taylor expansion of tanh(x) at x=0 is x - x^3/3 + o(x^3).
           For |x| > 2^-126 we have no underflow, whatever the rounding mode.
           For |x| < 2^-126, since |tanh(x)| < |x|, we always have underflow.
           For |x| = 2^-126, we have underflow for rounding towards zero,
           i.e., when atan(x) rounds to nextbelow(2^-126).
           In summary, we have underflow whenever |x|<2^-126 or |res|<2^-126. */
        // the C source sets errno to ERANGE here on underflow
        return res;
      }
      float x2 = x*x;
      return Math.fma(x, -0x1.555556p-2f*x2, x);
    }
    if(Integer.compareUnsigned(ux<<1, 0x41102cb3<<1)>0)
      return Math.copySign(1.0f, x) - Math.copySign(0x1p-25f, x);
    double z2 = z*z, z4 = z2*z2, z8 = z4*z4;
    double n0 = cn[0] + z2*cn[1], n2 = cn[2] + z2*cn[3], n4 = cn[4] + z2*cn[5], n6 = cn[6] + z2*cn[7];
    n0 += z4*n2;
    n4 += z4*n6;
    n0 += z8*n4;
    double d0 = cd[0] + z2*cd[1], d2 = cd[2] + z2*cd[3], d4 = cd[4] + z2*cd[5], d6 = cd[6] + z2*cd[7];
    d0 += z4*d2;
    d4 += z4*d6;
    d0 += z8*d4;
    double r = z*n0/d0;
    return (float)r;
  }
}
